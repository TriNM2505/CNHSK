package com.cnhsk.learning.knowledge;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionSource;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@Import(QuestionAdminServiceIntegrationTest.SqlConfiguration.class)
class QuestionAdminControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String ROOT = "/api/admin/questions";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private QuestionRepository questions;
    @Autowired private KnowledgePointRepository points;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"CONTENT_EDITOR", "TEACHER"})
    void FR001_AT01_allowedRolesReadBothEndpointsWithBearerAndCookie(String role) throws Exception {
        Question question = question("T11_ROLE", QuestionType.MULTIPLE_CHOICE, 1, QuestionStatus.APPROVED);
        for (String path : List.of(ROOT, ROOT + "/" + question.getId())) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token(role))).andExpect(status().isOk());
            mvc.perform(get(path).cookie(new Cookie("access_token", token(role)))).andExpect(status().isOk());
        }
        mvc.perform(post(ROOT).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "MODERATOR", "ACCOUNTANT", "ADMIN"})
    void FR001_AT01_everyOtherRoleIsForbiddenBeforeValidationOrLookup(String role) throws Exception {
        for (String path : List.of(ROOT, ROOT + "/abc")) {
            mvc.perform(get(path).param("hskLevel", "0").header("Authorization", "Bearer " + token(role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.request_id").isNotEmpty());
            mvc.perform(get(path).cookie(new Cookie("access_token", token(role))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test
    void FR001_AT01_noTokenIs401AndAnExplicitAllowedRoleWorksInAMultiRoleToken() throws Exception {
        for (String path : List.of(ROOT, ROOT + "/abc")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        }
        mvc.perform(get(ROOT).header("Authorization", "Bearer " + jwt.issueAccessToken(901L,
                List.of("ADMIN", "TEACHER"), false, UUID.randomUUID()))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "9223372036854775808", "-9223372036854775809", "9223372036854775807"})
    void FR019_AT19_invalidNonPositiveOverflowingAndMissingIdsShare404(String id) throws Exception {
        mvc.perform(get(ROOT + "/{id}", id).header("Authorization", bearer()).header("X-Request-Id", "t11-not-found"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("QUESTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy câu hỏi."))
                .andExpect(jsonPath("$.request_id").value("t11-not-found"));
    }

    @ParameterizedTest
    @MethodSource("invalidFilters")
    void FR017_AT17_invalidFiltersReturn422AndFieldDetails(String field, String value) throws Exception {
        mvc.perform(get(ROOT).param(field, value).header("Authorization", bearer()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value(field)).andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    static Stream<Arguments> invalidFilters() {
        return Stream.of(Arguments.of("hskLevel", "0"), Arguments.of("hskLevel", "10"), Arguments.of("hskLevel", "abc"),
                Arguments.of("hskLevel", "1.5"), Arguments.of("hskLevel", ""), Arguments.of("page", "-1"),
                Arguments.of("page", "abc"), Arguments.of("page", "2147483648"), Arguments.of("page", ""),
                Arguments.of("size", "0"), Arguments.of("size", "101"), Arguments.of("size", "abc"), Arguments.of("size", ""),
                Arguments.of("qtype", "BAD"), Arguments.of("qtype", "multiple_choice"), Arguments.of("qtype", ""),
                Arguments.of("status", "BAD"), Arguments.of("status", "approved"), Arguments.of("status", ""),
                Arguments.of("q", "a"), Arguments.of("q", ""), Arguments.of("q", "a".repeat(101)));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9})
    void FR017_AT17_acceptsAllContractLevelsIncludingBeyondTheFrontendScope(int level) throws Exception {
        mvc.perform(get(ROOT).param("hskLevel", Integer.toString(level)).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void FR017_AT17_filtersEveryFieldAndBothLabelRolesWithIntersectionAndDescendingIds() throws Exception {
        KnowledgePoint target = node("NP1-901"); KnowledgePoint other = node("TV1-901");
        Question secondary = question("T11_FILTER_SECONDARY", QuestionType.MULTIPLE_CHOICE, 2, QuestionStatus.APPROVED);
        label(secondary, other, KnowledgeLabelRole.PRIMARY); label(secondary, target, KnowledgeLabelRole.SECONDARY);
        Question primary = question("T11_FILTER_PRIMARY", QuestionType.MULTIPLE_CHOICE, 2, QuestionStatus.APPROVED);
        label(primary, target, KnowledgeLabelRole.PRIMARY); label(primary, other, KnowledgeLabelRole.SECONDARY);
        label(question("T11_FILTER_LEVEL", QuestionType.MULTIPLE_CHOICE, 1, QuestionStatus.APPROVED), target, KnowledgeLabelRole.PRIMARY);
        label(question("T11_FILTER_TYPE", QuestionType.TRUE_FALSE, 2, QuestionStatus.APPROVED), target, KnowledgeLabelRole.PRIMARY);
        label(question("T11_FILTER_STATUS", QuestionType.MULTIPLE_CHOICE, 2, QuestionStatus.ARCHIVED), target, KnowledgeLabelRole.PRIMARY);
        assertThat(response("?q=T11_FILTER_&hskLevel=2").path("items")).hasSize(4);
        assertThat(response("?q=T11_FILTER_&qtype=MULTIPLE_CHOICE").path("items")).hasSize(4);
        assertThat(response("?q=T11_FILTER_&status=APPROVED").path("items")).hasSize(4);
        assertThat(response("?q=T11_FILTER_&kid=NP1-901").path("items")).hasSize(5);
        JsonNode combined = response("?q=T11_FILTER_&hskLevel=2&qtype=MULTIPLE_CHOICE&status=APPROVED&kid=np1-901");
        assertThat(combined.path("items")).extracting(row -> row.path("id").asLong()).containsExactly(primary.getId(), secondary.getId());
        assertThat(combined.path("totalElements").asLong()).isEqualTo(2);
        assertThat(response("?kid=NP1-999").path("items")).isEmpty();
    }

    @Test
    void FR017_AT17_paginatesAtTheDatabaseAndPreservesTotalOnPastEndPages() throws Exception {
        question("UNRELATED_PAGE", QuestionType.MULTIPLE_CHOICE, 1, QuestionStatus.APPROVED);
        long newest = 0;
        for (int index = 0; index < 12; index++) newest = question("T11_PAGE_" + index, QuestionType.MULTIPLE_CHOICE, 1, QuestionStatus.APPROVED).getId();
        JsonNode first = response("?q=T11_PAGE_"); JsonNode second = response("?q=T11_PAGE_&page=1");
        assertThat(first.path("items")).hasSize(10); assertThat(first.path("items").get(0).path("id").asLong()).isEqualTo(newest);
        assertThat(first.path("page").asInt()).isZero(); assertThat(first.path("size").asInt()).isEqualTo(10);
        assertThat(second.path("items")).hasSize(2); assertThat(second.path("page").asInt()).isEqualTo(1);
        assertThat(second.path("totalElements").asLong()).isEqualTo(12);
        assertThat(response("?q=T11_PAGE_&size=1&page=1").path("items")).hasSize(1);
        assertThat(response("?q=T11_PAGE_&size=100").path("items")).hasSize(12);
        JsonNode past = response("?q=T11_PAGE_&page=2147483647&size=100");
        assertThat(past.path("items")).isEmpty(); assertThat(past.path("totalElements").asLong()).isEqualTo(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%x", "_x", "!x", "%_!"})
    void FR017_AT17_searchEscapesPercentUnderscoreAndTheEscapeCharacter(String search) throws Exception {
        Question literal = question("T11_LITERAL", QuestionType.MULTIPLE_CHOICE, 1, QuestionStatus.APPROVED);
        literal.setStem("Tìm đúng chuỗi " + search); questions.saveAndFlush(literal);
        Question decoy = question("T11_DECOY", QuestionType.MULTIPLE_CHOICE, 1, QuestionStatus.APPROVED);
        decoy.setStem("Tìm đúng chuỗi aax"); questions.saveAndFlush(decoy);
        JsonNode result = mapper.readTree(mvc.perform(get(ROOT).param("q", search).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(result.path("items")).extracting(row -> row.path("id").asLong()).containsExactly(literal.getId());
    }

    @Test
    void FR017_AT17_searchesCodeAndStemIgnoringCaseAndTreatsInjectionAsText() throws Exception {
        Question question = question("T11_AlphaCode", QuestionType.MULTIPLE_CHOICE, 1, QuestionStatus.APPROVED);
        question.setStem("Xin CHÀO trong câu tự soạn"); questions.saveAndFlush(question);
        for (String search : List.of("alphacode", "chào")) {
            mvc.perform(get(ROOT).param("q", search).header("Authorization", bearer())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].id").value(question.getId()));
        }
        mvc.perform(get(ROOT).param("q", "%' OR 1=1 --").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(ROOT).param("q", "x".repeat(100)).header("Authorization", bearer())).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(QuestionType.class)
    void FR019_AT19_detailReturnsEveryContractFieldAndCamelCaseOptionsForAllTypes(QuestionType type) throws Exception {
        Question question = question("T11_DETAIL", type, 3, QuestionStatus.REJECTED);
        question.setAudioUrl("https://example.test/self-authored.mp3"); question.setImageUrl("https://example.test/self-authored.png");
        question.setExplanation("Lời giải tự soạn"); questions.saveAndFlush(question);
        KnowledgePoint primary = node("NP1-901"); KnowledgePoint secondary = node("TV1-901");
        label(question, primary, KnowledgeLabelRole.PRIMARY); label(question, secondary, KnowledgeLabelRole.SECONDARY);
        JsonNode detail = response("/" + question.getId());
        assertThat(detail.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "code", "qtype", "hskLevel", "difficulty", "stem",
                "options", "correctKey", "explanation", "audioUrl", "imageUrl", "source", "status", "reviewReason", "labels", "used");
        assertThat(detail.path("qtype").asText()).isEqualTo(type.name()); assertThat(detail.path("explanation").asText()).isEqualTo(question.getExplanation());
        assertThat(detail.path("correctKey").isNull() ? null : detail.path("correctKey").asText()).isEqualTo(question.getCorrectKey());
        assertThat(detail.path("labels")).hasSize(2); assertThat(detail.path("labels").get(0).path("nameVi").asText()).isEqualTo(primary.getTitle());
        for (JsonNode option : detail.path("options")) {
            assertThat(option.fieldNames()).toIterable().containsExactlyInAnyOrder("key", "text", "isCorrect", "errorKid", "errorNote");
            assertThat(option.path("isCorrect").isBoolean()).isTrue();
        }
        if (!question.getOptions().isEmpty()) assertThat(detail.path("options").get(0).path("isCorrect").asBoolean()).isEqualTo(question.getOptions().get(0).isCorrect());
        assertThat(detail.toString()).doesNotContain("is_correct", "error_kid", "error_note");
    }

    @Test
    void FR018_AT18_summaryTruncatesUnicodeAndKeepsNullableAndUnlabeledFieldsWithoutAnswers() throws Exception {
        Question question = question("T11_NULL", QuestionType.MULTIPLE_CHOICE, 1, QuestionStatus.PENDING_REVIEW);
        question.setCode(null); question.setSource(QuestionSource.AI); question.setStem("🙂".repeat(121)); questions.saveAndFlush(question);
        JsonNode row = response("").path("items").get(0);
        String summary = row.path("stemSummary").asText(); assertThat(summary.codePointCount(0, summary.length())).isEqualTo(120);
        assertThat(row.path("code").isNull()).isTrue(); assertThat(row.path("primaryKid").isNull()).isTrue(); assertThat(row.path("secondaryKids")).isEmpty();
        assertThat(row.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "code", "qtype", "hskLevel", "difficulty", "stemSummary", "source", "status", "primaryKid", "secondaryKids", "used");
        assertThat(row.toString()).doesNotContain("options", "isCorrect", "correctKey", "explanation");
        JsonNode detail = response("/" + question.getId());
        assertThat(detail.path("labels")).isEmpty(); assertThat(detail.path("explanation").isNull()).isTrue();
    }

    @Test
    void NFR_P02_fiveThousandSelfAuthoredQuestionsAreListedByKidWithinOneHundredMillisecondsP95() throws Exception {
        KnowledgePoint point = node("NP1-950");
        jdbc.update("""
                INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,source,status,difficulty)
                SELECT 'T11_PERF_' || n, 'MULTIPLE_CHOICE', 1, 'Câu hỏi hiệu năng tự soạn', CAST(? AS jsonb), 'IMPORT', 'APPROVED', 2
                FROM generate_series(1,5000) n
                """, "[{\"key\":\"A\",\"text\":\"我\",\"is_correct\":true},{\"key\":\"B\",\"text\":\"你\",\"is_correct\":false}]");
        jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) SELECT id,?,'PRIMARY' FROM learning.questions WHERE code LIKE ?",
                point.getId(), "T11_PERF_%");
        String token = bearer();
        for (int index = 0; index < 5; index++) mvc.perform(get(ROOT).param("kid", point.getCode()).header("Authorization", token)).andExpect(status().isOk());
        long[] samples = new long[20];
        for (int index = 0; index < samples.length; index++) {
            long started = System.nanoTime();
            mvc.perform(get(ROOT).param("kid", point.getCode()).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5000)).andExpect(jsonPath("$.items.length()").value(10));
            samples[index] = (System.nanoTime() - started) / 1_000_000;
        }
        long[] sorted = samples.clone(); Arrays.sort(sorted); long p95 = sorted[(int) Math.ceil(samples.length * .95) - 1];
        System.out.println("T11_QUESTION_LIST_BENCHMARK rows=5000 samples_ms=" + Arrays.toString(samples) + " p95_ms=" + p95);
        assertThat(p95).isLessThan(100);
    }

    private Question question(String code, QuestionType type, int level, QuestionStatus status) {
        Question question = new Question(code, type, (short) level, type == QuestionType.FILL_BLANK ? "我___家。" : "Nội dung tự soạn");
        question.setStatus(status); if (status == QuestionStatus.REJECTED) question.setReviewReason("Lý do tự soạn");
        if (type == QuestionType.FILL_BLANK) question.setCorrectKey("在");
        else if (type == QuestionType.SENTENCE_ORDER) {
            question.setOptions(List.of(new QuestionOption("A", "我", false, null, null), new QuestionOption("B", "在", false, null, null), new QuestionOption("C", "家", false, null, null)));
            question.setCorrectKey("ABC");
        } else if (type != QuestionType.ESSAY) {
            question.setOptions(List.of(new QuestionOption("A", type == QuestionType.TRUE_FALSE ? "Đúng" : "我", true, null, null),
                    new QuestionOption("B", type == QuestionType.TRUE_FALSE ? "Sai" : "你", false, "TV1-901", "Lỗi tự soạn")));
        }
        return questions.saveAndFlush(question);
    }

    private KnowledgePoint node(String kid) {
        return points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn " + kid, KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例", (short) 1));
    }
    private void label(Question question, KnowledgePoint point, KnowledgeLabelRole role) {
        labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), point.getId(), role));
    }
    private JsonNode response(String suffix) throws Exception {
        return mapper.readTree(mvc.perform(get(ROOT + suffix).header("Authorization", bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }
    private String bearer() { return "Bearer " + token("TEACHER"); }
    private String token(String role) { return jwt.issueAccessToken(901L, List.of(role), false, UUID.randomUUID()); }
}
