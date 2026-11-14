package com.cnhsk.learning.leaderboard.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cnhsk.learning.leaderboard.LeaderboardRedisClient;
import com.cnhsk.learning.leaderboard.LeaderboardService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class LeaderboardControllerIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-16T03:00:00Z");
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final String GAME = "/api/public/leaderboards/games/FLYING_DART";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired StringRedisTemplate redis;
    @Autowired JwtService jwt;
    @Autowired LeaderboardService service;
    @MockitoBean Clock clock;
    @MockitoSpyBean LeaderboardRedisClient cacheClient;
    private final List<String> keys = new ArrayList<>();
    private long topic;
    private long player;
    private String remote;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        remote = "192.0.2." + (System.nanoTime() % 200 + 1);
        topic = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level,order_index) VALUES (?,?,1,2) RETURNING id",
                Long.class, "LB_" + UUID.randomUUID().toString().replace("-", ""), "Chủ đề bảng xếp hạng");
        player = user("Dũng");
        keys.add("lb:game:FLYING_DART:all:all");
        keys.add("lb:topic:" + topic + ":all:all");
        keys.add("lb:topic:" + topic + ":week:" + java.time.LocalDate.parse("2026-11-16").toEpochDay());
        keys.add("lb:rate:guest:" + remote + ":" + NOW.getEpochSecond());
        redis.delete(keys);
    }

    @AfterEach void cleanupRedis() { redis.delete(keys); }

    @Test void AT01_topicTopTwentyGuestPrivacyAndRank128() throws Exception {
        long last = player;
        for (int rank = 1; rank <= 128; rank++) {
            last = rank == 1 ? player : user("Người " + rank);
            attempt(last, "QUIZ", topic, null, 1000L - rank, false);
        }
        JsonNode guest = read("/api/public/leaderboards/topics/" + topic + "?period=all", null);
        assertThat(guest.fieldNames()).toIterable().containsExactlyInAnyOrder("type", "ref", "period", "periodStart", "periodEnd", "entries", "myEntry");
        assertThat(guest.path("entries")).hasSize(20);
        assertThat(guest.path("myEntry").isNull()).isTrue();
        assertThat(guest.path("entries").get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("rank", "userId", "displayName", "avatarUrl", "score", "durationMs");
        assertThat(guest.toString()).doesNotContain("email", "password", "submittedAt", "banned");
        JsonNode own = read("/api/public/leaderboards/topics/" + topic + "?period=all", last);
        assertThat(own.path("myEntry").path("rank").asLong()).isEqualTo(128);
        assertThat(service.rankOf(last, topic)).contains(new LeaderboardService.Rank(128, 128));
    }

    @Test void FR005_offsetIsVietnamAndEmptyOrUnknownTopicsFollowContract() throws Exception {
        JsonNode empty = read("/api/public/leaderboards/topics/" + topic, null);
        assertThat(empty.path("period").asText()).isEqualTo("week");
        assertThat(empty.path("periodStart").asText()).isEqualTo("2026-11-16T00:00:00+07:00");
        assertThat(empty.path("periodEnd").asText()).isEqualTo("2026-11-23T00:00:00+07:00");
        assertThat(empty.path("entries")).isEmpty();
        mvc.perform(get("/api/public/leaderboards/topics/" + Long.MAX_VALUE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("TOPIC_NOT_FOUND"));
    }

    @Test void FR014_FR015_catalogsArePublicOrderedAndMatchSixGameNames() throws Exception {
        jdbc.update("INSERT INTO learning.topics(code,name,hsk_level,order_index) VALUES (?,?,1,1)",
                "LB_" + UUID.randomUUID().toString().replace("-", ""), "Chủ đề đứng trước");
        JsonNode topics = read("/api/public/leaderboards/topics?hsk=1", null);
        var fixtureNames = new ArrayList<String>();
        topics.forEach(value -> { if (value.path("name").asText().startsWith("Chủ đề")) fixtureNames.add(value.path("name").asText()); });
        assertThat(fixtureNames).containsSubsequence("Chủ đề đứng trước", "Chủ đề bảng xếp hạng");
        topics.forEach(value -> assertThat(value.path("hskLevel").asInt()).isEqualTo(1));
        JsonNode games = read("/api/public/leaderboards/games", null);
        assertThat(games).hasSize(6);
        assertThat(games.get(4).path("code").asText()).isEqualTo("BRUSH_STROKE");
        assertThat(games.get(4).path("name").asText()).isEqualTo("Thần Bút");
        assertThat(games.get(5).path("code").asText()).isEqualTo("FLYING_DART");
        assertThat(games.get(5).path("name").asText()).isEqualTo("Phi Đao");
    }

    @Test void AT14_cachedTopAndFreshMyEntryAreIsolatedBetweenViewers() throws Exception {
        attempt(player, "GAME", null, "FLYING_DART", 50L, false);
        JsonNode first = read(GAME + "?period=all", null);
        assertThat(first.path("entries").get(0).path("score").asLong()).isEqualTo(50);
        attempt(player, "GAME", null, "FLYING_DART", 80L, false);
        JsonNode second = read(GAME + "?period=all", player);
        assertThat(second.path("entries").get(0).path("score").asLong()).isEqualTo(50);
        assertThat(second.path("myEntry").path("score").asLong()).isEqualTo(80);
        assertThat(read(GAME + "?period=all", user("Khách khác")).path("myEntry").isNull()).isTrue();
        jdbc.update("UPDATE auth.users SET display_name='Tên mới' WHERE id=?", player);
        assertThat(read(GAME + "?period=all", null).path("entries").get(0).path("displayName").asText()).isEqualTo("Tên mới");
        String raw = redis.opsForValue().get("lb:game:FLYING_DART:all:all");
        assertThat(raw).doesNotContain("myEntry", "displayName", "email", "avatarUrl");
    }

    @Test void bannedAndSuspiciousFilteringUpdatesAfterExpiryAndRankOfReadsFreshBans() throws Exception {
        long suspicious = user("Gian lận");
        attempt(suspicious, "GAME", null, "FLYING_DART", 999L, true);
        attempt(player, "GAME", null, "FLYING_DART", 80L, false);
        attempt(player, "QUIZ", topic, null, 80L, false);
        assertThat(read(GAME + "?period=all", null).path("entries")).hasSize(1);
        jdbc.update("UPDATE auth.users SET banned_at=now(),ban_reason='fixture' WHERE id=?", player);
        assertThat(service.rankOf(player, topic)).isEmpty();
        assertThat(read(GAME + "?period=all", null).path("entries")).hasSize(1); // Approved TTL-only delay.
        redis.delete("lb:game:FLYING_DART:all:all");
        assertThat(read(GAME + "?period=all", null).path("entries")).isEmpty();
        jdbc.update("UPDATE auth.users SET banned_at=NULL,ban_reason=NULL WHERE id=?", player);
        redis.delete("lb:game:FLYING_DART:all:all");
        assertThat(read(GAME + "?period=all", null).path("entries")).hasSize(1);
        jdbc.update("UPDATE learning.attempts SET suspicious=true WHERE user_id=?", player);
        redis.delete("lb:game:FLYING_DART:all:all");
        assertThat(read(GAME + "?period=all", null).path("entries")).isEmpty();
    }

    @Test void AT13_cacheReadOrWriteFailureStillReturnsDatabaseResult() throws Exception {
        attempt(player, "GAME", null, "FLYING_DART", 80L, false);
        doThrow(new IllegalStateException("connection-refused")).when(cacheClient).get(anyString());
        assertThat(read(GAME + "?period=all", player).path("myEntry").path("score").asLong()).isEqualTo(80);
        verify(cacheClient, never()).set(anyString(), anyString(), any());
        reset(cacheClient);
        doThrow(new IllegalStateException("write-failed")).when(cacheClient).set(anyString(), anyString(), any());
        assertThat(read(GAME + "?period=all", null).path("entries")).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"/topics?hsk=0,VALIDATION_FAILED", "/topics?hsk=10,VALIDATION_FAILED", "/topics?hsk=x,VALIDATION_FAILED",
            "/topics?hsk=9223372036854775808,VALIDATION_FAILED", "/topics/0,VALIDATION_FAILED", "/topics/abc,VALIDATION_FAILED",
            "/topics/9223372036854775808,VALIDATION_FAILED", "/games/char_rain,INVALID_GAME_CODE", "/games/UNKNOWN,INVALID_GAME_CODE",
            "/games/CHAR_RAIN?period=2026-W39,VALIDATION_FAILED", "/games/CHAR_RAIN?period=,VALIDATION_FAILED"})
    void AT11_AT12_invalidInputIs422(String suffix, String code) throws Exception {
        mvc.perform(get("/api/public/leaderboards" + suffix)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value(code)).andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    private JsonNode read(String path, Long viewer) throws Exception {
        var request = get(path).with(req -> { req.setRemoteAddr(remote); return req; });
        if (viewer != null) request.header("Authorization", "Bearer " + jwt.issueAccessToken(viewer, List.of("LEARNER"), false, UUID.randomUUID()));
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private long user(String name) {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", HASH, name);
    }

    private void attempt(long user, String kind, Long topicId, String game, long score, boolean suspicious) {
        jdbc.update("""
                INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,game_code,status,score,duration_ms,submitted_at,suspicious)
                VALUES (?,?,?,?,?,'SUBMITTED',?,100,?,?)
                """, user, kind, topicId == null ? null : "QUIZ", topicId, game, score, Timestamp.from(NOW), suspicious);
    }
}
