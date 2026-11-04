package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
class RadicalSearchIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger CALLER = new AtomicInteger(10);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JwtService jwt;
    @Autowired private Clock clock;
    private String address;

    @BeforeEach
    void isolateCaller() {
        address = "198.18.1." + CALLER.incrementAndGet();
        // Import integration tests commit CHAR fixtures; isolate filters within this rolled-back transaction.
        jdbc.update("UPDATE learning.lexemes SET radical = NULL, stroke_count = NULL WHERE kind = 'CHAR'");
    }

    @Test
    void FR006_AT06_radicalsCountCharactersOnlyAndSortByCountThenRadicalWithoutPagination() throws Exception {
        lexeme("CHAR", "口", 6, 0);
        lexeme("CHAR", "口", 5, 0);
        lexeme("CHAR", "木", 4, 0);
        lexeme("CHAR", "土", 3, 0);
        lexeme("WORD", "水", 4, 0);
        lexeme("CHAR", null, null, 0);
        lexeme("CHAR", "", null, 0);
        JsonNode result = response(request("/radicals").param("page", "999").param("size", "1"));
        assertThat(result).hasSize(3);
        assertThat(result).extracting(value -> value.path("radical").asText()).containsExactly("口", "土", "木");
        assertThat(result).extracting(value -> value.path("charCount").asInt()).containsExactly(2, 1, 1);
    }

    @Test
    void FR006_AT06_exactSearchUsesBothFiltersAndStrokeFrequencyIdOrder() throws Exception {
        long first = lexeme("CHAR", "口", 6, 20);
        long second = lexeme("CHAR", "口", 6, 20);
        long third = lexeme("CHAR", "口", 6, 10);
        lexeme("CHAR", "口", 5, 100);
        lexeme("CHAR", "木", 6, 200);
        lexeme("WORD", "口", 6, 300);
        JsonNode result = response(request("/by-radical").param("radical", " 口 ").param("strokes", "6"));
        assertThat(ids(result)).containsExactly(first, second, third);
        assertThat(result.path("total").asInt()).isEqualTo(3);
        assertThat(result.path("items")).allSatisfy(item -> {
            assertThat(item.path("kind").asText()).isEqualTo("CHAR");
            assertThat(item.path("approximate").asBoolean()).isFalse();
        });
        JsonNode page = response(request("/by-radical").param("radical", "口").param("strokes", "6")
                .param("size", "2").param("page", "1"));
        assertThat(ids(page)).containsExactly(third);
        assertThat(page.path("total").asInt()).isEqualTo(3);
    }

    @Test
    void FR006_AT06_fallbackRetainsRadicalAndOrdersAdjacentStrokesBeforeFrequency() throws Exception {
        long five = lexeme("CHAR", "口", 5, 1);
        long seven = lexeme("CHAR", "口", 7, 100);
        lexeme("CHAR", "木", 6, 1000);
        lexeme("CHAR", "木", 5, 1000);
        lexeme("WORD", "口", 6, 1000);
        JsonNode result = response(request("/by-radical").param("radical", "口").param("strokes", "6"));
        assertThat(ids(result)).containsExactly(five, seven);
        assertThat(result.path("items")).allSatisfy(item -> assertThat(item.path("approximate").asBoolean()).isTrue());
        assertThat(result.path("total").asInt()).isEqualTo(2);
        assertThat(response(request("/by-radical").param("radical", "口").param("strokes", "6")
                .param("page", "1").param("size", "1")).path("items")).hasSize(1);
    }

    @Test
    void FR006_pageBeyondExactMatchesDoesNotTriggerApproximateSearch() throws Exception {
        lexeme("CHAR", "口", 6, 0);
        lexeme("CHAR", "口", 5, 0);
        JsonNode result = response(request("/by-radical").param("radical", "口").param("strokes", "6")
                .param("page", "2147483647").param("size", "50"));
        assertThat(result.path("items")).isEmpty();
        assertThat(result.path("total").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 64})
    void FR006_strokesOnlyFallbackRespectsOneTo64Boundary(int strokes) throws Exception {
        long expected = lexeme("CHAR", null, strokes == 1 ? 2 : 63, 0);
        lexeme("CHAR", null, strokes == 1 ? 0 : 65, 0);
        JsonNode result = response(request("/by-radical").param("strokes", Integer.toString(strokes)));
        assertThat(ids(result)).containsExactly(expected);
        assertThat(result.path("items").get(0).path("approximate").asBoolean()).isTrue();
    }

    @Test
    void FR006_radicalOnlySearchSortsMissingStrokeCountLastAndUsesDefault20Maximum50() throws Exception {
        long last = lexeme("CHAR", "木", null, 1000);
        for (int index = 0; index < 54; index++) lexeme("CHAR", "木", 4, index);
        assertThat(response(request("/by-radical").param("radical", "木")).path("items")).hasSize(20);
        JsonNode result = response(request("/by-radical").param("radical", "木").param("size", "50").param("page", "1"));
        assertThat(result.path("items")).hasSize(5);
        assertThat(result.path("total").asInt()).isEqualTo(55);
        assertThat(ids(result).get(4)).isEqualTo(last);
    }

    @Test
    void FR006_AT06_invalidFiltersAndPaginationReturn422AndEachRequestCountsOnce() throws Exception {
        List<Map<String, String>> invalid = List.of(Map.of(), Map.of("radical", "  "), Map.of("strokes", "0"),
                Map.of("strokes", "65"), Map.of("strokes", "abc"), Map.of("strokes", "1.5"), Map.of("strokes", ""),
                Map.of("strokes", "2147483648"), Map.of("radical", "x".repeat(11)), Map.of("radical", "x\u0000"),
                Map.of("radical", "口", "page", "-1"), Map.of("radical", "口", "size", "0"),
                Map.of("radical", "口", "size", "51"), Map.of("radical", "口", "page", "bad"),
                Map.of("radical", "口", "size", "1.5"), Map.of("radical", "口", "size", ""));
        for (Map<String, String> params : invalid) {
            var req = request("/by-radical");
            params.forEach(req::param);
            mvc.perform(req).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.request_id").isNotEmpty()).andExpect(jsonPath("$.details[0].field").isNotEmpty());
        }
        assertThat(redis.opsForValue().get(key(null))).isEqualTo(Integer.toString(invalid.size()));
    }

    @Test
    void FR006_radicalValuesAreBoundLiterallyIncludingQuotesAndWildcards() throws Exception {
        long expected = lexeme("CHAR", "O'%_", 4, 0);
        lexeme("CHAR", "Oxxx", 4, 0);
        assertThat(ids(response(request("/by-radical").param("radical", "O'%_")))).containsExactly(expected);
        JsonNode empty = response(request("/by-radical").param("radical", "' OR 1=1"));
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("total").asInt()).isZero();
        assertThat(empty.path("hint").isNull()).isTrue();
    }

    @Test
    void FR002_guestQuotaIsSharedByAllFourEndpointsIncludingInvalidRequests() throws Exception {
        long id = lexeme("CHAR", "口", 6, 0);
        redis.opsForValue().set(key(null), "56", Duration.ofHours(1));
        response(request("/search").param("type", "HANZI").param("q", "自造未找到"));
        response(request("/radicals"));
        mvc.perform(request("/by-radical")).andExpect(status().isUnprocessableEntity());
        response(request("/lexemes/" + id));
        assertThat(redis.opsForValue().get(key(null))).isEqualTo("60");
        for (String path : List.of("/radicals", "/by-radical", "/lexemes/bad")) {
            mvc.perform(request(path).header("X-Forwarded-For", UUID.randomUUID().toString()))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.loginSuggested").value(true))
                    .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
        }
        assertThat(redis.opsForValue().get(key(null))).isEqualTo("63");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT"})
    void FR002_everyAuthenticatedRoleShares600PerUserAcrossNewEndpoints(String role) throws Exception {
        long userId = 970_000 + CALLER.get();
        String bearer = "Bearer " + jwt.issueAccessToken(userId, List.of(role), false, UUID.randomUUID());
        redis.opsForValue().set(key(userId), "597", Duration.ofHours(1));
        response(request("/radicals").header("Authorization", bearer));
        response(request("/by-radical").param("strokes", "6").header("Authorization", bearer));
        mvc.perform(request("/lexemes/bad").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(request("/radicals").header("Authorization", bearer)
                        .with(req -> { req.setRemoteAddr("192.0.2.1"); return req; }))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.loginSuggested").doesNotExist());
        assertThat(redis.opsForValue().get(key(userId))).isEqualTo("601");
        assertThat(redis.opsForValue().get(key(null))).isNull();
    }

    private long lexeme(String kind, String radical, Integer strokes, int frequency) {
        return jdbc.queryForObject("""
                INSERT INTO learning.lexemes(kind, text, meaning_vi, radical, stroke_count, frequency)
                VALUES (?, ?, 'Mục từ bộ thủ tự soạn', ?, ?, ?) RETURNING id
                """, Long.class, kind, "部试-" + UUID.randomUUID(), radical, strokes, frequency);
    }

    private MockHttpServletRequestBuilder request(String path) {
        return get("/api/public/dictionary" + path).with(req -> { req.setRemoteAddr(address); return req; });
    }

    private JsonNode response(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private List<Long> ids(JsonNode result) {
        return result.path("items").findValues("id").stream().map(JsonNode::asLong).toList();
    }

    private String key(Long userId) {
        String hour = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC).format(clock.instant());
        return "library:dictionary:" + (userId == null ? "guest:" + address : "user:" + userId) + ":" + hour;
    }
}
