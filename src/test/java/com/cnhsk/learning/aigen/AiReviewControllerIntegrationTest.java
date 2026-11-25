package com.cnhsk.learning.aigen;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@Import(AiReviewControllerIntegrationTest.FixedTimeConfiguration.class)
class AiReviewControllerIntegrationTest extends AbstractIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    static final String ROOT = "/api/admin/ai-questions";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    private Fixture fixture;
    private long actor;

    @BeforeEach
    void setup() {
        fixture = new Fixture(jdbc);
        actor = fixture.user();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER"})
    void allowedReviewersCanListWithEitherCredentialAndApproveWithoutAViewedMarker(String role) throws Exception {
        long id = fixture.question("AI", "PENDING_REVIEW", 1, NOW, true);
        for (boolean cookie : List.of(false, true)) {
            MockHttpServletRequestBuilder request = get(ROOT);
            if (cookie) request.cookie(new Cookie("access_token", token(role)));
            else request.header("Authorization", bearer(role));
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));
        }
        mvc.perform(post(ROOT + "/{id}/approve", id).header("Authorization", bearer(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedAt").value(NOW.toString()));
        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM learning.questions WHERE id=?", Long.class, id)).isEqualTo(actor);
        assertThat(fixture.audits(id, "AI_QUESTION_APPROVE")).isEqualTo(1);
        assertThat(redis.hasKey(Fixture.key(actor, id))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "LEARNER", "MODERATOR", "ACCOUNTANT", "CONTENT_EDITOR"})
    void otherRolesAreForbiddenBeforeInputValidationOnEveryOperation(String role) throws Exception {
        mvc.perform(get(ROOT).param("hsk", "bad").header("Authorization", bearer(role)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        for (String path : List.of("/abc/approve", "/abc/reject", "/bulk-approve")) {
            mvc.perform(post(ROOT + path).header("Authorization", bearer(role)).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test
    void unauthenticatedRequestsAre401AndCookieWritesRequireMatchingCsrf() throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        for (String path : List.of("/1/approve", "/1/reject", "/bulk-approve")) {
            mvc.perform(post(ROOT + path)).andExpect(status().isUnauthorized());
            for (String header : List.of("", "wrong")) {
                var request = post(ROOT + path).cookie(new Cookie("access_token", token("TEACHER")),
                        new Cookie("XSRF-TOKEN", "review-csrf"));
                if (!header.isEmpty()) request.header("X-XSRF-TOKEN", header);
                mvc.perform(request).andExpect(status().isForbidden());
            }
        }
        long id = fixture.question("AI", "PENDING_REVIEW", 1, NOW, true);
        mvc.perform(post(ROOT + "/{id}/approve", id).cookie(new Cookie("access_token", token("TEACHER")),
                        new Cookie("XSRF-TOKEN", "review-csrf")).header("X-XSRF-TOKEN", "review-csrf"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("invalidFilters")
    void invalidQueueParametersReturn422WithFieldDetails(String field, String value) throws Exception {
        mvc.perform(get(ROOT).param(field, value).header("Authorization", bearer("TEACHER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value(field)).andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    static Stream<Arguments> invalidFilters() {
        return Stream.of(Arguments.of("hsk", "0"), Arguments.of("hsk", "10"), Arguments.of("hsk", "1.5"),
                Arguments.of("hsk", ""), Arguments.of("hsk", "2147483648"), Arguments.of("hsk", "abc"),
                Arguments.of("page", "-1"), Arguments.of("page", ""), Arguments.of("page", "2147483648"),
                Arguments.of("size", "0"), Arguments.of("size", "51"), Arguments.of("size", "1.5"), Arguments.of("size", ""),
                Arguments.of("kid", "x".repeat(21)), Arguments.of("from", "2026-11-16"), Arguments.of("from", ""),
                Arguments.of("to", "not-a-date"), Arguments.of("to", ""));
    }

    @Test
    void reversedDateRangeReturns422AndAllNineHskLevelsRemainAccepted() throws Exception {
        mvc.perform(get(ROOT).param("from", NOW.toString()).param("to", NOW.minusSeconds(1).toString())
                        .header("Authorization", bearer("TEACHER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        for (int hsk = 1; hsk <= 9; hsk++) {
            mvc.perform(get(ROOT).param("hsk", Integer.toString(hsk)).header("Authorization", bearer("TEACHER")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void queueFiltersIntersectIncludeBothLabelRolesAndSortOldestThenId() throws Exception {
        long first = fixture.question("AI", "PENDING_REVIEW", 2, NOW.minusSeconds(100), true);
        long second = fixture.question("AI", "PENDING_REVIEW", 2, NOW.minusSeconds(100), true);
        long later = fixture.question("AI", "PENDING_REVIEW", 2, NOW, true);
        fixture.question("AI", "PENDING_REVIEW", 1, NOW, true);
        fixture.question("AI", "APPROVED", 2, NOW, true);
        fixture.question("AI", "REJECTED", 2, NOW, true);
        fixture.question("AI", "ARCHIVED", 2, NOW, true);
        fixture.question("IMPORT", "PENDING_REVIEW", 2, NOW, true);
        fixture.question("MANUAL", "PENDING_REVIEW", 2, NOW, true);
        long secondary = fixture.node("TV");
        fixture.label(second, secondary, "SECONDARY");
        redis.opsForValue().set(Fixture.key(actor, second), "1", java.time.Duration.ofHours(24));
        JsonNode all = list(get(ROOT).param("hsk", "2").param("from", NOW.minusSeconds(100).toString())
                .param("to", NOW.toString()).param("kid", fixture.kid));
        assertThat(ids(all)).containsExactly(first, second, later);
        assertThat(all.path("totalElements").asLong()).isEqualTo(3);
        assertThat(all.path("items").get(1).path("viewedByMe").asBoolean()).isTrue();
        assertThat(all.path("items").get(0).path("viewedByMe").asBoolean()).isFalse();
        assertThat(all.path("items").get(0).path("runId").isNull()).isTrue();
        JsonNode bySecondary = list(get(ROOT).param("kid", fixture.code(secondary)));
        assertThat(ids(bySecondary)).containsExactly(second);
        assertThat(bySecondary.path("items").get(0).path("labels")).hasSize(2);
        JsonNode page = list(get(ROOT).param("kid", fixture.kid).param("hsk", "2").param("page", "1").param("size", "2"));
        assertThat(ids(page)).containsExactly(later);
        assertThat(page.path("totalElements").asLong()).isEqualTo(3);
        JsonNode pastEnd = list(get(ROOT).param("kid", fixture.kid).param("page", "2147483647"));
        assertThat(pastEnd.path("items")).isEmpty();
        assertThat(pastEnd.path("totalElements").asLong()).isEqualTo(4);
        assertThat(list(get(ROOT).param("kid", "NP9-999")).path("items")).isEmpty();
        for (long id : List.of(first, later)) assertThat(redis.hasKey(Fixture.key(actor, id))).isFalse();
        redis.delete(Fixture.key(actor, second));
    }

    @Test
    void queueFiltersByQuestionTypeAndTrueFalseDraftsCanBeApproved() throws Exception {
        long choice = fixture.question("AI", "PENDING_REVIEW", 2, NOW, true);
        long statement = fixture.question("AI", "PENDING_REVIEW", 2, NOW, true);
        jdbc.update("UPDATE learning.questions SET qtype='TRUE_FALSE', stem='北京是中国的首都。', options=CAST(? AS jsonb) WHERE id=?",
                "[{\"key\":\"A\",\"text\":\"Đúng\",\"is_correct\":true},{\"key\":\"B\",\"text\":\"Sai\",\"is_correct\":false}]",
                statement);
        JsonNode onlyStatements = list(get(ROOT).param("kid", fixture.kid).param("qtype", "TRUE_FALSE"));
        assertThat(ids(onlyStatements)).containsExactly(statement);
        assertThat(onlyStatements.path("items").get(0).path("qtype").asText()).isEqualTo("TRUE_FALSE");
        assertThat(ids(list(get(ROOT).param("kid", fixture.kid).param("qtype", "MULTIPLE_CHOICE")))).containsExactly(choice);
        mvc.perform(get(ROOT).param("qtype", "ESSAY").header("Authorization", bearer("TEACHER")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(ROOT + "/" + statement + "/approve").header("Authorization", bearer("TEACHER")))
                .andExpect(status().isOk());
        assertThat(fixture.status(statement)).isEqualTo("APPROVED");
    }

    @Test
    void contentEditorsSeeNodesTheJobSkipsAndCanListOnlyAiQuestions() throws Exception {
        long generated = fixture.question("AI", "APPROVED", 1, NOW, true);
        fixture.question("IMPORT", "APPROVED", 1, NOW, true);
        long ready = fixture.node("TV");
        jdbc.update("UPDATE learning.knowledge_points SET can_do='Hiểu từ tự soạn' WHERE id=?", ready);
        jdbc.update("INSERT INTO learning.lexemes(kind,text,meaning_vi,knowledge_point_id) VALUES ('WORD',?,'nghĩa tự soạn',?)",
                "gap-" + UUID.randomUUID(), ready);
        String body = mvc.perform(get("/api/admin/ai-node-gaps").header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode gaps = mapper.readTree(body);
        List<String> kids = new ArrayList<>();
        gaps.path("nodes").forEach(node -> kids.add(node.path("kid").asText()));
        assertThat(kids).contains(fixture.kid).doesNotContain(fixture.code(ready));
        gaps.path("nodes").forEach(node -> {
            if (node.path("kid").asText().equals(fixture.kid)) {
                assertThat(node.path("missingCanDo").asBoolean()).isTrue();
                assertThat(node.path("lexemeCount").asLong()).isZero();
            }
        });
        assertThat(gaps.path("totalNodes").asLong()).isGreaterThanOrEqualTo(kids.size());
        mvc.perform(get("/api/admin/ai-node-gaps").header("Authorization", bearer("TEACHER"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/ai-node-gaps").header("Authorization", bearer("LEARNER"))).andExpect(status().isForbidden());

        String page = mvc.perform(get("/api/admin/questions").param("kid", fixture.kid).param("source", "AI")
                        .header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<Long> sourced = new ArrayList<>();
        mapper.readTree(page).path("items").forEach(item -> sourced.add(item.path("id").asLong()));
        assertThat(sourced).containsExactly(generated);
        mvc.perform(get("/api/admin/questions").param("source", "ROBOT").header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void summaryContainsEightyUnicodeCodePointsWithoutSplittingSupplementaryCharacters() throws Exception {
        long id = fixture.question("AI", "PENDING_REVIEW", 1, NOW, true);
        String stem = "𠀀".repeat(85);
        jdbc.update("UPDATE learning.questions SET stem=? WHERE id=?", stem, id);
        JsonNode row = list(get(ROOT).param("kid", fixture.kid)).path("items").get(0);
        assertThat(row.path("stemSummary").asText()).isEqualTo("𠀀".repeat(80));
        assertThat(row.path("generatedAt").asText()).isEqualTo(NOW.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIMARY", "STEM", "EXPLANATION", "TWO_CORRECT", "NO_CORRECT", "KEY_MISMATCH"})
    void malformedQuestionIs422WithDetailsAndNoTransitionOrAudit(String defect) throws Exception {
        long id = fixture.question("AI", "PENDING_REVIEW", 1, NOW, !defect.equals("PRIMARY"));
        switch (defect) {
            case "STEM" -> jdbc.update("UPDATE learning.questions SET stem='  ' WHERE id=?", id);
            case "EXPLANATION" -> jdbc.update("UPDATE learning.questions SET explanation=NULL WHERE id=?", id);
            case "TWO_CORRECT" -> jdbc.update("UPDATE learning.questions SET options=CAST(? AS jsonb) WHERE id=?", Fixture.OPTIONS.replace("false", "true"), id);
            case "NO_CORRECT" -> jdbc.update("UPDATE learning.questions SET options=CAST(? AS jsonb) WHERE id=?", Fixture.OPTIONS.replace("true", "false"), id);
            case "KEY_MISMATCH" -> jdbc.update("UPDATE learning.questions SET correct_key='B' WHERE id=?", id);
            default -> { }
        }
        mvc.perform(post(ROOT + "/{id}/approve", id).header("Authorization", bearer("TEACHER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("MALFORMED_QUESTION"))
                .andExpect(jsonPath("$.details").isNotEmpty());
        assertThat(fixture.status(id)).isEqualTo("PENDING_REVIEW");
        assertThat(fixture.audits(id, "AI_QUESTION_APPROVE")).isZero();
    }

    @Test
    void editedQuestionWithTwoLongOptionsCanBeApprovedWithoutGenerationOnlyLimits() throws Exception {
        long id = fixture.question("AI", "PENDING_REVIEW", 1, NOW, true);
        jdbc.update("UPDATE learning.questions SET stem=?, explanation=?, options=CAST(? AS jsonb) WHERE id=?",
                "自".repeat(501), "Giải ".repeat(250), Fixture.OPTIONS.replace("我", "我".repeat(201)), id);
        mvc.perform(post(ROOT + "/{id}/approve", id).header("Authorization", bearer("TEACHER"))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "9223372036854775808"})
    void invalidSingleQuestionIdsReturn422(String id) throws Exception {
        for (String action : List.of("approve", "reject")) {
            mvc.perform(post(ROOT + "/{id}/" + action, id).header("Authorization", bearer("TEACHER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Tự soạn\"}"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void validNumericIdsThatDoNotExistReturn404() throws Exception {
        for (String action : List.of("approve", "reject")) {
            mvc.perform(post(ROOT + "/{id}/" + action, Long.MAX_VALUE).header("Authorization", bearer("TEACHER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Tự soạn\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("QUESTION_NOT_FOUND"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"AI:APPROVED", "AI:REJECTED", "AI:ARCHIVED", "IMPORT:PENDING_REVIEW", "MANUAL:PENDING_REVIEW"})
    void nonPendingAndOtherSourcesReturn409ForBothTransitions(String pair) throws Exception {
        String[] values = pair.split(":");
        long id = fixture.question(values[0], values[1], 1, NOW, false);
        for (String action : List.of("approve", "reject")) {
            mvc.perform(post(ROOT + "/{id}/" + action, id).header("Authorization", bearer("TEACHER"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Không phù hợp\"}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("QUESTION_NOT_PENDING"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{}", "{\"reason\":null}", "{\"reason\":\"  \"}", "{\"reason\":1}", "{\"reason\":true}", "{\"reason\":[]}"})
    void invalidRejectBodiesReturn422(String body) throws Exception {
        long id = fixture.question("AI", "PENDING_REVIEW", 1, NOW, true);
        mvc.perform(post(ROOT + "/{id}/reject", id).header("Authorization", bearer("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        assertThat(fixture.status(id)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void rejectTrimsAndCountsUnicodeCodePointsRecordsAuditAndRejectsReplay() throws Exception {
        long id = fixture.question("AI", "PENDING_REVIEW", 1, NOW, true);
        String reason = "𠀀".repeat(1000);
        mvc.perform(post(ROOT + "/{id}/reject", id).header("Authorization", bearer("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("reason", reason + "𠀀"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(ROOT + "/{id}/reject", id).header("Authorization", bearer("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("reason", "  " + reason + "  "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewedAt").value(NOW.toString()));
        assertThat(jdbc.queryForObject("SELECT review_reason FROM learning.questions WHERE id=?", String.class, id)).isEqualTo(reason);
        assertThat(fixture.audits(id, "AI_QUESTION_REJECT")).isEqualTo(1);
        mvc.perform(post(ROOT + "/{id}/reject", id).header("Authorization", bearer("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"lần hai\"}"))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @MethodSource("invalidBulkBodies")
    void bulkIdsMustBeUniqueTypedPositiveLongsWithOneToFiftyItems(String body) throws Exception {
        mvc.perform(post(ROOT + "/bulk-approve").header("Authorization", bearer("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    static Stream<String> invalidBulkBodies() {
        return Stream.of("", "{", "{}", "{\"ids\":null}", "{\"ids\":[]}", "{\"ids\":[null]}",
                "{\"ids\":[0]}", "{\"ids\":[-1]}", "{\"ids\":[1,1]}", "{\"ids\":[\"1\"]}",
                "{\"ids\":[1.0]}", "{\"ids\":[true]}", "{\"ids\":[9223372036854775808]}",
                "{\"ids\":" + LongStream.rangeClosed(1, 51).boxed().toList() + "}");
    }

    @Test
    void fiftyUnviewedIdsReturnOrderedNotViewedWithoutChangingQuestions() throws Exception {
        List<Long> ids = LongStream.rangeClosed(1, 50).map(value -> Long.MAX_VALUE - value).boxed().toList();
        JsonNode result = mapper.readTree(mvc.perform(post(ROOT + "/bulk-approve").header("Authorization", bearer("TEACHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("ids", ids))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("results");
        assertThat(result).extracting(row -> row.path("id").asLong()).containsExactlyElementsOf(ids);
        assertThat(result).allSatisfy(row -> assertThat(row.path("outcome").asText()).isEqualTo("NOT_VIEWED"));
    }

    @Test
    void fiveHundredSelfAuthoredPendingRowsProduceQueueTimingSamplesAndP95() throws Exception {
        for (int index = 0; index < 500; index++) fixture.question("AI", "PENDING_REVIEW", 1, NOW.plusSeconds(index), true);
        for (int warmup = 0; warmup < 3; warmup++) list(get(ROOT).param("kid", fixture.kid).param("size", "50"));
        long[] samples = new long[10];
        for (int index = 0; index < samples.length; index++) {
            long started = System.nanoTime();
            JsonNode page = list(get(ROOT).param("kid", fixture.kid).param("size", "50"));
            samples[index] = System.nanoTime() - started;
            assertThat(page.path("totalElements").asLong()).isEqualTo(500);
            assertThat(page.path("items")).hasSize(50);
        }
        long[] sorted = samples.clone(); Arrays.sort(sorted);
        System.out.println("AI review queue 500 rows: samples_ms=" + Arrays.toString(Arrays.stream(samples).mapToDouble(value -> value / 1_000_000.0).toArray())
                + ", p95_ms=" + sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1_000_000.0 + ", target_ms=300");
    }

    private String token(String role) { return jwt.issueAccessToken(actor, List.of(role), true, UUID.randomUUID()); }
    private String bearer(String role) { return "Bearer " + token(role); }
    private JsonNode list(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request.header("Authorization", bearer("TEACHER"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
    private static List<Long> ids(JsonNode page) {
        List<Long> result = new ArrayList<>(); page.path("items").forEach(row -> result.add(row.path("id").asLong())); return result;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTimeConfiguration {
        @Bean @Primary Clock aiReviewTestClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    static final class Fixture {
        private static final AtomicInteger SEQUENCE = new AtomicInteger(600);
        private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
        static final String OPTIONS = "[{\"key\":\"A\",\"text\":\"我\",\"is_correct\":true},{\"key\":\"B\",\"text\":\"你\",\"is_correct\":false}]";
        final JdbcTemplate jdbc;
        final List<Long> questionIds = new ArrayList<>();
        final List<Long> nodeIds = new ArrayList<>();
        final List<Long> actorIds = new ArrayList<>();
        final long primary;
        final String kid;
        Fixture(JdbcTemplate jdbc) { this.jdbc = jdbc; primary = node("NP"); kid = code(primary); }
        long user() {
            long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,email_verified_at) VALUES (?,?,?,?) RETURNING id", Long.class,
                    "review-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người kiểm thử", Timestamp.from(NOW));
            actorIds.add(id); return id;
        }
        long node(String domain) {
            long id = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,hsk_level,domain,name_zh) VALUES (?, 'Nút tự soạn',1,?,'示例') RETURNING id",
                    Long.class, domain + "1-" + SEQUENCE.incrementAndGet(), domain);
            nodeIds.add(id); return id;
        }
        String code(long node) { return jdbc.queryForObject("SELECT code FROM learning.knowledge_points WHERE id=?", String.class, node); }
        long question(String source, String state, int hsk, Instant generated, boolean withPrimary) {
            Long reviewed = source.equals("AI") && state.equals("APPROVED") ? actorIds.get(0) : null;
            long id = jdbc.queryForObject("INSERT INTO learning.questions(qtype,hsk_level,stem,options,correct_key,explanation,source,status,created_at,reviewed_by,review_reason,code) "
                            + "VALUES ('MULTIPLE_CHOICE',?,'他___老师。',CAST(? AS jsonb),'A','Lời giải tự soạn',?,?,?, ?,?,?) RETURNING id", Long.class,
                    hsk, OPTIONS, source, state, Timestamp.from(generated), reviewed, state.equals("REJECTED") ? "Từ chối tự soạn" : null,
                    source.equals("IMPORT") ? "REVIEW-" + UUID.randomUUID() : null);
            questionIds.add(id); if (withPrimary) label(id, primary, "PRIMARY"); return id;
        }
        void label(long question, long node, String role) {
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,?)", question, node, role);
        }
        String status(long id) { return jdbc.queryForObject("SELECT status FROM learning.questions WHERE id=?", String.class, id); }
        long audits(long id, String action) {
            return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE target_id=? AND action=?", Long.class, id, action);
        }
        void cleanup(StringRedisTemplate redis) {
            for (long question : questionIds) {
                for (long actor : actorIds) redis.delete(key(actor, question));
                jdbc.update("DELETE FROM shared.audit_logs WHERE target_id=? AND action IN ('AI_QUESTION_APPROVE','AI_QUESTION_REJECT','QUESTION_UPDATE')", question);
                jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question);
                jdbc.update("DELETE FROM learning.questions WHERE id=?", question);
            }
            for (long node : nodeIds) {
                jdbc.update("DELETE FROM learning.lexemes WHERE knowledge_point_id=?", node);
                jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", node);
            }
            for (long actor : actorIds) jdbc.update("DELETE FROM auth.users WHERE id=?", actor);
        }
        static String key(long user, long question) { return "ai-review:viewed:" + user + ":" + question; }
    }
}
