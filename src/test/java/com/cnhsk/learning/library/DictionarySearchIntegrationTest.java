package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.content.text.TextNormalizer;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class DictionarySearchIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger ADDRESS = new AtomicInteger(20);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private LexemeRepository lexemes;
    @Autowired private JwtService jwt;
    @Autowired private StringRedisTemplate redis;
    @Autowired private Clock clock;
    @Autowired private JdbcTemplate jdbc;
    private String address;

    @BeforeEach
    void isolateCaller() {
        address = "198.51.100." + ADDRESS.incrementAndGet();
    }

    @Test
    void FR001_AT01_publicLookupDoesNotOpenPrivateOrWriteEndpoints() throws Exception {
        response(request("HANZI", "自造不存在"));
        mvc.perform(post("/api/notes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notes").header("Authorization", bearer(900_001, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/public/dictionary/search").header("Authorization", bearer(900_001, "ADMIN")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "ADMIN", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT"})
    void FR001_FR002_AT01_AT02_everyAuthenticatedRoleGets600PerUserWithBearerOrCookie(String role) throws Exception {
        long userId = 910_000 + ADDRESS.get();
        redis.opsForValue().set(userKey(userId), "599", Duration.ofHours(1));
        String token = jwt.issueAccessToken(userId, List.of(role), false, UUID.randomUUID());
        response(request("HANZI", "自造不存在").cookie(new Cookie("access_token", token)));
        mvc.perform(request("HANZI", "自造不存在").header("Authorization", "Bearer " + token)
                        .with(req -> { req.setRemoteAddr("192.0.2.99"); return req; }))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(jsonPath("$.loginSuggested").doesNotExist());
        assertThat(redis.opsForValue().get(userKey(userId))).isEqualTo("601");
    }

    @Test
    void FR002_AT02_guest61IsBlockedAndForwardedForCannotChangeTheCounter() throws Exception {
        for (int index = 0; index < 60; index++) response(request("HANZI", "自造不存在")
                .header("X-Forwarded-For", "192.0.2." + index));
        mvc.perform(request("HANZI", "自造不存在").header("X-Forwarded-For", "203.0.113.1"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty()).andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(jsonPath("$.loginSuggested").value(true));
        assertThat(redis.opsForValue().get(guestKey())).isEqualTo("61");
    }

    @Test
    void FR002_invalidRequestsAreCountedExactlyOnceBeforeParameterValidation() throws Exception {
        for (int index = 0; index < 60; index++) mvc.perform(request("HANZI", "").param("page", "bad"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(request("HANZI", "").param("page", "bad"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.loginSuggested").value(true));
        assertThat(redis.opsForValue().get(guestKey())).isEqualTo("61");
    }

    @Test
    void FR002_AT02_lookupDoesNotConsumeFreeUsage() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        String registration = mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                "password", password, "confirmPassword", password, "displayName", "Người học thử", "acceptTerms", true));
        long userId = mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray())
                .path("userId").asLong();
        String before = jdbc.queryForObject("SELECT free_usage::text FROM auth.users WHERE id=?", String.class, userId);
        response(request("HANZI", "自造不存在").header("Authorization", bearer(userId, "LEARNER")));
        String after = jdbc.queryForObject("SELECT free_usage::text FROM auth.users WHERE id=?", String.class, userId);
        assertThat(after).isEqualTo(before);
    }

    @Test
    void FR003_AT03_exactMatchesPreferWordThenGrammarThenCharacterAndExposeOnlySummaryFields() throws Exception {
        lexeme(LexemeKind.CHAR, "好", "hǎo", "tốt", 900);
        lexeme(LexemeKind.GRAMMAR, "好", "hǎo", "ngữ pháp thử", 1000);
        lexeme(LexemeKind.WORD, "好", "hǎo", "từ thử", 1);
        JsonNode result = response(request("HANZI", "好"));
        assertThat(result.path("items")).extracting(item -> item.path("kind").asText())
                .containsExactly("WORD", "GRAMMAR", "CHAR");
        assertThat(result.path("total").asInt()).isEqualTo(3);
        assertThat(result.path("items").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "kind", "text", "pinyin", "sinoViet", "meaningVi", "hskLevel", "approximate");
        assertThat(result.toString()).doesNotContain("extra", "answerKey", "frequency", "knowledgePointId");
        assertThat(result.path("items").get(0).path("approximate").asBoolean()).isFalse();
        lexeme(LexemeKind.WORD, "你好", "nǐ hǎo", "xin chào thử", 1);
        assertThat(response(request("HANZI", "你好")).path("items").get(0).path("text").asText()).isEqualTo("你好");
    }

    @Test
    void FR003_AT03_fallbackKeepsFirstOccurrenceOrderAndDeduplicatesRenRen() throws Exception {
        for (String character : List.of("你", "们", "好", "人")) lexeme(LexemeKind.CHAR, character, "rén", "chữ thử", 1);
        assertThat(response(request("HANZI", "你们好")).path("items")).extracting(item -> item.path("text").asText())
                .containsExactly("你", "们", "好");
        JsonNode repeated = response(request("HANZI", "人人"));
        assertThat(repeated.path("items")).hasSize(1);
        assertThat(repeated.path("items").get(0).path("text").asText()).isEqualTo("人");
        assertThat(repeated.path("total").asInt()).isEqualTo(1);
        assertThat(response(request("HANZI", "好你好")).path("items")).extracting(item -> item.path("text").asText())
                .containsExactly("好", "你");
    }

    @Test
    void FR003_handlesSupplementaryHanziAndMissingCharactersWithoutLeakingOtherKinds() throws Exception {
        String supplementary = new String(Character.toChars(0x20000));
        Lexeme value = lexeme(LexemeKind.CHAR, supplementary, null, "chữ tổng hợp thử", 1);
        value.setHskLevel(null);
        lexemes.saveAndFlush(value);
        lexeme(LexemeKind.WORD, "人", "rén", "từ thử", 1);
        JsonNode result = response(request("HANZI", supplementary + "无"));
        assertThat(result.path("items")).extracting(item -> item.path("text").asText()).contains(supplementary);
        assertThat(result.path("items").get(0).path("hskLevel").isNull()).isTrue();
        assertThat(result.path("items").get(0).path("pinyin").isNull()).isTrue();
        assertThat(response(request("HANZI", "not-cjk")).path("items")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HANZI", "PINYIN", "MEANING"})
    void FR003_FR004_FR005_longQueriesReturnTranslationHintWithNoMatches(String type) throws Exception {
        String query = (type.equals("HANZI") ? "人" : "x").repeat(type.equals("HANZI") ? 11 : 51);
        JsonNode result = response(request(type, query));
        assertThat(result.path("items")).isEmpty();
        assertThat(result.path("total").asInt()).isZero();
        assertThat(result.path("hint").asText()).isEqualTo("USE_TRANSLATE");
    }

    @Test
    void FR004_AT04_allThreePinyinFormsReturnTheSameCandidates() throws Exception {
        lexeme(LexemeKind.WORD, "你好", "nǐ hǎo", "xin chào thử", 1);
        lexeme(LexemeKind.WORD, "拟好", "nǐ hào", "từ tự soạn thử", 100);
        List<Long> expected = response(request("PINYIN", "ni hao")).path("items").findValues("id").stream()
                .map(JsonNode::asLong).sorted().toList();
        for (String q : List.of("nǐ hǎo", "ni3hao3", "NI3-HAO3")) {
            JsonNode result = response(request("PINYIN", q));
            assertThat(result.path("items").findValues("id").stream().map(JsonNode::asLong).sorted().toList()).isEqualTo(expected);
            assertThat(result.path("items").get(0).path("text").asText()).isEqualTo("你好");
            assertThat(result.path("items").get(0).path("pinyin").asText()).isEqualTo("nǐ hǎo");
        }
    }

    @Test
    void FR004_AT04_explicitToneWinsBeforeFrequencyAndXianIncludesBothSegmentations() throws Exception {
        lexeme(LexemeKind.CHAR, "妈", "mā", "mẹ thử", 1);
        lexeme(LexemeKind.CHAR, "马", "mǎ", "ngựa thử", 1000);
        assertThat(response(request("PINYIN", "mā")).path("items").get(0).path("text").asText()).isEqualTo("妈");
        assertThat(response(request("PINYIN", "ma")).path("items").get(0).path("text").asText()).isEqualTo("马");
        lexeme(LexemeKind.CHAR, "先", "xiān", "trước thử", 2);
        Lexeme city = lexeme(LexemeKind.WORD, "西安", "xī ān", "địa danh thử", 1);
        city.setPinyinNorm("xi an");
        lexemes.saveAndFlush(city);
        assertThat(response(request("PINYIN", "xian")).path("items")).extracting(item -> item.path("text").asText())
                .contains("先", "西安");
    }

    @Test
    void FR005_AT05_vietnameseDiacriticsShareGroupsAndRankExactPrefixThenSubstring() throws Exception {
        lexeme(LexemeKind.WORD, "辞", "cí", "chào", 1);
        lexeme(LexemeKind.WORD, "招", "zhāo", "chào buổi sáng", 100);
        lexeme(LexemeKind.WORD, "召", "zhào", "xin chào", 1000);
        for (String q : List.of("chao", "chào")) assertThat(response(request("MEANING", q)).path("items"))
                .extracting(item -> item.path("meaningVi").asText()).containsExactly("chào", "chào buổi sáng", "xin chào");
    }

    @Test
    void FR005_escapesLikeWildcardsAndBindsQuotesWithoutChangingTheLiteralQuery() throws Exception {
        lexeme(LexemeKind.WORD, "值", "zhí", "100%_ giá trị!", 1);
        lexeme(LexemeKind.WORD, "数", "shù", "100XX giá trị!", 100);
        assertThat(response(request("MEANING", "100%_")).path("items")).hasSize(1);
        assertThat(response(request("MEANING", "100%_")).path("items").get(0).path("text").asText()).isEqualTo("值");
        lexeme(LexemeKind.WORD, "名", "míng", "O'Brien", 1);
        assertThat(response(request("MEANING", "O'Brien")).path("items")).hasSize(1);
        assertThat(response(request("MEANING", "' OR 1=1 --")).path("items")).isEmpty();
        assertThat(response(request("MEANING", "giá trị!")).path("items")).hasSize(2);
    }

    @Test
    void FR003_FR004_databasePaginationHasDefault20Maximum50AndStableTotals() throws Exception {
        for (int index = 0; index < 55; index++) lexeme(LexemeKind.WORD, "分页试" + index, "fēn yè", "mục từ phân trang thử", index);
        assertThat(response(request("PINYIN", "fenye")).path("items")).hasSize(20);
        JsonNode second = response(request("PINYIN", "fenye").param("page", "1").param("size", "50"));
        assertThat(second.path("items")).hasSize(5);
        assertThat(second.path("total").asInt()).isEqualTo(55);
        assertThat(second.path("items").get(0).path("text").asText()).isEqualTo("分页试4");
        JsonNode pastEnd = response(request("PINYIN", "fenye").param("page", "2147483647").param("size", "50"));
        assertThat(pastEnd.path("items")).isEmpty();
        assertThat(pastEnd.path("total").asInt()).isEqualTo(55);
    }

    @Test
    void FR003_FR005_missingOrInvalidParametersAlwaysReturn422WithTheStandardEnvelope() throws Exception {
        List<Map<String, String>> invalid = List.of(Map.of(), Map.of("type", "HANZI"), Map.of("q", "人"),
                Map.of("type", "OTHER", "q", "人"), Map.of("type", "HANZI", "q", "  "),
                Map.of("type", "MEANING", "q", "a"), Map.of("type", "HANZI", "q", "人", "page", "-1"),
                Map.of("type", "HANZI", "q", "人", "size", "0"), Map.of("type", "HANZI", "q", "人", "size", "51"),
                Map.of("type", "HANZI", "q", "人", "page", "abc"), Map.of("type", "HANZI", "q", "人", "size", "1.5"),
                Map.of("type", "HANZI", "q", "人", "page", "2147483648"), Map.of("type", "HANZI", "q", "人", "size", ""),
                Map.of("type", "HANZI", "q", "\u0000"), Map.of("type", "MEANING", "q", "ch\u0001ao"));
        for (Map<String, String> params : invalid) {
            var req = get("/api/public/dictionary/search").with(request -> { request.setRemoteAddr(address); return request; });
            params.forEach(req::param);
            mvc.perform(req).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").isNotEmpty()).andExpect(jsonPath("$.request_id").isNotEmpty())
                    .andExpect(jsonPath("$.details[0].field").isNotEmpty());
        }
        assertThat(redis.opsForValue().get(guestKey())).isEqualTo(Integer.toString(invalid.size()));
    }

    private Lexeme lexeme(LexemeKind kind, String text, String pinyin, String meaning, int frequency) {
        Lexeme value = lexemes.findByKindAndText(kind, text).orElseGet(() -> new Lexeme(kind, text, meaning));
        value.setPinyin(pinyin);
        value.setPinyinNorm(PinyinUtils.normalize(pinyin));
        value.setMeaningVi(meaning);
        value.setMeaningNorm(TextNormalizer.normalizeMeaning(meaning));
        value.setHskLevel((short) 1);
        value.setFrequency(frequency);
        return lexemes.saveAndFlush(value);
    }

    private MockHttpServletRequestBuilder request(String type, String q) {
        return get("/api/public/dictionary/search").param("type", type).param("q", q)
                .with(request -> { request.setRemoteAddr(address); return request; });
    }

    private JsonNode response(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private String bearer(long userId, String role) {
        return "Bearer " + jwt.issueAccessToken(userId, List.of(role), false, UUID.randomUUID());
    }

    private String hour() { return DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC).format(clock.instant()); }
    private String guestKey() { return "library:dictionary:guest:" + address + ":" + hour(); }
    private String userKey(long userId) { return "library:dictionary:user:" + userId + ":" + hour(); }
}
