package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.learning.mastery.EvidenceAllocator.AllocatedEvidence;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;

@Transactional
class EvidenceAllocatorTest extends AbstractIntegrationTest {

    private static final AtomicInteger KID_SEQUENCE = new AtomicInteger(700);
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode("fixture-only-password");
    private static final Instant NOW = Instant.parse("2026-11-05T08:00:00Z");

    @Autowired private EvidenceAllocator allocator;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    @Test
    void AT05_sourceContextIncludesFutureDueReviewNameAndExcludedSources() {
        assertThat(EvidenceAllocator.contextOf("MOCK_EXAM")).isEqualTo("EXAM");
        assertThat(EvidenceAllocator.contextOf("PLACEMENT")).isEqualTo("QUIZ");
        assertThat(EvidenceAllocator.contextOf("TOPIC_TEST")).isEqualTo("QUIZ");
        for (String source : List.of("TOPIC_PRACTICE", "GRAMMAR_LESSON", "CHAR_RECOGNITION",
                "TARGETED_PRACTICE", "GAME_ROUND", "DUE_REVIEW")) {
            assertThat(EvidenceAllocator.contextOf(source)).isEqualTo("PRACTICE");
        }
        for (String source : List.of("TOPIC_STUDY", "CHAR_WRITING", "WRITING_CHALLENGE", "PRON_STAGE",
                "QUIZ", "QUIZ_RETRY")) {
            assertThat(EvidenceAllocator.contextOf(source)).isNull();
        }
        assertThat(EvidenceAllocator.contextOf("FUTURE_SOURCE")).isNull();
    }

    @Test
    void quizSourcesAreExplicitlyExcludedWithoutUnknownSourceWarnings() {
        Logger logger = (Logger) LoggerFactory.getLogger(EvidenceAllocator.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(EvidenceAllocator.contextOf("QUIZ")).isNull();
            assertThat(EvidenceAllocator.contextOf("QUIZ_RETRY")).isNull();
            assertThat(appender.list).isEmpty();

            assertThat(EvidenceAllocator.contextOf("FUTURE_SOURCE")).isNull();
            assertThat(appender.list).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).contains("No evidence context", "FUTURE_SOURCE"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void AT06_questionPrimarySecondaryErrorAndFallbackUseCurrentLabels() throws Exception {
        long user = user();
        Node primary = node();
        Node secondary = node();
        Node error = node();
        long question = question(primary, secondary, error);
        long right = attempt(user, "TOPIC_TEST", List.of(choice(1, question, 4)),
                List.of(answer(1, question, null, "A", true, "{}", NOW)));
        List<AllocatedEvidence> correct = allocate(right);
        assertThat(correct).extracting(AllocatedEvidence::kid).containsExactly(primary.kid(), secondary.kid());
        assertThat(correct).extracting(AllocatedEvidence::score).containsExactly(100, 100);
        assertThat(correct).extracting(AllocatedEvidence::role).containsExactly("PRIMARY", "SECONDARY");
        assertThat(correct.get(0).staticWeight()).isEqualByComparingTo("0.6");
        assertThat(correct.get(1).staticWeight()).isEqualByComparingTo("0.3");

        long wrongUser = user();
        long wrong = attempt(wrongUser, "TOPIC_TEST", List.of(choice(1, question, 4)),
                List.of(answer(1, question, null, "B", false, "{}", NOW.plusSeconds(1))));
        assertThat(allocate(wrong)).singleElement().satisfies(value -> {
            assertThat(value.kid()).isEqualTo(error.kid());
            assertThat(value.role()).isEqualTo("ERROR");
            assertThat(value.score()).isZero();
        });
        long withoutError = attempt(wrongUser, "TOPIC_TEST", List.of(choice(1, question, 4)),
                List.of(answer(1, question, null, "C", false, "{}", NOW.plusSeconds(2))));
        assertThat(allocate(withoutError)).singleElement().satisfies(value -> {
            assertThat(value.kid()).isEqualTo(primary.kid());
            assertThat(value.role()).isEqualTo("PRIMARY");
            assertThat(value.score()).isZero();
        });
    }

    @Test
    void AT07_contextFormRoleWeightsAreExact() throws Exception {
        long user = user();
        Node node = node();
        long question = question(node, null, null);
        long exam = attempt(user, "MOCK_EXAM", List.of(choice(1, question, 0)),
                List.of(answer(1, question, null, "A", true, "{}", NOW)));
        assertThat(allocate(exam).get(0).staticWeight()).isEqualByComparingTo("1.0");
        long anotherQuestion = question(node, null, null);
        long binary = attempt(user, "TOPIC_PRACTICE", List.of(choice(1, anotherQuestion, 2)),
                List.of(answer(1, anotherQuestion, null, "A", true, "{}", NOW.plusSeconds(1))));
        assertThat(allocate(binary).get(0).staticWeight()).isEqualByComparingTo("0.25");
        long secondaryQuestion = question(node, node(), null);
        long secondarySession = attempt(user, "TOPIC_PRACTICE", List.of(choice(1, secondaryQuestion, 2)),
                List.of(answer(1, secondaryQuestion, null, "A", true, "{}", NOW.plusSeconds(2))));
        assertThat(allocate(secondarySession).get(1).staticWeight()).isEqualByComparingTo("0.125");
    }

    @Test
    void AT08_correctAnswerWindowIsOpenAtExactly168HoursAndIncludesSameSession() throws Exception {
        long user = user();
        Node node = node();
        long question = question(node, null, null);
        attempt(user, "TOPIC_TEST", List.of(choice(1, question, 2)),
                List.of(answer(1, question, null, "A", true, "{}", NOW.minus(168, ChronoUnit.HOURS).minusNanos(1000))));
        long current = attempt(user, "TOPIC_TEST", List.of(choice(1, question, 2)),
                List.of(answer(1, question, null, "B", false, "{}", NOW)));
        assertThat(allocate(current)).hasSize(1);
        attempt(user, "TOPIC_TEST", List.of(choice(1, question, 2)),
                List.of(answer(1, question, null, "A", true, "{}", NOW.minus(168, ChronoUnit.HOURS))));
        assertThat(allocate(current)).hasSize(1);
        attempt(user, "TOPIC_TEST", List.of(choice(1, question, 2)),
                List.of(answer(1, question, null, "A", true, "{}", NOW.minus(168, ChronoUnit.HOURS).plusNanos(1000))));
        assertThat(allocate(current)).isEmpty();
        long repeated = attempt(user(), "TOPIC_TEST", List.of(choice(1, question, 2), choice(2, question, 2)),
                List.of(answer(1, question, null, "A", true, "{}", NOW.plusSeconds(10)),
                        answer(2, question, null, "B", false, "{}", NOW.plusSeconds(11))));
        assertThat(allocate(repeated)).extracting(AllocatedEvidence::itemId).hasSize(1);
    }

    @Test
    void AT08_filtersSuspiciousMissingSkippedAudioAndArchivedQuestion() throws Exception {
        long user = user();
        Node node = node();
        long question = question(node, null, null);
        long session = attempt(user, "TOPIC_TEST", List.of(choice(1, question, 2), choice(2, question, 2),
                        choice(3, question, 2), choice(4, question, 2)),
                List.of(answer(1, question, null, "B", false, "{\"skipped\":true}", NOW),
                        answer(2, question, null, "B", false, "{\"missing\":true}", NOW.plusSeconds(1)),
                        answer(3, question, null, "B", false, "{\"audioFailed\":true}", NOW.plusSeconds(2)),
                        answer(4, question, null, "B", false, "{\"suspicious\":true}", NOW.plusSeconds(3))));
        assertThat(allocate(session)).isEmpty();
        long normal = attempt(user, "TOPIC_TEST", List.of(choice(1, question, 2)),
                List.of(answer(1, question, null, "B", false, "{}", NOW.plusSeconds(4))));
        assertThat(allocate(normal)).hasSize(1);
        jdbc.update("UPDATE learning.questions SET status='ARCHIVED' WHERE id=?", question);
        assertThat(allocate(normal)).isEmpty();
        jdbc.update("UPDATE learning.attempts SET suspicious=true WHERE id=?", session);
        assertThat(allocate(session)).isEmpty();
    }

    private List<AllocatedEvidence> allocate(long attemptId) {
        Attempt attempt = attempts.findById(attemptId).orElseThrow();
        return allocator.allocateNew(attempt, items.findByAttemptIdOrderByOrderIndexAsc(attemptId));
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users (email, password_hash, display_name) "
                + "VALUES (?, ?, 'Kiểm thử') RETURNING id", Long.class,
                "t07-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
    }

    private Node node() {
        String kid = "VB3-" + KID_SEQUENCE.incrementAndGet();
        long id = jdbc.queryForObject("INSERT INTO learning.knowledge_points "
                + "(code, title, domain, name_zh, hsk_level) VALUES (?, 'Nút giả', 'VB', '字', 3) RETURNING id",
                Long.class, kid);
        return new Node(id, kid);
    }

    private long question(Node primary, Node secondary, Node error) {
        String errorJson = error == null ? "" : ",\"error_kid\":\"" + error.kid() + "\"";
        String options = "[{\"key\":\"A\",\"text\":\"Đúng\",\"is_correct\":true},"
                + "{\"key\":\"B\",\"text\":\"Sai\",\"is_correct\":false" + errorJson + ",\"error_note\":\"Gợi ý giả\"},"
                + "{\"key\":\"C\",\"text\":\"Khác\",\"is_correct\":false}]";
        long id = jdbc.queryForObject("INSERT INTO learning.questions "
                + "(qtype, hsk_level, stem, options, correct_key, status, code) "
                + "VALUES ('MULTIPLE_CHOICE', 1, 'Câu giả', ?::jsonb, 'A', 'APPROVED', ?) RETURNING id",
                Long.class, options, "T07-" + UUID.randomUUID());
        jdbc.update("INSERT INTO learning.question_knowledge_points (question_id, knowledge_point_id, role) "
                + "VALUES (?, ?, 'PRIMARY')", id, primary.id());
        if (secondary != null) {
            jdbc.update("INSERT INTO learning.question_knowledge_points (question_id, knowledge_point_id, role) "
                    + "VALUES (?, ?, 'SECONDARY')", id, secondary.id());
        }
        return id;
    }

    private ServedItem choice(int no, long questionId, int optionCount) {
        List<ServedItem.Option> options = switch (optionCount) {
            case 0 -> null;
            case 2 -> List.of(new ServedItem.Option("A", "A"), new ServedItem.Option("B", "B"));
            default -> List.of(new ServedItem.Option("A", "A"), new ServedItem.Option("B", "B"),
                    new ServedItem.Option("C", "C"), new ServedItem.Option("D", "D"));
        };
        return new ServedItem(no, ItemType.QUESTION_CHOICE, Skill.READING, null,
                new ServedItem.Prompt("Câu giả", null, null, null, null, null, null, null, null),
                options, "A", null, null, new ServedItem.Rules(0, 0, false), questionId);
    }

    private Answer answer(int no, Long questionId, Long lexemeId, String value, Boolean correct,
            String meta, Instant answeredAt) {
        return new Answer(no, questionId, lexemeId, value, correct, meta, answeredAt);
    }

    private long attempt(long user, String source, List<ServedItem> served, List<Answer> answers)
            throws JsonProcessingException {
        String snapshot = mapper.writeValueAsString(new ServedSnapshot(Map.of(), served));
        long id = jdbc.queryForObject("INSERT INTO learning.attempts "
                + "(user_id, kind, ref_type, ref_id, status, served_items, served_at, submitted_at) "
                + "VALUES (?, 'TOPIC_TEST', ?, 1, 'SUBMITTED', ?::jsonb, ?, ?) RETURNING id",
                Long.class, user, source, snapshot, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW));
        for (Answer answer : answers) {
            jdbc.update("INSERT INTO learning.attempt_items "
                    + "(attempt_id, question_id, lexeme_id, answer, is_correct, order_index, answered_at, item_type, skill, meta) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, 'QUESTION_CHOICE', 'READING', ?::jsonb)",
                    id, answer.questionId(), answer.lexemeId(), answer.value(), answer.correct(), answer.no(),
                    Timestamp.from(answer.answeredAt()), answer.meta());
        }
        return id;
    }

    private record Node(long id, String kid) { }
    private record Answer(int no, Long questionId, Long lexemeId, String value, Boolean correct,
            String meta, Instant answeredAt) { }
}
