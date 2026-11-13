package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = "cnhsk.quiz.question-count=12")
@Transactional
class QuizCatalogIntegrationTest extends AbstractIntegrationTest {
    private static final Instant SUBMITTED = Instant.parse("2026-11-14T04:05:06.123456Z");
    private static final String OPTIONS = "[{\"key\":\"A\",\"text\":\"nước\",\"is_correct\":true},"
            + "{\"key\":\"B\",\"text\":\"lửa\",\"is_correct\":false}]";
    @Autowired private QuizCatalog catalog;
    @Autowired private QuizRepository repository;
    @Autowired private QuizConfig config;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManagerFactory factory;
    private long user;
    private int nextOrder;

    @BeforeEach
    void setup() {
        user = user();
        nextOrder = jdbc.queryForObject("SELECT least(coalesce(min(order_index),0),0)-1000 "
                + "FROM learning.topics", Integer.class);
    }

    @Test
    void onlyApprovedPrimaryQuestionsOfTheTopicsOwnKidCount() {
        long baseline = catalog.list(user, 1, 0, 50).total();
        Topic eligible = topic(1, 12);
        questions(eligible.kid(), 1, 5, "PENDING_REVIEW");
        questions(eligible.kid(), 1, 1, "ARCHIVED");
        questions(eligible.kid(), 1, 1, "REJECTED");
        Topic sharedKid = topic(1, eligible.kid());
        Topic insufficient = topic(1, 11);
        long foreignKid = node(1);
        long foreignQuestion = questions(foreignKid, 1, 1, "APPROVED").get(0);
        // This question has PRIMARY on another KID and only SECONDARY on this topic's KID.
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) "
                + "VALUES (?,?,'SECONDARY')", foreignQuestion, insufficient.kid());
        long unlinked = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level,order_index) "
                + "VALUES (?,'Chủ đề chưa gắn KID',1,?) RETURNING id", Long.class,
                "QC-" + UUID.randomUUID(), nextOrder++);

        var result = catalog.list(user, 1, 0, 50);
        assertThat(result.total()).isEqualTo(baseline + 2);
        assertThat(result.items()).extracting(QuizCatalog.QuizListing::topicId)
                .contains(eligible.id(), sharedKid.id()).doesNotContain(insufficient.id(), unlinked);
        assertThat(result.items().get(0).availableQuestions()).isEqualTo(12);
        assertThat(result.items().get(1).availableQuestions()).isEqualTo(12);
    }

    @Test
    void configuredTwelveQuestionThresholdControlsVisibilityAndDtoQuestionCount() {
        assertThat(config.getQuestionCount()).isEqualTo(12);
        Topic eleven = topic(1, 11);
        Topic twelve = topic(1, 12);
        var result = catalog.list(user, 1, 0, 50);
        assertThat(result.items()).extracting(QuizCatalog.QuizListing::topicId)
                .contains(twelve.id()).doesNotContain(eleven.id());
        assertThat(result.items()).allSatisfy(item -> assertThat(item.questionCount()).isEqualTo(12));
    }

    @Test
    void defaultTenQuestionThresholdShowsTenAndHidesNine() {
        Topic nine = topic(1, 9);
        Topic ten = topic(1, 10);
        QuizCatalog defaults = new QuizCatalog(repository, new QuizConfig());
        var result = defaults.list(user, 1, 0, 50);
        assertThat(result.items()).extracting(QuizCatalog.QuizListing::topicId)
                .contains(ten.id()).doesNotContain(nine.id());
        assertThat(result.items()).allSatisfy(item -> assertThat(item.questionCount()).isEqualTo(10));
    }

    @Test
    void submittedHistoryIsPrivateAndSuspiciousFirstAttemptIsStillTheFirstAttempt() throws Exception {
        Topic topic = topic(1, 12);
        long otherUser = user();
        attempt(otherUser, topic.id(), "QUIZ", "QUIZ", "SUBMITTED", 100, false);
        attempt(user, topic.id(), "QUIZ", "QUIZ", "SUBMITTED", 80, true);
        attempt(user, topic.id(), "QUIZ", "QUIZ", "IN_PROGRESS", 100, false);
        attempt(user, topic.id(), "PRACTICE", "QUIZ", "SUBMITTED", 100, false);
        attempt(user, topic.id(), "PRACTICE", "QUIZ_RETRY", "SUBMITTED", 70, false);
        attempt(user, topic.id(), "PRACTICE", "QUIZ_RETRY", "SUBMITTED", 95, true);
        attempt(user, topic.id(), "PRACTICE", "QUIZ_RETRY", "IN_PROGRESS", 100, false);
        attempt(user, topic.id(), "PRACTICE", "QUIZ_RETRY", "ABANDONED", 100, false);
        attempt(user, topic.id(), "EXAM", "QUIZ_RETRY", "SUBMITTED", 100, false);
        attempt(user, topic.id(), "PRACTICE", "TOPIC_PRACTICE", "SUBMITTED", 100, false);

        JsonNode response = mapper.readTree(mvc.perform(get("/api/quizzes").param("hsk", "1")
                        .param("userId", Long.toString(otherUser)).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        JsonNode item = response.path("items").get(0);
        assertThat(item.path("topicId").asLong()).isEqualTo(topic.id());
        assertThat(item.path("myFirstAttempt").path("score").asLong()).isEqualTo(80);
        assertThat(item.path("myFirstAttempt").path("suspicious").asBoolean()).isTrue();
        assertThat(item.path("myBestRetryScore").asLong()).isEqualTo(95);
        Instant raw = jdbc.queryForObject("SELECT submitted_at FROM learning.attempts "
                + "WHERE user_id=? AND kind='QUIZ' AND status='SUBMITTED'", (row, index) ->
                        row.getTimestamp(1).toInstant(), user);
        assertThat(raw).isEqualTo(SUBMITTED);
        String serialized = item.path("myFirstAttempt").path("submittedAt").asText();
        assertThat(serialized).endsWith("Z");
        assertThat(Instant.parse(serialized)).isEqualTo(raw);
    }

    @Test
    void getUsesContractFieldsDefaultsAndExplicitJsonNulls() throws Exception {
        Topic topic = topic(1, 12);
        JsonNode response = mapper.readTree(mvc.perform(get("/api/quizzes").param("hsk", "1")
                        .header("Authorization", bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(response.fieldNames()).toIterable().containsExactlyInAnyOrder("items", "page", "size", "total");
        assertThat(response.path("page").asInt()).isZero();
        assertThat(response.path("size").asInt()).isEqualTo(20);
        JsonNode item = response.path("items").get(0);
        assertThat(item.path("topicId").asLong()).isEqualTo(topic.id());
        assertThat(item.fieldNames()).toIterable().containsExactlyInAnyOrder("topicId", "topicName", "hskLevel",
                "questionCount", "availableQuestions", "myFirstAttempt", "myBestRetryScore");
        assertThat(item.get("myFirstAttempt").isNull()).isTrue();
        assertThat(item.get("myBestRetryScore").isNull()).isTrue();
        assertThat(item.path("questionCount").asInt()).isEqualTo(12);
        assertThat(item.path("availableQuestions").asLong()).isEqualTo(12);
        mvc.perform(get("/api/quizzes").param("hsk", "9").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void paginationFiltersAndStableOrderingRetainTotalsBeyondTheLastPage() {
        long baseline = catalog.list(user, 1, 0, 50).total();
        Topic first = topic(1, 12);
        int tieOrder = nextOrder;
        Topic tieOne = topic(1, 12);
        Topic tieTwo = topic(1, 12);
        jdbc.update("UPDATE learning.topics SET order_index=? WHERE id IN (?,?)", tieOrder, tieOne.id(), tieTwo.id());
        Topic levelTwo = topic(2, 12);
        jdbc.update("UPDATE learning.topics SET order_index=? WHERE id=?", tieOrder - 5000, levelTwo.id());

        var pageZero = catalog.list(user, 1, 0, 2);
        assertThat(pageZero.total()).isEqualTo(baseline + 3);
        assertThat(pageZero.items()).extracting(QuizCatalog.QuizListing::topicId).containsExactly(first.id(), tieOne.id());
        assertThat(catalog.list(user, 1, 1, 2).items().get(0).topicId()).isEqualTo(tieTwo.id());
        assertThat(catalog.list(user, null, 0, 50).items().get(0).topicId()).isEqualTo(first.id());
        assertThat(catalog.list(user, 2, 0, 50).items()).allSatisfy(item -> assertThat(item.hskLevel()).isEqualTo(2));
        var beyond = catalog.list(user, 1, Integer.MAX_VALUE, 50);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(baseline + 3);
        assertThat(beyond.page()).isEqualTo(Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @CsvSource(value = {"hsk|0", "hsk|10", "hsk|abc", "hsk|''", "hsk|2147483648",
            "page|-1", "page|abc", "page|1.5", "page|2147483648", "page|''",
            "size|0", "size|51", "size|abc", "size|2147483648", "size|''"}, delimiter = '|')
    void invalidQueryParametersUseTheExisting422Error(String field, String value) throws Exception {
        mvc.perform(get("/api/quizzes").param(field, value).header("Authorization", bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value(field))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 50})
    void catalogAndPersonalScoresAlwaysUseOneSelect(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Topic topic = topic(1, 12);
            attempt(user, topic.id(), "QUIZ", "QUIZ", "SUBMITTED", 80, false);
            attempt(user, topic.id(), "PRACTICE", "QUIZ_RETRY", "SUBMITTED", 90, false);
        }
        Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mvc.perform(get("/api/quizzes").param("hsk", "1").param("size", "50")
                    .header("Authorization", bearer())).andExpect(status().isOk());
            assertThat(statistics.getPrepareStatementCount()).as("catalog SELECT count for %s fixture topics", count)
                    .isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    @Test
    void reportP95ForThreeHundredTopicsWithoutATimingGate() throws Exception {
        long existing = catalog.list(user, 1, 0, 50).total();
        for (int i = 0; i < 300; i++) topic(1, 12);
        String token = bearer();
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/quizzes").param("hsk", "1").param("size", "50")
                    .header("Authorization", token)).andExpect(status().isOk());
        }
        Statistics statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        double[] samples = new double[20];
        try {
            for (int i = 0; i < samples.length; i++) {
                statistics.clear();
                long started = System.nanoTime();
                mvc.perform(get("/api/quizzes").param("hsk", "1").param("size", "50")
                                .header("Authorization", token)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.total").value(existing + 300));
                samples[i] = (System.nanoTime() - started) / 1_000_000.0;
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            }
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT,
                "QUIZ_CATALOG_P95 fixture_topics=300 existing_topics=%d samples=20 p95_ms=%.3f query_count=1%n",
                existing, samples[(int) Math.ceil(samples.length * 0.95) - 1]);
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) "
                + "VALUES (?,'fixture-unused','Quiz catalog fixture') RETURNING id", Long.class,
                "quiz-catalog-" + UUID.randomUUID() + "@example.test");
    }

    private long node(int level) {
        String prefix = "VB" + level + "-";
        String code = jdbc.queryForObject("SELECT ? || lpad(n::text,3,'0') FROM generate_series(0,999) n "
                + "WHERE NOT EXISTS (SELECT 1 FROM learning.knowledge_points WHERE code=? || lpad(n::text,3,'0')) "
                + "ORDER BY n LIMIT 1", String.class, prefix, prefix);
        return jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) "
                + "VALUES (?,'Nút kiểm thử catalog','VB','阅读',?) RETURNING id", Long.class, code, level);
    }

    private Topic topic(int level, int count) {
        Topic topic = topic(level, node(level));
        questions(topic.kid(), level, count, "APPROVED");
        return topic;
    }

    private Topic topic(int level, long kid) {
        long id = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level,knowledge_point_id,order_index) "
                + "VALUES (?,'Quiz tự soạn',?,?,?) RETURNING id", Long.class,
                "QC-" + UUID.randomUUID(), level, kid, nextOrder++);
        return new Topic(id, kid);
    }

    private List<Long> questions(long kid, int level, int count, String status) {
        List<Long> ids = jdbc.queryForList("INSERT INTO learning.questions"
                + "(qtype,hsk_level,stem,options,correct_key,status,code,review_reason) "
                + "SELECT 'MULTIPLE_CHOICE',?,'Chọn nghĩa của 水.',?::jsonb,'A',?,? || '-' || n,? "
                + "FROM generate_series(1,?) n RETURNING id", Long.class,
                level, OPTIONS, status, "QC-" + UUID.randomUUID(),
                status.equals("REJECTED") ? "Lý do kiểm thử tự soạn" : null, count);
        List<Object[]> labels = new ArrayList<>();
        ids.forEach(id -> labels.add(new Object[] {id, kid}));
        jdbc.batchUpdate("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) "
                + "VALUES (?,?,'PRIMARY')", labels);
        return ids;
    }

    private void attempt(long owner, long topic, String kind, String source, String status, long score, boolean suspicious) {
        jdbc.update("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,status,score,submitted_at,suspicious) "
                + "VALUES (?,?,?,?,?,?,?,?)", owner, kind, source, topic, status, score,
                status.equals("SUBMITTED") ? Timestamp.from(SUBMITTED) : null, suspicious);
    }

    private String bearer() { return "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID()); }
    private record Topic(long id, long kid) { }
}
