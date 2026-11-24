package com.cnhsk.community.game;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import com.cnhsk.learning.api.ContentLookup;
import com.cnhsk.learning.api.GameCode;
import com.cnhsk.learning.api.GameStatsApi;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@Import(GameCatalogControllerIntegrationTest.Fixtures.class)
class GameCatalogControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/games";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final List<ExpectedMetadata> METADATA = List.of(
            new ExpectedMetadata("Viết đúng thứ tự nét để giữ mực trong lọ.", "WRITING",
                    "Viết từng nét theo đúng thứ tự trên khung chữ mờ. Mỗi lần viết sai, lọ mực vơi một phần ba; sai ba lần là hết mực. Viết được càng nhiều chữ trong 60 giây càng cao điểm.", 60),
            new ExpectedMetadata("Ghép từng từ tiếng Trung với thẻ pinyin của từ đó.", "VOCAB",
                    "Chọn một từ, rồi chọn thẻ pinyin tương ứng. Xong năm bảng càng nhanh càng cao điểm; ghép sai ba lần là dừng.", 600),
            new ExpectedMetadata("Nhận diện bộ thủ của từng chữ Hán.", "CHARACTER",
                    "Nhìn chữ và chọn bộ thủ đúng trong bốn phương án. Xong 15 câu càng nhanh càng cao điểm; sai ba lần là dừng.", 600),
            new ExpectedMetadata("Chọn chữ hoặc từ khớp với nghĩa, pinyin hoặc âm thanh.", "LISTENING",
                    "Đọc hoặc nghe gợi ý rồi chọn một trong sáu phương án. Đúng càng nhiều câu trong 60 giây càng cao điểm.", 60),
            new ExpectedMetadata("Viết chữ Hán đang rơi trước khi nó chạm đất.", "WRITING",
                    "Viết chữ đang rơi vào ô trống, không cần đúng thứ tự nét. Chữ rơi nhanh dần; viết được càng nhiều chữ và càng nhanh thì hạng càng cao.", 600),
            new ExpectedMetadata("Gõ âm Hán Việt để gấu phi dao hạ chữ.", "CHARACTER",
                    "Gõ âm Hán Việt của chữ rồi nhấn Enter, có dấu hay không dấu đều được. Phi trúng càng nhiều chữ trong 60 giây càng cao điểm; gõ sai ba lần là dừng.", 60));
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private TestClock clock;
    @MockitoSpyBean private ContentLookup content;
    @MockitoSpyBean private GameStatsApi stats;
    private long owner;
    private long other;

    @BeforeEach void fixture() {
        clock.advance(120);
        doReturn(playablePool()).when(content).pool(any());
        owner = user(); other = user();
        clearInvocations(stats, content);
    }

    @Test void AT02_guestGetsAllSixGamesInEnumOrderWithOnlyPublicFields() throws Exception {
        var games = ok(get(BASE));
        assertThat(games).hasSize(6);
        for (int index = 0; index < GameCode.values().length; index++) {
            var game = games.get(index); var code = GameCode.values()[index];
            var metadata = METADATA.get(index);
            assertThat(game.path("code").asText()).isEqualTo(code.name());
            assertThat(game.path("name").asText()).isEqualTo(code.displayName());
            assertThat(game.path("description").asText()).isEqualTo(metadata.description());
            assertThat(game.path("skill").asText()).isEqualTo(metadata.skill());
            assertThat(game.path("howToPlay").asText()).isEqualTo(metadata.howToPlay());
            assertThat(game.path("timeLimitSeconds").asInt()).isEqualTo(metadata.timeLimitSeconds());
            assertThat(game.path("playable").asBoolean()).isTrue();
            assertThat(game.path("unavailableReason").isNull()).isTrue();
            assertThat(game.path("myStats").isNull()).isTrue();
            assertThat(fields(game)).containsExactlyInAnyOrder("code", "name", "description", "skill", "howToPlay",
                    "timeLimitSeconds", "playable", "unavailableReason", "myStats");
        }
        verifyNoInteractions(stats);
        verify(content, times(6)).pool(argThat(query -> query.preferLearnedBy() == null
                // Without a chosen level the catalogue checks the HSK 1-3 content every game draws from.
                && query.hskLevels().equals(java.util.Set.of(1, 2, 3))));
    }

    @Test void AT02_noUsableContentDisablesTheCardsWithoutReturningAnswerData() throws Exception {
        doReturn(List.of()).when(content).pool(any());
        for (var game : ok(get(BASE))) {
            assertThat(game.path("playable").asBoolean()).isFalse();
            assertThat(game.path("unavailableReason").asText()).isEqualTo("NOT_ENOUGH_CONTENT");
            assertThat(fields(game)).doesNotContain("items", "answer", "accept", "uiConfig", "pointsPerCorrect", "userId");
        }
    }

    @Test void AT03_suspiciousRoundCountsButDoesNotSetThePersonalRecord() throws Exception {
        Instant now = clock.instant();
        attempt(owner, "GAME", "SUBMITTED", 5, false, now.minusSeconds(30));
        attempt(owner, "GAME", "SUBMITTED", 8, false, now.minusSeconds(20));
        attempt(owner, "GAME", "SUBMITTED", 9, true, now.minusSeconds(10));
        for (String state : List.of("IN_PROGRESS", "ABANDONED", "EXPIRED")) attempt(owner, "GAME", state, 99, false, now);
        attempt(owner, "PRACTICE", "SUBMITTED", 100, false, now);
        attempt(other, "GAME", "SUBMITTED", 90, false, now);
        var games = ok(get(BASE).header("Authorization", bearer(owner, "LEARNER")));
        var recorded = games.get(0).path("myStats");
        assertThat(recorded.path("bestPoints").asLong()).isEqualTo(80);
        assertThat(recorded.path("roundsPlayed").asLong()).isEqualTo(3);
        assertThat(recorded.path("lastPlayedAt").asText()).isEqualTo(now.minusSeconds(9).toString());
        assertThat(fields(recorded)).containsExactlyInAnyOrder("bestPoints", "roundsPlayed", "lastPlayedAt");
        assertThat(games.get(1).path("myStats").path("roundsPlayed").asLong()).isZero();
        verify(stats).gameStats(owner); verifyNoMoreInteractions(stats);
    }

    @Test void FR005_oneCommonSnapshotKeepsTwoUsersGuestAndNewStatsSeparate() throws Exception {
        Instant now = clock.instant();
        attempt(owner, "GAME", "SUBMITTED", 5, false, now.minusSeconds(5));
        attempt(other, "GAME", "SUBMITTED", 2, false, now.minusSeconds(4));
        var first = ok(get(BASE).header("Authorization", bearer(owner, "LEARNER")));
        assertThat(first.get(0).path("myStats").path("bestPoints").asLong()).isEqualTo(50);
        var second = ok(get(BASE).param("userId", Long.toString(owner)).header("Authorization", bearer(other, "LEARNER")));
        assertThat(second.get(0).path("myStats").path("bestPoints").asLong()).isEqualTo(20);
        attempt(owner, "GAME", "SUBMITTED", 8, false, now.minusSeconds(2));
        assertThat(ok(get(BASE).header("Authorization", bearer(owner, "LEARNER"))).get(0)
                .path("myStats").path("bestPoints").asLong()).isEqualTo(80);
        assertThat(ok(get(BASE)).get(0).path("myStats").isNull()).isTrue();
        assertThat(first.get(0).path("myStats").path("bestPoints").asLong()).isEqualTo(50);
        verify(content, times(6)).pool(any());
        verify(stats, times(2)).gameStats(owner); verify(stats).gameStats(other); verifyNoMoreInteractions(stats);
    }

    @Test void Q_T04_03_signedInWithNoRoundsGetsZeroStatisticsForEveryGame() throws Exception {
        for (var game : ok(get(BASE).header("Authorization", bearer(owner, "LEARNER")))) {
            var personal = game.path("myStats");
            assertThat(personal.isObject()).isTrue();
            assertThat(personal.path("bestPoints").asLong()).isZero();
            assertThat(personal.path("roundsPlayed").asLong()).isZero();
            assertThat(personal.path("lastPlayedAt").isNull()).isTrue();
        }
    }

    @Test void FR002_publicGetAlsoWorksForEveryOtherAuthenticatedRole() throws Exception {
        for (String role : List.of("TEACHER", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT", "ADMIN")) {
            assertThat(ok(get(BASE).header("Authorization", bearer(owner, role)))).hasSize(6);
        }
    }

    @Test void FR002_cookieAuthenticationCanReadWithoutCsrfButDoesNotOpenUnsafeRoutes() throws Exception {
        Cookie access = new Cookie("access_token", token(owner, "LEARNER"));
        assertThat(ok(get(BASE).cookie(access))).hasSize(6);
        mvc.perform(post(BASE).cookie(access)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
    }

    @Test void FR002_onlyTheExactGetIsPublicAndCommunityPermissionsRemainInForce() throws Exception {
        mvc.perform(post(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/CHAR_RAIN")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/CHAR_RAIN/rounds")).andExpect(status().isUnauthorized());
        for (var request : List.of(post(BASE), get(BASE + "/CHAR_RAIN"), post(BASE + "/CHAR_RAIN/rounds"))) {
            mvc.perform(request.header("Authorization", bearer(owner, "TEACHER"))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test void T09_expiredCookieStillGets401AndCallingAgainWithoutItWorksAsGuest() throws Exception {
        String access = token(owner, "LEARNER"); clock.advance(901);
        mvc.perform(get(BASE).cookie(new Cookie("access_token", access))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("TOKEN_EXPIRED"));
        assertThat(ok(get(BASE)).get(0).path("myStats").isNull()).isTrue();
        verifyNoInteractions(stats);
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private Set<String> fields(JsonNode value) { Set<String> fields = new HashSet<>(); value.fieldNames().forEachRemaining(fields::add); return fields; }
    private long user() { return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Game catalog fixture') RETURNING id",
            Long.class, UUID.randomUUID() + "@example.test", HASH); }
    private String token(long id, String role) { return jwt.issueAccessToken(id, List.of(role), false, UUID.randomUUID()); }
    private String bearer(long id, String role) { return "Bearer " + token(id, role); }
    private void attempt(long user, String kind, String state, long correct, boolean suspicious, Instant served) {
        jdbc.update("INSERT INTO learning.attempts(user_id,kind,ref_type,game_code,status,served_at,submitted_at,score,suspicious) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)", user, kind, kind.equals("GAME") ? "GAME_ROUND" : "TOPIC_PRACTICE", "CHAR_RAIN", state,
                Timestamp.from(served), state.equals("SUBMITTED") ? Timestamp.from(served.plusSeconds(1)) : null, correct, suspicious);
    }

    private static List<ContentLookup.LexemeCard> playablePool() {
        var initials = List.of("bā", "bá", "bǎ", "bà", "mā", "má");
        var finals = List.of("ní", "shí", "bó", "tí");
        var radicals = List.of("氵", "冫", "木", "日");
        return IntStream.range(0, 48).mapToObj(index -> {
            String pinyin = initials.get(index % 6) + " " + finals.get((index % 24) / 6);
            String kind = index < 24 ? "CHAR" : "WORD";
            return new ContentLookup.LexemeCard(index + 1L, kind, "字組" + index, pinyin,
                    "pinyin-" + index % 24, "nghĩa mẫu " + index, Set.of("nghĩa mẫu " + index),
                    radicals.get(index % radicals.size()), 4, null, 1, "nhân");
        }).toList();
    }

    private record ExpectedMetadata(String description, String skill, String howToPlay, int timeLimitSeconds) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean @Primary TestClock gameCatalogClock() { return new TestClock(); }
    }

    static final class TestClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;
        TestClock() { this(new AtomicReference<>(Instant.parse("2035-11-14T01:00:00Z")), ZoneOffset.UTC); }
        private TestClock(AtomicReference<Instant> current, ZoneId zone) { this.current = current; this.zone = zone; }
        void advance(long seconds) { current.updateAndGet(now -> now.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId value) { return new TestClock(current, value); }
        @Override public Instant instant() { return current.get(); }
    }
}
