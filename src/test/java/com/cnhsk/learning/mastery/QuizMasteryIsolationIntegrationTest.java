package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class QuizMasteryIsolationIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T04:00:00Z");
    private static final String OPTIONS = "[{\"key\":\"A\",\"text\":\"nước\",\"is_correct\":true},"
            + "{\"key\":\"B\",\"text\":\"lửa\",\"is_correct\":false}]";

    @Autowired private PracticeService practice;
    @Autowired private EvidenceAllocator allocator;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "topicTestHandler") private PracticeSourceHandler handler;

    private final List<Long> questionIds = new ArrayList<>();
    private Instant now = NOW;
    private Long userId;
    private Long knowledgeId;
    private Long topicId;

    @BeforeEach
    void setup() {
        when(clock.instant()).thenAnswer(ignored -> now);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_TEST);
        when(handler.onFinish(any())).thenReturn(Map.of());
        userId = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,'fixture-unused','Quiz fixture') RETURNING id", Long.class,
                "quiz-isolation-" + UUID.randomUUID() + "@example.test");
        String kid = jdbc.queryForObject("SELECT 'NL3-' || number FROM generate_series(900,999) number "
                + "WHERE NOT EXISTS (SELECT 1 FROM learning.knowledge_points WHERE code='NL3-' || number) "
                + "ORDER BY number LIMIT 1", String.class);
        knowledgeId = jdbc.queryForObject("INSERT INTO learning.knowledge_points"
                + "(code,title,domain,name_zh,hsk_level) VALUES (?,'Nút kiểm thử quiz','NL','阅读',3) RETURNING id",
                Long.class, kid);
        topicId = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level,knowledge_point_id) "
                + "VALUES (?,'Chủ đề kiểm thử quiz',3,?) RETURNING id", Long.class,
                "QUIZ-" + UUID.randomUUID(), knowledgeId);
    }

    @AfterEach
    void cleanup() {
        if (userId != null) {
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", userId);
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", userId);
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN "
                    + "(SELECT id FROM learning.attempts WHERE user_id=?)", userId);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", userId);
            jdbc.update("DELETE FROM auth.users WHERE id=?", userId);
            String hour = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC).format(NOW);
            redis.delete(List.of("practice:start:" + userId + ":" + hour,
                    "practice:ans:" + userId + ":" + hour));
        }
        for (long questionId : questionIds) {
            jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", questionId);
            jdbc.update("DELETE FROM learning.questions WHERE id=?", questionId);
        }
        if (topicId != null) {
            jdbc.update("DELETE FROM learning.topics WHERE id=?", topicId);
        }
        if (knowledgeId != null) {
            jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", knowledgeId);
        }
    }

    @ParameterizedTest
    @CsvSource({"QUIZ, false", "QUIZ, true", "QUIZ_RETRY, false", "QUIZ_RETRY, true"})
    void submittingQuizPreservesAllProgressAndAllocatesNoMasteryEvidence(
            PracticeSource source, boolean existingProgress) {
        if (existingProgress) {
            long control = start(PracticeSource.TOPIC_TEST, 1);
            finish(control, 1, "B");
            assertThat(jdbc.queryForObject("SELECT cardinality(evidence_item_ids) "
                    + "FROM learning.user_progress WHERE user_id=? AND target_type='KNOWLEDGE_POINT' "
                    + "AND target_id=?", Integer.class, userId, knowledgeId)).isEqualTo(1);
        }
        List<String> before = progressSnapshot();
        assertThat(before.size()).isEqualTo(existingProgress ? 1 : 0);
        Instant servedAt = now;
        long session = start(source, 10);
        var result = finish(session, 10, "A");

        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.suspicious()).isFalse();
        assertThat(result.items()).hasSize(10).allSatisfy(item -> assertThat(item.correct()).isTrue());
        jdbc.queryForObject("SELECT kind,ref_type,ref_id,served_at,submitted_at,duration_ms,suspicious "
                + "FROM learning.attempts WHERE id=?", (row, index) -> {
                    assertThat(row.getString("kind")).isEqualTo(source.kind().name());
                    assertThat(row.getString("ref_type")).isEqualTo(source.name());
                    assertThat(row.getLong("ref_id")).isEqualTo(topicId);
                    assertThat(row.getTimestamp("served_at").toInstant()).isEqualTo(servedAt);
                    assertThat(row.getTimestamp("submitted_at").toInstant()).isEqualTo(now);
                    assertThat(row.getInt("duration_ms")).isEqualTo(30_000);
                    assertThat(row.getBoolean("suspicious")).isFalse();
                    return true;
                }, session);
        assertThat(progressSnapshot()).containsExactlyElementsOf(before);

        List<Long> quizItemIds = jdbc.queryForList("SELECT id FROM learning.attempt_items "
                + "WHERE attempt_id=? ORDER BY order_index", Long.class, session);
        assertThat(quizItemIds).hasSize(10);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(allocator.allocateNew(attempts.findById(session).orElseThrow(),
                    items.findByAttemptIdOrderByOrderIndexAsc(session))).isEmpty();
            assertThat(allocator.reallocate(userId, Set.copyOf(quizItemIds))).isEmpty();
        });
        assertThat(progressSnapshot()).containsExactlyElementsOf(before);
    }

    private List<String> progressSnapshot() {
        // PostgreSQL renders every stored column, including evidence IDs and raw timestamps.
        return jdbc.queryForList("SELECT row_to_json(progress)::text FROM learning.user_progress progress "
                + "WHERE user_id=? ORDER BY target_type,target_id", String.class, userId);
    }

    private long start(PracticeSource source, int count) {
        List<ServedItem> served = IntStream.rangeClosed(1, count).mapToObj(no -> {
            long question = jdbc.queryForObject("INSERT INTO learning.questions"
                    + "(qtype,hsk_level,stem,options,correct_key,status,code) "
                    + "VALUES ('MULTIPLE_CHOICE',3,'Chọn nghĩa của 水.',?::jsonb,'A','APPROVED',?) RETURNING id",
                    Long.class, OPTIONS, "QUIZ-" + UUID.randomUUID());
            questionIds.add(question);
            jdbc.update("INSERT INTO learning.question_knowledge_points"
                    + "(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')", question, knowledgeId);
            return new ServedItem(no, ItemType.QUESTION_CHOICE, Skill.READING, null,
                    new ServedItem.Prompt("Chọn nghĩa của 水.", null, null, null, null, null, null, null, null),
                    List.of(new ServedItem.Option("A", "nước"), new ServedItem.Option("B", "lửa")),
                    "A", null, "水 nghĩa là nước.", new ServedItem.Rules(2000, 0, false), question);
        }).toList();
        return practice.start(new PracticeService.StartPracticeCommand(userId, source, topicId,
                null, served, List.of(), null, Map.of())).sessionId();
    }

    private PracticeService.FinishResult finish(long session, int count, String answer) {
        now = now.plusSeconds(30);
        return practice.finish(userId, session, IntStream.rangeClosed(1, count)
                .mapToObj(no -> new Answer(no, answer, 3000, 0, false, false, null)).toList());
    }
}
