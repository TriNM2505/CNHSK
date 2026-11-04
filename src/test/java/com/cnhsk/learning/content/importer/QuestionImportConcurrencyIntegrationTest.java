package com.cnhsk.learning.content.importer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.validate.QuestionRowValidatorTest;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.write.QuestionRowWriter;
import com.cnhsk.learning.content.importer.write.RowWriteRejectedException;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionImportConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private UserRepository users;
    @Autowired private QuestionRepository questions;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private KnowledgePointRepository points;
    @Autowired private QuestionRowWriter writer;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private JdbcTemplate jdbc;
    private User user;
    private Question question;
    private Set<Long> questionBaseline;
    private Set<Long> pointBaseline;

    @BeforeEach
    void setup() {
        questionBaseline = questions.findAll().stream().map(Question::getId).collect(Collectors.toSet());
        pointBaseline = points.findAll().stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        user = users.saveAndFlush(new User("question-race-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người kiểm thử khoá câu"));
        for (String kid : List.of("TV1-001", "TV1-002")) points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn", KnowledgeDomain.TV, "示例", (short) 1));
        var initial = cells(); initial.put("kid_secondary", "TV1-002");
        new TransactionTemplate(manager).executeWithoutResult(status -> writer.write(row(initial)));
        question = questions.findByCode("T08_RACE").orElseThrow();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id=?)", user.getId());
        jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", user.getId());
        Set<Long> ids = questions.findAll().stream().map(Question::getId).filter(id -> !questionBaseline.contains(id)).collect(Collectors.toSet());
        labels.deleteAllInBatch(labels.findByQuestionIdIn(ids)); questions.deleteAllByIdInBatch(ids);
        points.deleteAllByIdInBatch(points.findAll().stream().map(KnowledgePoint::getId).filter(id -> !pointBaseline.contains(id)).toList());
        users.deleteById(user.getId());
    }

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS,NULL", "SUBMITTED,true", "ABANDONED,false", "EXPIRED,false"})
    void AT12_anyAttemptItemLocksGradingRegardlessOfStateOrCorrectness(String state, String correctness) {
        long attempt = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,status,submitted_at) VALUES (?,'PRACTICE',?,now()) RETURNING id",
                Long.class, user.getId(), state);
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,question_id,item_type,skill,order_index,is_correct) VALUES (?,?,'QUESTION_CHOICE','READING',1,?)",
                attempt, question.getId(), correctness.equals("NULL") ? null : Boolean.valueOf(correctness));
        var changed = cells(); changed.put("correct", "B");
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> writer.write(row(changed))))
                .isInstanceOfSatisfying(RowWriteRejectedException.class, error -> assertThat(error.code()).isEqualTo("GRADING_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void AT12_attemptCommittedBeforeTheQuestionLockIsSeenByTheWriter() throws Exception {
        var pool = Executors.newFixedThreadPool(2); CountDownLatch recorded = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                record(); recorded.countDown(); await(release);
            }));
            await(recorded);
            var changed = cells(); changed.put("correct", "B");
            var second = pool.submit(() -> new TransactionTemplate(manager).execute(status -> writer.write(row(changed))));
            awaitCondition(() -> waiting("questions") >= 1); assertThat(second.isDone()).isFalse();
            release.countDown(); first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(RowWriteRejectedException.class)
                    .satisfies(error -> assertThat(((RowWriteRejectedException) error.getCause()).code()).isEqualTo("GRADING_CHANGE_NOT_ALLOWED"));
            assertThat(questions.findById(question.getId()).orElseThrow().getOptions()).filteredOn(option -> option.isCorrect())
                    .singleElement().extracting(option -> option.key()).isEqualTo("A");
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void AT12_questionLockSerializesTheFirstAttemptItemBehindAnAlreadyWritingRow() throws Exception {
        var pool = Executors.newFixedThreadPool(2); CountDownLatch written = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        try {
            var changed = cells(); changed.put("correct", "B");
            var first = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                var result = writer.write(row(changed)); written.countDown(); await(release); return result;
            }));
            await(written);
            var second = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> record()));
            awaitCondition(() -> waiting("attempt_items") >= 1); assertThat(second.isDone()).isFalse();
            release.countDown(); assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(RowAction.UPDATE); second.get(10, TimeUnit.SECONDS);
            assertThat(questions.usedQuestionIds(List.of(question.getId()))).containsExactly(question.getId());
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void AT12_concurrentUpdatesReadOptionalScalarsAndLabelsAfterTheLock() throws Exception {
        var pool = Executors.newFixedThreadPool(2); CountDownLatch written = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        try {
            var swap = cells(); swap.put("kid_primary", "TV1-002"); swap.put("kid_secondary", "TV1-001"); swap.put("difficulty", "3");
            var first = pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                var result = writer.write(row(swap)); written.countDown(); await(release); return result;
            }));
            await(written);
            var editorial = cells(); editorial.put("kid_primary", "TV1-002"); editorial.put("kid_secondary", "TV1-001");
            editorial.put("difficulty", ""); editorial.put("explanation", "Lời giải sau lượt cập nhật trước");
            var second = pool.submit(() -> new TransactionTemplate(manager).execute(status -> writer.write(row(editorial))));
            awaitCondition(() -> waiting("questions") >= 1); assertThat(second.isDone()).isFalse();
            release.countDown(); assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(RowAction.UPDATE);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(RowAction.UPDATE);
            assertThat(questions.findById(question.getId()).orElseThrow().getDifficulty()).isEqualTo((short) 3);
            Map<Long, KnowledgeLabelRole> roles = labels.findByQuestionIdIn(List.of(question.getId())).stream()
                    .collect(Collectors.toMap(QuestionKnowledgePoint::getKnowledgePointId, QuestionKnowledgePoint::getRole));
            assertThat(roles).containsEntry(points.findByCode("TV1-002").orElseThrow().getId(), KnowledgeLabelRole.PRIMARY)
                    .containsEntry(points.findByCode("TV1-001").orElseThrow().getId(), KnowledgeLabelRole.SECONDARY).hasSize(2);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private void record() {
        long attempt = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind) VALUES (?,'PRACTICE') RETURNING id", Long.class, user.getId());
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,question_id,item_type,skill,order_index) VALUES (?,?,'QUESTION_CHOICE','READING',1)", attempt, question.getId());
    }
    private long waiting(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE ?", Long.class, "%" + table + "%");
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private static void awaitCondition(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); }
        throw new AssertionError("Expected a PostgreSQL lock waiter within 10 seconds");
    }
    private static Map<String, String> cells() {
        var values = QuestionRowValidatorTest.values(QuestionType.MULTIPLE_CHOICE); values.put("code", "T08_RACE"); return values;
    }
    private static ValidatedRow row(Map<String, String> values) { return new ValidatedRow(2, values, null, null); }
}
