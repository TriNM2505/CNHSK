package com.cnhsk.learning.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrammarQueryIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @Autowired private GrammarQueryService service;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    private long owner;
    private long other;

    @BeforeAll
    void identitiesThroughAuthApi() throws Exception {
        owner = createUser();
        other = createUser();
    }

    @Test
    void FR001_AT01_acceptsZeroToThreeRepeatedLevelsAndRejectsInvalidValues() throws Exception {
        Lexeme first = grammar(1, "第一");
        Lexeme advanced = grammar(8, "第八");
        assertThat(list(owner).size()).isGreaterThanOrEqualTo(2);
        JsonNode grouped = list(owner, "7", "8", "9");
        assertThat(grouped).allSatisfy(row -> assertThat(row.path("hskLevel").asInt()).isBetween(7, 9));
        assertThat(find(grouped, advanced.getId()).path("text").asText()).isEqualTo(advanced.getText());
        JsonNode levelOne = find(list(owner, "1"), first.getId());
        assertThat(levelOne.path("hskLevel").asInt()).isEqualTo(1);
        assertThat(levelOne.path("structure").asText()).isEqualTo("S + 是 + … + 的");
        for (String[] invalid : List.of(new String[] {"0"}, new String[] {"10"}, new String[] {"abc"},
                new String[] {"1", "2", "3", "4"})) {
            var request = get("/api/grammar").header("Authorization", bearer(owner));
            for (String value : invalid) request.param("hskLevel", value);
            mvc.perform(request).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void FR002_FR003_AT02_usesOnlyTheCurrentUsersSubmittedLessonsAndBestNonZeroScore() throws Exception {
        Lexeme point = grammar(2, "是……的");
        attempt(owner, point, "IN_PROGRESS", 100, 100, NOW.minusSeconds(900));
        attempt(owner, point, "SUBMITTED", 3, 5, NOW.minusSeconds(600));
        attempt(owner, point, "SUBMITTED", 4, 5, NOW.minusSeconds(60));
        attempt(owner, point, "SUBMITTED", 0, 0, NOW.minusSeconds(10));
        attempt(other, point, "SUBMITTED", 5, 5, NOW);

        JsonNode ownerRow = find(list(owner), point.getId());
        assertThat(ownerRow.path("learned").asBoolean()).isTrue();
        assertThat(ownerRow.path("bestPercent").asInt()).isEqualTo(80);
        assertThat(ownerRow.path("lastStudiedAt").asText()).isEqualTo(NOW.minusSeconds(10).toString());
        JsonNode otherRow = find(list(other), point.getId());
        assertThat(otherRow.path("bestPercent").asInt()).isEqualTo(100);
        assertThat(otherRow.path("lastStudiedAt").asText()).isEqualTo(NOW.toString());
    }

    @Test
    void FR005_AT03_AT04_hidesExercisesAndReturnsNotFoundForWrongKind() throws Exception {
        Lexeme point = grammar(3, "把字句");
        JsonNode detail = mapper.readTree(mvc.perform(get("/api/grammar/{id}", point.getId())
                        .header("Authorization", bearer(owner))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
        assertThat(detail.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "text", "titleVi", "hskLevel",
                "structure", "explanation", "commonMistake", "examples", "exerciseCount", "learned",
                "bestPercent", "lastStudiedAt");
        assertThat(detail.toString()).doesNotContain("exercises", "answerKey", "A-secret");
        Lexeme word = new Lexeme(LexemeKind.WORD, "词" + UUID.randomUUID(), "từ");
        word.setHskLevel((short) 1);
        lexemes.saveAndFlush(word);
        for (String id : List.of(word.getId().toString(), "999999999", "0", "abc")) {
            mvc.perform(get("/api/grammar/{id}", id).header("Authorization", bearer(owner)))
                    .andExpect(id.equals("0") || id.equals("abc") ? status().isUnprocessableEntity() : status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value(id.equals("0") || id.equals("abc")
                            ? "VALIDATION_FAILED" : "GRAMMAR_POINT_NOT_FOUND"));
        }
    }

    @Test
    void FR003_listUsesOneAggregateQueryForAllGrammarPoints() {
        grammar(1, "一");
        grammar(2, "二");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        try {
            entityManager.flush();
            entityManager.clear();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            service.list(owner, List.of());
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        String body = mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test", "password", password,
                "confirmPassword", password, "displayName", "Grammar test user", "acceptTerms", true));
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private Lexeme grammar(int level, String text) {
        Lexeme point = new Lexeme(LexemeKind.GRAMMAR, text + UUID.randomUUID(), "Tiêu đề tiếng Việt");
        point.setHskLevel((short) level);
        point.setExtra(new LexemeExtra(List.of(new LexemeExtra.Example("我是学生。", "Wǒ shì xuéshēng.", "Tôi là học sinh.")),
                null, "S + 是 + … + 的", "Giải thích dài", "Lỗi thường gặp", List.of(
                        new LexemeExtra.Exercise(1, "___", List.of(new LexemeExtra.Option("A", "A-secret"),
                                new LexemeExtra.Option("B", "B")), "A", "Giải thích đáp án"))));
        return lexemes.saveAndFlush(point);
    }

    private void attempt(long userId, Lexeme point, String status, long score, long maxScore, Instant submittedAt) {
        Attempt attempt = new Attempt(userId, "PRACTICE", "GRAMMAR_LESSON", point.getId(),
                NOW.minusSeconds(1200), NOW.plusSeconds(3600), mapper.createObjectNode());
        attempt.setStatus(status);
        if ("SUBMITTED".equals(status)) attempt.setSubmittedAt(submittedAt);
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempts.saveAndFlush(attempt);
    }

    private JsonNode list(long userId, String... levels) throws Exception {
        var request = get("/api/grammar").header("Authorization", bearer(userId));
        for (String level : levels) request.param("hskLevel", level);
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("id").asLong() == id) return row;
        throw new AssertionError("Grammar point missing from response: " + id);
    }

    private String bearer(long userId) {
        return "Bearer " + jwt.issueAccessToken(userId, List.of("LEARNER"), false, UUID.randomUUID());
    }
}
