package com.cnhsk.learning.knowledge;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.content.importer.validate.QuestionRowValidatorTest;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.write.QuestionRowWriter;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.QuestionAdminService.QuestionUpdate;
import com.cnhsk.learning.knowledge.QuestionAdminService.QuestionUpdateOption;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionAdminConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private QuestionAdminService service;
    @Autowired private QuestionRowWriter writer;
    @Autowired private QuestionRepository questions;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private KnowledgePointRepository points;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    private long actorId;
    private long questionId;
    private long primaryId;
    private long secondaryId;

    @BeforeEach
    void setup() {
        actorId = users.saveAndFlush(new User("t12-race-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người kiểm thử")).getId();
        primaryId = points.saveAndFlush(new KnowledgePoint("NP1-981", "Nút tự soạn", KnowledgeDomain.NP, "示例", (short) 1)).getId();
        secondaryId = points.saveAndFlush(new KnowledgePoint("TV1-981", "Nút phụ tự soạn", KnowledgeDomain.TV, "示例", (short) 1)).getId();
        Question question = new Question("T12_RACE", QuestionType.MULTIPLE_CHOICE, (short) 1, "Câu tự soạn");
        question.setStatus(QuestionStatus.APPROVED); question.setExplanation("Lời giải ban đầu");
        question.setOptions(List.of(new QuestionOption("A", "我", true, null, null), new QuestionOption("B", "你", false, null, null)));
        questionId = questions.saveAndFlush(question).getId();
        labels.saveAndFlush(new QuestionKnowledgePoint(questionId, primaryId, KnowledgeLabelRole.PRIMARY));
        labels.saveAndFlush(new QuestionKnowledgePoint(questionId, secondaryId, KnowledgeLabelRole.SECONDARY));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM shared.audit_logs WHERE target_type='question' AND target_id=?", questionId);
        jdbc.update("DELETE FROM learning.attempt_items WHERE question_id=?", questionId);
        jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", actorId);
        labels.deleteAllInBatch(labels.findByQuestionIdIn(Set.of(questionId))); questions.deleteById(questionId);
        points.deleteAllByIdInBatch(List.of(primaryId, secondaryId)); users.deleteById(actorId);
    }

    @Test
    void AT22_auditFailureRollsBackQuestionAndSwappedLabels() {
        assertThatThrownBy(() -> transaction(Long.MAX_VALUE, () -> service.update(questionId, input("Lời giải thất bại", true))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(questions.findById(questionId).orElseThrow().getExplanation()).isEqualTo("Lời giải ban đầu");
        assertThat(labels.findByQuestionIdIn(Set.of(questionId))).filteredOn(label -> label.getRole() == KnowledgeLabelRole.PRIMARY)
                .singleElement().extracting(QuestionKnowledgePoint::getKnowledgePointId).isEqualTo(primaryId);
        assertThat(audits()).isZero();
    }

    @Test
    void AT22_twoPutsWaitForTheSameRowAndAuditTheLatestCommittedBeforeValue() throws Exception {
        race(() -> service.update(questionId, input("Lần sửa thứ nhất", true)),
                () -> service.update(questionId, input("Lần sửa thứ hai", false)));
        assertThat(questions.findById(questionId).orElseThrow().getExplanation()).isEqualTo("Lần sửa thứ hai");
        assertThat(audits()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT detail->'before'->>'explanation' FROM shared.audit_logs WHERE target_type='question' AND target_id=? ORDER BY id DESC LIMIT 1",
                String.class, questionId)).isEqualTo("Lần sửa thứ nhất");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void AT22_putAndQuestionImportSerializeInBothOrders(boolean importFirst) throws Exception {
        Supplier<Object> imported = () -> {
            var cells = QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE);
            cells.put("code", "T12_RACE"); cells.put("kid_primary", "TV1-981"); cells.put("kid_secondary", "NP1-981");
            cells.put("distractor_errors", ""); cells.put("explanation", "Lời giải từ nhập file");
            return writer.write(new ValidatedRow(2, cells, null, null));
        };
        Supplier<Object> put = () -> service.update(questionId, input("Lời giải từ PUT", false));
        race(importFirst ? imported : put, importFirst ? put : imported);
        assertThat(questions.findById(questionId).orElseThrow().getExplanation())
                .isEqualTo(importFirst ? "Lời giải từ PUT" : "Lời giải từ nhập file");
        assertThat(audits()).isEqualTo(1);
        assertThat(labels.findByQuestionIdIn(Set.of(questionId))).hasSize(2);
    }

    @Test
    void AT23_concurrentArchivesOnlyAuditOneStateTransition() throws Exception {
        race(() -> service.archive(questionId), () -> service.archive(questionId));
        assertThat(questions.findById(questionId).orElseThrow().getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
        assertThat(audits()).isEqualTo(1);
    }

    @Test
    void AT22_attemptCommittedBeforeTheLockPreventsChangingGrading() {
        long attempt = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind) VALUES (?,'PRACTICE') RETURNING id", Long.class, actorId);
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,question_id,item_type,skill,order_index) VALUES (?,?,'QUESTION_CHOICE','READING',1)", attempt, questionId);
        QuestionUpdate value = input("Không được ghi", false);
        QuestionUpdate changed = new QuestionUpdate(value.stem(), List.of(new QuestionUpdateOption("A", "我", false, null, null),
                new QuestionUpdateOption("B", "你", true, null, null)), null, value.explanation(), 1, 2, null, null, value.primaryKid(), value.secondaryKids());
        assertThatThrownBy(() -> transaction(actorId, () -> service.update(questionId, changed)))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.GRADING_CHANGE_NOT_ALLOWED));
        assertThat(questions.findById(questionId).orElseThrow().getExplanation()).isEqualTo("Lời giải ban đầu");
        assertThat(audits()).isZero();
    }

    private void race(Supplier<Object> firstAction, Supplier<Object> secondAction) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch written = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> transaction(actorId, () -> {
                Object value = firstAction.get(); written.countDown(); await(release); return value;
            }));
            await(written);
            var second = pool.submit(() -> transaction(actorId, secondAction));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            boolean waiting = false;
            while (System.nanoTime() < deadline) {
                waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%learning.questions%'", Long.class) > 0;
                if (waiting) break;
                if (second.isDone()) second.get(1, TimeUnit.SECONDS);
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(waiting).as("Second transaction waits on the PostgreSQL question row").isTrue();
            assertThat(second.isDone()).isFalse();
            release.countDown(); first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue(); }
    }

    private <T> T transaction(long userId, Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, Set.of("TEACHER", "CONTENT_EDITOR"), true, UUID.randomUUID().toString(), UUID.randomUUID(), Instant.now()), null, List.of()));
        try { return new TransactionTemplate(manager).execute(status -> action.get()); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(25, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
    private long audits() { return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE target_type='question' AND target_id=?", Long.class, questionId); }
    private static QuestionUpdate input(String explanation, boolean swap) {
        return new QuestionUpdate("Câu tự soạn", List.of(new QuestionUpdateOption("A", "我", true, null, null),
                new QuestionUpdateOption("B", "你", false, null, null)), null, explanation, 1, 2, null, null,
                swap ? "TV1-981" : "NP1-981", List.of(swap ? "NP1-981" : "TV1-981"));
    }
}
