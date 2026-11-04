package com.cnhsk.learning.content.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.ExamQuestionRowValidator;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.write.ExamQuestionRowWriter;
import com.cnhsk.learning.content.importer.write.ExamRowWriter;
import com.cnhsk.learning.content.importer.write.RowWriteRejectedException;
import com.cnhsk.learning.exam.ExamAttemptService;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ExamQuestionImportIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CsvReader reader;
    @Autowired private ExamQuestionRowValidator validator;
    @Autowired private ExamQuestionRowWriter writer;
    @Autowired private ExamRowWriter examWriter;
    @Autowired private ExamAttemptService examAttempts;
    @Autowired private PracticeService practice;
    @Autowired private PlatformTransactionManager manager;
    @MockitoSpyBean private ExamImportSupport exams;
    @MockitoSpyBean private QuestionRepository questions;
    private final List<String> questionCodes = new ArrayList<>();
    private final String examCode = code("EXAM");
    private Long examId;
    private Long learner;

    @AfterEach
    void cleanup() {
        if (learner != null) {
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id=?)", learner);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", learner);
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", learner);
            jdbc.update("DELETE FROM auth.users WHERE id=?", learner);
        }
        if (examId != null) {
            jdbc.update("DELETE FROM learning.exam_questions WHERE exam_id=?", examId);
            jdbc.update("DELETE FROM learning.exams WHERE id=?", examId);
        }
        questionCodes.forEach(code -> jdbc.update("DELETE FROM learning.questions WHERE code=?", code));
    }

    @Test
    void FR008_AT07_validatesReferencesSectionsUsabilityLevelAndOrder() {
        exam();
        String a = question(1, "APPROVED", false);
        String b = question(1, "APPROVED", false);
        String pending = question(1, "PENDING_REVIEW", false);
        String audio = question(1, "APPROVED", true);
        String otherLevel = question(2, "APPROVED", false);
        assertThat(write(row(a, 1, 1, ""))).isEqualTo(RowAction.CREATE);
        assertThat(validate("UNKNOWN," + a + ",1,1,").errors()).extracting(CsvReader.RowError::code)
                .containsExactly("REFERENCE_NOT_FOUND");
        assertThat(validate(csv("UNKNOWN", 1, 2, ""), csv(b, 3, 2, ""), csv(pending, 1, 2, ""),
                csv(audio, 1, 2, ""), csv(otherLevel, 1, 2, "")).errors()).extracting(CsvReader.RowError::code)
                .containsExactly("REFERENCE_NOT_FOUND", "SECTION_NOT_FOUND", "QUESTION_NOT_USABLE",
                        "QUESTION_NOT_USABLE", "LEVEL_MISMATCH");
        var moved = validate(csv(a, 1, 2, ""), csv(b, 1, 1, ""));
        assertThat(moved.validRows()).hasSize(1);
        assertThat(moved.errors()).extracting(CsvReader.RowError::code).containsExactly("DUPLICATE_ORDER");
        var duplicate = validate(csv(a, 1, 2, ""), csv(b, 1, 2, ""));
        assertThat(duplicate.errors()).extracting(CsvReader.RowError::code).containsExactly("DUPLICATE_ORDER");
        assertThat(validate(csv(a, 1, 1, "")).validRows()).singleElement()
                .satisfies(row -> assertThat(row.values()).containsEntry("points", "1"));
        assertThat(validate(csv(b, 1, 1000, "11")).errors()).extracting(CsvReader.RowError::code)
                .containsExactly("INVALID_VALUE", "INVALID_VALUE");
        assertThatThrownBy(() -> write(row(b, 1, 1, "1"))).isInstanceOfSatisfying(RowWriteRejectedException.class,
                error -> assertThat(error.code()).isEqualTo("DUPLICATE_ORDER"));
    }

    @Test
    void FR008_AT07_checksTwoHundredLimitAndThreeBatchReads() {
        exam();
        String existing = question(1, "APPROVED", false);
        String a = question(1, "APPROVED", false);
        String b = question(1, "APPROVED", false);
        assertThat(write(row(existing, 1, 1, "1"))).isEqualTo(RowAction.CREATE);
        String prefix = code("BULK");
        jdbc.update("""
                INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,status)
                SELECT ? || i, 'TRUE_FALSE', 1, 'Câu tự soạn',
                    '[{"key":"A","is_correct":true},{"key":"B","is_correct":false}]', 'A','APPROVED'
                FROM generate_series(2,199) i
                """, prefix);
        for (int i = 2; i <= 199; i++) questionCodes.add(prefix + i);
        jdbc.update("""
                INSERT INTO learning.exam_questions(exam_id,question_id,section_index,order_index)
                SELECT ?, id, 1, CAST(substring(code FROM ?) AS integer) FROM learning.questions WHERE left(code, ?) = ?
                """, examId, prefix.length() + 1, prefix.length(), prefix);

        for (String[] rows : List.of(new String[]{csv(a, 1, 200, "")},
                new String[]{csv(a, 1, 200, ""), csv(b, 1, 201, ""), csv(existing, 1, 1, "2")})) {
            clearInvocations(exams, questions);
            var result = validate(rows);
            verify(exams).exams(eq(List.of(examCode)), eq(false));
            verify(exams).slots(eq(List.of(examCode)));
            verify(questions).findByCodeIn(any());
            if (rows.length == 1) assertThat(result.errors()).isEmpty();
            else {
                assertThat(result.validRows()).hasSize(2);
                assertThat(result.errors()).extracting(CsvReader.RowError::code).containsExactly("INVALID_VALUE");
            }
        }
        assertThat(write(row(a, 1, 200, "1"))).isEqualTo(RowAction.CREATE);
        assertThatThrownBy(() -> write(row(b, 1, 201, "1"))).isInstanceOfSatisfying(RowWriteRejectedException.class,
                error -> assertThat(error.code()).isEqualTo("INVALID_VALUE"));
    }

    @Test
    void FR009_AT08_updatingPointsDoesNotChangeAlreadyServedWeights() {
        exam();
        String question = question(1, "APPROVED", false);
        assertThat(write(row(question, 1, 1, "2"))).isEqualTo(RowAction.CREATE);
        learner = jdbc.queryForObject("""
                INSERT INTO auth.users(email,password_hash,display_name)
                VALUES (?, 'test-hash', 'Người học tự soạn') RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.test");
        long session = examAttempts.start(learner, examId).session().sessionId();
        assertThat(write(row(question, 1, 1, "7"))).isEqualTo(RowAction.UPDATE);
        assertThat(write(row(question, 1, 1, "7"))).isEqualTo(RowAction.UNCHANGED);
        var result = practice.finish(learner, session, List.of(new Answer(1, "A", 2000, 0, false, false, null)));
        assertThat(result.extra()).containsEntry("weightedScore", 2L).containsEntry("weightedMaxScore", 2L);
    }

    @Test
    void FR009_parallelWritersKeepOneSlotAndCannotRemoveAnOccupiedSection() throws Exception {
        exam();
        String a = question(1, "APPROVED", false);
        String b = question(1, "APPROVED", false);
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> attempt(gate, () -> write(row(a, 1, 1, "1"))));
            var second = pool.submit(() -> attempt(gate, () -> write(row(b, 1, 1, "1"))));
            gate.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATE", "DUPLICATE_ORDER");

            var race = new CountDownLatch(1);
            var linked = pool.submit(() -> attempt(race, () -> write(row(a, 2, 2, "1"))));
            var removed = pool.submit(() -> attempt(race, () -> transaction().execute(status -> examWriter.write(
                    new ValidatedRow(2, Map.of("code", examCode, "title", "Đề tự soạn", "hsk_level", "1",
                            "sections", "1:Đọc", "status", "PUBLISHED"), null, null)))));
            race.countDown();
            String link = linked.get(15, TimeUnit.SECONDS);
            String remove = removed.get(15, TimeUnit.SECONDS);
            if ("UPDATE".equals(remove)) assertThat(link).isEqualTo("SECTION_NOT_FOUND");
            else {
                assertThat(remove).isEqualTo("INVALID_VALUE");
                assertThat(link).isIn("CREATE", "UPDATE");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static String attempt(CountDownLatch gate, java.util.function.Supplier<RowAction> action) throws Exception {
        assertThat(gate.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            return action.get().name();
        } catch (RowWriteRejectedException exception) {
            return exception.code();
        }
    }

    private void exam() {
        examId = jdbc.queryForObject("""
                INSERT INTO learning.exams(code,title,hsk_level,status,sections)
                VALUES (?, 'Đề tự soạn', 1, 'PUBLISHED', '[{"index":1,"name":"Đọc"},{"index":2,"name":"Viết"}]')
                RETURNING id
                """, Long.class, examCode);
    }

    private String question(int level, String status, boolean audio) {
        String code = code("QUESTION");
        jdbc.update("""
                INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,status,audio_url)
                VALUES (?, 'TRUE_FALSE', ?, 'Câu tự soạn',
                    '[{"key":"A","is_correct":true},{"key":"B","is_correct":false}]', 'A', ?, ?)
                """, code, level, status, audio ? "sample.mp3" : null);
        questionCodes.add(code);
        return code;
    }

    private static String code(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
    }

    private RowAction write(ValidatedRow row) {
        return transaction().execute(status -> writer.write(row));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(manager);
    }

    private ValidatedRow row(String question, int section, int order, String points) {
        return new ValidatedRow(2, Map.of("exam_code", examCode, "question_code", question,
                "section_index", Integer.toString(section), "order_index", Integer.toString(order), "points", points),
                null, null);
    }

    private String csv(String question, int section, int order, String points) {
        return String.join(",", examCode, question, Integer.toString(section), Integer.toString(order), points);
    }

    private com.cnhsk.learning.content.importer.validate.RowValidationResult validate(String... rows) {
        String csv = "exam_code,question_code,section_index,order_index,points\n" + String.join("\n", rows) + "\n";
        return validator.validate(reader.read(ImportDataType.EXAM_QUESTION, "synthetic.csv",
                csv.getBytes(StandardCharsets.UTF_8)));
    }
}
