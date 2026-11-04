package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class LexemeDetailIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger CALLER = new AtomicInteger(10);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private StringRedisTemplate redis;
    @Autowired private Clock clock;
    @Autowired private LexemeDetailService details;
    private String address;

    @BeforeEach
    void isolateCaller() {
        address = "198.18.2." + CALLER.incrementAndGet();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {6})
    void FR007_AT07_everyCharacterHasStrokeSourceEvenWithoutStrokeCount(Integer strokes) throws Exception {
        long id = lexeme("CHAR", "{}", strokes);
        jdbc.update("UPDATE learning.lexemes SET radical='口', sino_viet='THỬ', audio_url=? WHERE id=?",
                "/media/self-authored-character.ogg", id);
        JsonNode result = response(request(Long.toString(id)));
        assertThat(result.path("strokeData").fieldNames()).toIterable().containsExactly("source");
        assertThat(result.path("strokeData").path("source").asText()).isEqualTo("hanzi-writer-data");
        assertThat(result.path("audioUrl").asText()).isEqualTo("/media/self-authored-character.ogg");
        assertThat(result.path("radical").asText()).isEqualTo("口");
        assertThat(result.path("sinoViet").asText()).isEqualTo("THỬ");
        if (strokes == null) assertThat(result.path("strokeCount").isNull()).isTrue();
        else assertThat(result.path("strokeCount").asInt()).isEqualTo(strokes);
        assertThat(result.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "kind", "text", "pinyin",
                "sinoViet", "meaningVi", "hskLevel", "audioUrl", "radical", "strokeCount", "strokeData",
                "examples", "structure", "components", "containedIn", "related");
        for (String array : List.of("examples", "components", "containedIn", "related")) assertThat(result.path(array)).isEmpty();
    }

    @Test
    void FR007_AT07_wordComponentsUseOutgoingContainsAndReferencesExposeOnlyPublicFields() throws Exception {
        long word = lexeme("WORD", "{}", null);
        long first = lexeme("CHAR", "{}", null);
        long second = lexeme("CHAR", "{}", null);
        long unrelated = lexeme("CHAR", "{}", null);
        link(word, second, "CONTAINS");
        link(word, first, "CONTAINS");
        link(word, unrelated, "RELATED");
        JsonNode result = response(request(Long.toString(word)));
        assertThat(ids(result, "components")).containsExactly(first, second);
        assertThat(result.path("strokeData").isNull()).isTrue();
        assertThat(result.path("components")).allSatisfy(reference ->
                assertThat(reference.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "text", "pinyin", "meaningVi"));
        assertThat(ids(response(request(Long.toString(first))), "containedIn")).containsExactly(word);
    }

    @Test
    void FR007_AT07_grammarReturnsExamplesAndStructureWithoutExercisesAnswersOrWholeExtra() throws Exception {
        long id = lexeme("GRAMMAR", """
                {"structure":"A + 试 + B", "examples":[
                  {"cn":"自造示例甲。","pinyin":"zì zào shì lì jiǎ","vi":"Ví dụ tự soạn A.","answerKey":"hidden-answer"},
                  {"cn":null,"pinyin":null,"vi":"Ví dụ tự soạn B."}],
                  "exercises":[{"answerKey":"hidden-answer","stem":"private-exercise"}],
                  "explanation":"private-explanation", "commonMistake":"private-mistake"}
                """, null);
        JsonNode result = response(request(Long.toString(id)));
        assertThat(result.path("strokeData").isNull()).isTrue();
        assertThat(result.path("structure").asText()).isEqualTo("A + 试 + B");
        assertThat(result.path("examples")).hasSize(2);
        assertThat(result.path("examples").get(0).path("cn").asText()).isEqualTo("自造示例甲。");
        assertThat(result.path("examples").get(1).has("cn")).isFalse();
        assertThat(result.toString()).doesNotContain("answerKey", "exercises", "hidden-answer", "private-", "commonMistake", "extra");
        assertThat(result.has("approximate")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"examples\":null}", "{\"examples\":[]}"})
    void FR007_absentExamplesAndStructureReturnEmptyArrayAndNull(String extra) throws Exception {
        long id = lexeme("WORD", extra, null);
        JsonNode result = response(request(Long.toString(id)));
        assertThat(result.path("examples")).isEmpty();
        assertThat(result.path("structure").isNull()).isTrue();
        assertThat(result.path("audioUrl").isNull()).isTrue();
        assertThat(result.path("hskLevel").isNull()).isTrue();
    }

    @Test
    void FR007_AT07_guestAndLearnerReceiveIdenticalJson() throws Exception {
        long id = lexeme("CHAR", "{\"examples\":[{\"cn\":\"自造句。\",\"vi\":\"Câu tự soạn.\"}]}", null);
        String bearer = "Bearer " + jwt.issueAccessToken(980_001, List.of("LEARNER"), false, UUID.randomUUID());
        assertThat(response(request(Long.toString(id)).header("Authorization", bearer)))
                .isEqualTo(response(request(Long.toString(id))));
    }

    @Test
    void FR007_relatedIsBidirectionalDeduplicatedSortedLimitedAndSeparateFromContains() throws Exception {
        long id = lexeme("WORD", "{}", null);
        long containsOnly = lexeme("CHAR", "{}", null);
        link(id, containsOnly, "CONTAINS");
        java.util.ArrayList<Long> expected = new java.util.ArrayList<>();
        for (int index = 0; index < 12; index++) {
            long other = lexeme("WORD", "{}", null);
            expected.add(other);
            link(id, other, "RELATED");
            link(other, id, "SYNONYM");
            link(id, other, "SYNONYM");
        }
        long incomingOnly = lexeme("WORD", "{}", null);
        link(incomingOnly, id, "RELATED");
        JsonNode result = response(request(Long.toString(id)));
        assertThat(ids(result, "related")).containsExactlyElementsOf(expected.subList(0, 10));
        assertThat(ids(result, "related")).doesNotContain(containsOnly);
        assertThat(ids(response(request(Long.toString(incomingOnly))), "related")).containsExactly(id);
        assertThat(ids(result, "components")).containsExactly(containsOnly);
    }

    @Test
    void FR007_containedInIsLimitedButComponentsAreNotLimitedAndDetailAlwaysUsesTwoSelects() throws Exception {
        long id = lexeme("CHAR", "{}", null);
        assertSelects(2, () -> response(request(Long.toString(id))));
        java.util.ArrayList<Long> parents = new java.util.ArrayList<>();
        java.util.ArrayList<Long> children = new java.util.ArrayList<>();
        for (int index = 0; index < 25; index++) {
            long parent = lexeme("WORD", "{}", null);
            long child = lexeme("CHAR", "{}", null);
            parents.add(parent);
            children.add(child);
            link(parent, id, "CONTAINS");
            link(id, child, "CONTAINS");
        }
        assertSelects(2, () -> {
            JsonNode result = response(request(Long.toString(id)));
            assertThat(ids(result, "components")).containsExactlyElementsOf(children);
            assertThat(ids(result, "containedIn")).containsExactlyElementsOf(parents.subList(0, 10));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "9223372036854775808", "+1", "1e2", "１"})
    void FR007_invalidIdsReturn404WithoutDatabaseQueriesAndCountOnce(String id) throws Exception {
        assertSelects(0, () -> mvc.perform(request(id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("LEXEME_NOT_FOUND"))
                .andExpect(jsonPath("$.request_id").isNotEmpty()));
        String hour = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC).format(clock.instant());
        assertThat(redis.opsForValue().get("library:dictionary:guest:" + address + ":" + hour)).isEqualTo("1");
    }

    @Test
    void FR007_nullServiceIdIsNotFoundWithoutDatabaseQueries() throws Exception {
        assertSelects(0, () -> org.assertj.core.api.Assertions.assertThatThrownBy(() -> details.detail(null))
                .isInstanceOf(com.cnhsk.shared.exception.ApiException.class)
                .hasMessage("Không tìm thấy mục từ."));
    }

    @Test
    void FR007_AT07_missingIdReturns404WithOneSelect() throws Exception {
        assertSelects(1, () -> mvc.perform(request(Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("LEXEME_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy mục từ.")));
    }

    private void assertSelects(long expected, CheckedAction action) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            action.run();
            assertThat(statistics.getPrepareStatementCount()).as("Fixed dictionary SELECT count").isEqualTo(expected);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    private long lexeme(String kind, String extra, Integer strokes) {
        return jdbc.queryForObject("""
                INSERT INTO learning.lexemes(kind, text, meaning_vi, extra, stroke_count)
                VALUES (?, ?, 'Mục từ chi tiết tự soạn', cast(? AS jsonb), ?) RETURNING id
                """, Long.class, kind, "详试-" + UUID.randomUUID(), extra, strokes);
    }

    private void link(long parent, long child, String type) {
        jdbc.update("INSERT INTO learning.lexeme_links(parent_id, child_id, link_type) VALUES (?, ?, ?)", parent, child, type);
    }

    private MockHttpServletRequestBuilder request(String id) {
        return get("/api/public/dictionary/lexemes/{id}", id).with(req -> { req.setRemoteAddr(address); return req; });
    }

    private JsonNode response(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private List<Long> ids(JsonNode result, String bucket) {
        return result.path(bucket).findValues("id").stream().map(JsonNode::asLong).toList();
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
