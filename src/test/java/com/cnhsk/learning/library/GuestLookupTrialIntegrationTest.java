package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.cnhsk.shared.config.CnhskProperties;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SecureTokenGenerator;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class GuestLookupTrialIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger ADDRESS = new AtomicInteger(10);

    @Autowired private MockMvc mvc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JwtService jwt;
    @Autowired private LibraryConfig config;
    @Autowired private CnhskProperties properties;
    private String address;

    @BeforeEach
    void isolateCaller() {
        address = "203.0.113." + ADDRESS.incrementAndGet();
    }

    @Test
    void firstLookupGivesTheBrowserAPrivateIdAndCountsDown() throws Exception {
        trial(null).andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.remaining").value(10));

        MockHttpServletResponse first = search(null, "HANZI", "自造一").andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie guest = first.getCookie(GuestLookupTrial.COOKIE);
        assertThat(guest).isNotNull();
        assertThat(first.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax", "Path=/api/public/dictionary");
        trial(guest).andExpect(jsonPath("$.remaining").value(9));

        assertThat(search(guest, "HANZI", "自造二").andReturn().getResponse()
                .getCookie(GuestLookupTrial.COOKIE)).isNull();
        trial(guest).andExpect(jsonPath("$.remaining").value(8));
    }

    @Test
    void pagingRepeatingAndInvalidOrTooLongLookupsAreFree() throws Exception {
        Cookie guest = newGuest();
        search(guest, "PINYIN", "ni hao").andExpect(status().isOk());
        search(guest, "PINYIN", "  NI HAO ").andExpect(status().isOk());
        mvc.perform(request("/search").cookie(guest).param("type", "PINYIN").param("q", "ni hao").param("page", "1"))
                .andExpect(status().isOk());
        search(guest, "MEANING", "a").andExpect(status().isUnprocessableEntity());
        search(guest, "MEANING", "b".repeat(51)).andExpect(status().isOk()).andExpect(jsonPath("$.hint").value("USE_TRANSLATE"));
        mvc.perform(request("/radicals").cookie(guest)).andExpect(status().isOk());
        mvc.perform(request("/lexemes/9223372036854775807").cookie(guest)).andExpect(status().isNotFound());
        trial(guest).andExpect(jsonPath("$.remaining").value(9));
    }

    @Test
    void allFourLookupTypesShareTenLookupsThenAskToSignIn() throws Exception {
        Cookie guest = newGuest();
        for (String type : List.of("HANZI", "PINYIN", "MEANING")) search(guest, type, "chung").andExpect(status().isOk());
        mvc.perform(request("/by-radical").cookie(guest).param("strokes", "4")).andExpect(status().isOk());
        for (int index = 0; index < 6; index++) search(guest, "MEANING", "nghia " + index).andExpect(status().isOk());
        trial(guest).andExpect(jsonPath("$.remaining").value(0));

        search(guest, "HANZI", "moi").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.loginSuggested").value(true))
                .andExpect(jsonPath("$.guestTrialExhausted").value(true))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(jsonPath("$.items").doesNotExist());
        mvc.perform(request("/by-radical").cookie(guest).param("strokes", "5")).andExpect(status().isTooManyRequests());

        search(guest, "HANZI", "chung").andExpect(status().isOk());
        search(newGuest(), "HANZI", "moi").andExpect(status().isOk());
    }

    @Test
    void parallelLookupsCannotPassTheLimit() throws Exception {
        Cookie guest = newGuest();
        List<Integer> statuses = IntStream.range(0, 16).parallel().mapToObj(index -> {
            try {
                return search(guest, "MEANING", "song song " + index).andReturn().getResponse().getStatus();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).toList();
        assertThat(statuses).filteredOn(value -> value == 200).hasSize(10);
        assertThat(statuses).filteredOn(value -> value == 429).hasSize(6);
    }

    @Test
    void signedInUsersHaveNoTrialAndGetNoCookie() throws Exception {
        String bearer = "Bearer " + jwt.issueAccessToken(930_001, List.of("LEARNER"), true, UUID.randomUUID());
        Cookie guest = newGuest();
        for (int index = 0; index < 11; index++) {
            assertThat(mvc.perform(request("/search").cookie(guest).header("Authorization", bearer)
                            .param("type", "MEANING").param("q", "hoc vien " + index))
                    .andExpect(status().isOk()).andReturn().getResponse().getCookie(GuestLookupTrial.COOKIE)).isNull();
        }
        mvc.perform(request("/guest-trial").cookie(guest).header("Authorization", bearer)).andExpect(status().isNoContent());
        trial(guest).andExpect(jsonPath("$.remaining").value(10));
    }

    @Test
    void malformedCookieIsReplacedInsteadOfBeingUsedAsAKey() throws Exception {
        MockHttpServletResponse response = search(new Cookie(GuestLookupTrial.COOKIE, "bad:*"), "HANZI", "自造")
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(GuestLookupTrial.isGuestId(response.getCookie(GuestLookupTrial.COOKIE).getValue())).isTrue();
        assertThat(redis.keys("library:guest-trial:bad*")).isEmpty();
    }

    @Test
    void theTrialStartsAgainAtMidnightInVietnamAndStoresNoLookupText() {
        String guest = new SecureTokenGenerator().generate();
        GuestLookupTrial lateEvening = at("2026-11-22T16:59:30Z");
        for (int index = 0; index < 10; index++) lateEvening.use(guest, "MEANING", "bi mat " + index);
        assertThatThrownBy(() -> lateEvening.use(guest, "MEANING", "them"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getExtra())
                        .containsEntry("retryAfterSeconds", 30L));
        String key = "library:guest-trial:" + guest + ":20261122";
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 30L);
        assertThat(redis.opsForSet().members(key)).hasSize(10).allSatisfy(member -> assertThat(member).doesNotContain("bi mat"));

        GuestLookupTrial nextDay = at("2026-11-22T17:00:00Z");
        assertThat(nextDay.status(guest)).hasValue(new GuestLookupTrial.Status(10, 10));
        nextDay.use(guest, "MEANING", "them");
        assertThat(nextDay.status(guest)).hasValue(new GuestLookupTrial.Status(10, 9));
    }

    @Test
    void redisFailureAllowsTheLookupAndHidesTheCounter() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.execute(any(), any(), any(Object[].class))).thenThrow(new IllegalStateException("down"));
        when(broken.opsForSet()).thenThrow(new IllegalStateException("down"));
        GuestLookupTrial trial = new GuestLookupTrial(broken, Clock.systemUTC(), config, new SecureTokenGenerator(), properties);
        String guest = trial.newGuestId();
        trial.use(guest, "HANZI", "a");
        assertThat(trial.status(guest)).isEmpty();
    }

    private GuestLookupTrial at(String instant) {
        return new GuestLookupTrial(redis, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), config,
                new SecureTokenGenerator(), properties);
    }

    private Cookie newGuest() {
        return new Cookie(GuestLookupTrial.COOKIE, new SecureTokenGenerator().generate());
    }

    private ResultActions search(Cookie guest, String type, String q) throws Exception {
        MockHttpServletRequestBuilder request = request("/search").param("type", type).param("q", q);
        return mvc.perform(guest == null ? request : request.cookie(guest));
    }

    private ResultActions trial(Cookie guest) throws Exception {
        MockHttpServletRequestBuilder request = request("/guest-trial");
        return mvc.perform(guest == null ? request : request.cookie(guest));
    }

    private MockHttpServletRequestBuilder request(String path) {
        return get("/api/public/dictionary" + path).with(request -> {
            request.setRemoteAddr(address);
            return request;
        });
    }
}
