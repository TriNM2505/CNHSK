package com.cnhsk.shared.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cnhsk.learning.leaderboard.LeaderboardDtos.Leaderboard;
import com.cnhsk.learning.leaderboard.LeaderboardDtos.LeaderboardEntry;
import com.cnhsk.learning.leaderboard.LeaderboardRateLimiter;
import com.cnhsk.learning.leaderboard.LeaderboardServiceImpl;
import com.cnhsk.learning.leaderboard.web.LeaderboardController;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.shared.security.SessionInvalidationMarker;
import com.cnhsk.shared.security.SessionInvalidationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LeaderboardController.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class, LeaderboardRateLimiter.class})
class LeaderboardSecurityConfigIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");
    private static final String GAME = "/api/public/leaderboards/games/CHAR_RAIN";
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean LeaderboardServiceImpl service;
    @MockitoBean RateLimiter counters;
    @MockitoBean SessionInvalidationService invalidation;
    @MockitoBean Clock clock;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        when(counters.increment(anyString(), any())).thenReturn(1L);
        when(service.listGames()).thenReturn(List.of());
        when(service.listTopics(any())).thenReturn(List.of());
        when(service.gameLeaderboard(anyString(), any(), any())).thenAnswer(invocation -> result(invocation.getArgument(2)));
        when(service.topicLeaderboard(anyLong(), any(), any())).thenAnswer(invocation -> result(invocation.getArgument(2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/public/leaderboards/topics", "/api/public/leaderboards/topics/9",
            "/api/public/leaderboards/games", "/api/public/leaderboards/games/CHAR_RAIN"})
    void FR001_AT15_fourGetsAllowGuestsAndInvalidCookieOrBearer(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", "Bearer invalid")).andExpect(status().isOk());
        mvc.perform(get(path).cookie(new Cookie("access_token", "invalid"))).andExpect(status().isOk());
    }

    @Test void FR002_validIdentityOnlyComesFromVerifiedCookieOrBearer() throws Exception {
        String token = token();
        mvc.perform(get(GAME).header("Authorization", "Bearer " + token).queryParam("userId", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.myEntry.userId").value(42));
        mvc.perform(get(GAME).cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.myEntry.userId").value(42));
        verifyNoInteractions(counters);
        verify(service, times(2)).gameLeaderboard("CHAR_RAIN", null, 42L);
    }

    @Test void AT15_expiredRevokedAndBannedAreGuestsButProtectedRoutesKeepTheirBehavior() throws Exception {
        String token = token();
        when(clock.instant()).thenReturn(NOW.plusSeconds(901));
        mvc.perform(get(GAME).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.myEntry").isEmpty());
        mvc.perform(get("/api/topics").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        when(clock.instant()).thenReturn(NOW);
        for (var marker : List.of(SessionInvalidationMarker.revoked(NOW.plusMillis(1).toEpochMilli()),
                SessionInvalidationMarker.banned(NOW.plusMillis(1).toEpochMilli(), "fixture"))) {
            when(invalidation.findMarker(42)).thenReturn(Optional.of(marker));
            mvc.perform(get(GAME).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.myEntry").isEmpty());
            mvc.perform(get("/api/topics").header("Authorization", "Bearer " + token))
                    .andExpect(marker.reason() == SessionInvalidationMarker.Reason.BANNED
                            ? status().isForbidden() : status().isUnauthorized());
        }
    }

    @Test void guestLimitIsSharedBetweenEndpointsAndUsesRemoteAddressNotForwardedHeader() throws Exception {
        when(counters.increment(anyString(), any())).thenReturn(120L, 121L);
        mvc.perform(get("/api/public/leaderboards/topics").header("X-Forwarded-For", "198.51.100.99")
                        .with(request -> { request.setRemoteAddr("192.0.2.1"); return request; }))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/leaderboards/games").header("X-Forwarded-For", "198.51.100.77")
                        .with(request -> { request.setRemoteAddr("192.0.2.1"); return request; }))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty()).andExpect(jsonPath("$.retryAfterSeconds").value(60));
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(counters, times(2)).increment(keys.capture(), any());
        assertThat(keys.getAllValues()).containsOnly("lb:rate:guest:192.0.2.1:" + NOW.getEpochSecond());
    }

    @Test void limiterFailsOpenAndCountsInvalidRequestsBeforeValidation() throws Exception {
        when(counters.increment(anyString(), any())).thenThrow(new IllegalStateException("sensitive-url"));
        mvc.perform(get(GAME)).andExpect(status().isOk());
        mvc.perform(get("/api/public/leaderboards/topics").param("hsk", "abc"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        verify(counters, times(2)).increment(anyString(), any());
    }

    @Test void writesAndOtherPathsAreNotMadePublic() throws Exception {
        String bearer = "Bearer " + token();
        mvc.perform(post(GAME).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/public/leaderboards/topics/9").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/leaderboards/games/CHAR_RAIN/extra").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/topics").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    private String token() { return jwt.issueAccessToken(42, List.of("LEARNER"), false, UUID.randomUUID()); }
    private static Leaderboard result(Long viewer) {
        return new Leaderboard("GAME", "CHAR_RAIN", "all", null, null, List.of(),
                viewer == null ? null : new LeaderboardEntry(128, viewer, "Người học", null, 80, null));
    }
}
