package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.contest.ContestAttemptIntegrationTest.Fixture;
import com.cnhsk.learning.contest.ContestAttemptService;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@TestPropertySource(properties = "cnhsk.assistant.help-location=classpath*:assistant-help/valid/*.md")
class ContestMasteryIsolationIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T04:00:00Z");
    @Autowired private ContestAttemptService contests;
    @Autowired private PracticeService practice;
    @Autowired private EvidenceAllocator allocator;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private StubClaudeClient stub;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private Clock clock;
    private Fixture fixture;
    private Instant now;
    private long user;
    private long exam;
    private Long knowledge;

    @BeforeEach
    void setup() {
        now = NOW;
        when(clock.instant()).thenAnswer(ignored -> now);
        when(clock.millis()).thenAnswer(ignored -> now.toEpochMilli());
        when(clock.withZone(any(ZoneId.class))).thenAnswer(invocation -> Clock.fixed(now, invocation.getArgument(0)));
        fixture = new Fixture(jdbc);
        user = fixture.user();
        exam = fixture.exam("DRAFT");
        String kid = jdbc.queryForObject("SELECT 'NL3-' || number FROM generate_series(800,899) number "
                + "WHERE NOT EXISTS (SELECT 1 FROM learning.knowledge_points WHERE code='NL3-' || number) "
                + "ORDER BY number LIMIT 1", String.class);
        knowledge = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) "
                + "VALUES (?,'Nút kiểm thử cuộc thi','NL','阅读',3) RETURNING id", Long.class, kid);
        long question = fixture.question(exam, "Chọn nghĩa của 水.", 0, 1, "APPROVED", "MULTIPLE_CHOICE", 99);
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')", question, knowledge);
    }

    @AfterEach
    void cleanup() {
        redis.delete(dailyKey());
        fixture.close();
        if (knowledge != null) jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", knowledge);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void AT13_fullContestEngineKeepsAssistantOpenAndPreservesEveryMasteryColumn(boolean existingProgress) throws Exception {
        if (existingProgress) {
            jdbc.update("""
                    INSERT INTO learning.user_progress(user_id,target_type,target_id,mastery,stability,difficulty,
                        review_count,lapse_count,next_review_at,mp_state,confidence)
                    VALUES (?,'KNOWLEDGE_POINT',?,0.630,2.5,4.2,7,2,now() + interval '3 days','review',0.8)
                    """, user, knowledge);
        }
        List<String> before = progressSnapshot();
        assertThat(before).hasSize(existingProgress ? 1 : 0);
        int calls = stub.callCount();
        var session = contests.start(user, exam, exam, NOW.plusSeconds(60));
        now = NOW.plusSeconds(30);
        practice.answer(user, session.sessionId(), new Answer(1, "A", 3000, 0, false, false, null));
        assertThat(contests.submit(user, exam)).isEqualTo(now);
        assertThat(jdbc.queryForMap("SELECT status,score,max_score,suspicious FROM learning.attempts WHERE id=?", session.sessionId()))
                .containsEntry("status", "SUBMITTED").containsEntry("score", 1L).containsEntry("max_score", 1L)
                .containsEntry("suspicious", false);
        assertThat(progressSnapshot()).containsExactlyElementsOf(before);
        List<Long> evidence = jdbc.queryForList("SELECT id FROM learning.attempt_items WHERE attempt_id=?", Long.class, session.sessionId());
        assertThat(evidence).hasSize(1);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(allocator.allocateNew(attempts.findById(session.sessionId()).orElseThrow(),
                    items.findByAttemptIdOrderByOrderIndexAsc(session.sessionId()))).isEmpty();
            assertThat(allocator.reallocate(user, Set.copyOf(evidence))).isEmpty();
        });
        assertThat(progressSnapshot()).containsExactlyElementsOf(before);
        stub.enqueue(new ClaudeClient.Completion("Hướng dẫn học tự soạn", 1, 1));
        mvc.perform(post("/api/assistant/ask").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"Học thế nào?\"}"))
                .andExpect(status().isOk());
        assertThat(stub.callCount()).isEqualTo(calls + 1);
        assertThat(redis.opsForValue().get(dailyKey())).isEqualTo("1");
    }

    private List<String> progressSnapshot() {
        return jdbc.queryForList("SELECT row_to_json(progress)::text FROM learning.user_progress progress "
                + "WHERE user_id=? ORDER BY target_type,target_id", String.class, user);
    }

    private String bearer() {
        return "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), true, UUID.randomUUID());
    }

    private String dailyKey() {
        return "assistant:ask:" + user + ":" + LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))
                .format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
