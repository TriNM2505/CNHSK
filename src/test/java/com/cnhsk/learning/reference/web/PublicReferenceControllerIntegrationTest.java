package com.cnhsk.learning.reference.web;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.learning.reference.ReferenceQueryService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class PublicReferenceControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/public/references";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Instant NOW = Instant.parse("2026-11-13T12:34:25Z");
    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private ReferenceQueryService service;
    @Autowired private RateLimiter counters;
    @Autowired private StringRedisTemplate redis;
    private long creator;
    private String ip;

    @BeforeEach
    void fixture() {
        ip = "2001:db8::" + Integer.toHexString(UUID.randomUUID().hashCode());
        creator = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Tu soan");
    }

    @Test
    void AT01_AT03_FR001_allRolesAndGuestSeeOnlyPublicFields() throws Exception {
        long book = seed("BOOK", "PUBLISHED");
        long media = seed("YOUTUBE_CHANNEL", "PUBLISHED");
        seed("BOOK", "DRAFT");
        seed("BOOK", "HIDDEN");
        JsonNode guest = ok(get(BASE).param("kind", "BOOK"));
        assertThat(guest.path("total").asLong()).isEqualTo(1);
        assertThat(fields(guest)).containsExactlyInAnyOrder("items", "page", "size", "total");
        JsonNode detail = ok(get(BASE + "/" + book));
        assertThat(fields(detail)).containsExactlyInAnyOrder("id", "kind", "title", "description", "url",
                "thumbnailUrl", "hskMin", "hskMax", "tags", "author", "publisher", "publishedYear", "isAffiliate");
        assertThat(detail.path("isAffiliate").asBoolean()).isTrue();
        assertThat(detail.path("description").asText()).isEqualTo("<script>demo</script>");
        assertThat(detail.path("thumbnailUrl").isNull()).isTrue();
        assertThat(fields(ok(get(BASE + "/" + media)))).containsExactlyInAnyOrder("id", "kind", "title",
                "description", "url", "thumbnailUrl", "hskMin", "hskMax", "tags");
        for (String role : List.of("LEARNER", "TEACHER", "CONTENT_EDITOR", "MODERATOR", "ADMIN", "ACCOUNTANT")) {
            String token = jwt.issueAccessToken(creator, Set.of(role), false, UUID.randomUUID());
            assertThat(ok(get(BASE).param("kind", "BOOK").header("Authorization", "Bearer " + token))).isEqualTo(guest);
            assertThat(ok(get(BASE + "/" + book).header("Authorization", "Bearer " + token))).isEqualTo(detail);
        }
        for (JsonNode item : List.of(detail, guest.path("items").get(0))) {
            assertThat(fields(item)).doesNotContain("createdBy", "status", "reportCount", "lastReportedAt",
                    "sortOrder", "publishedAt", "createdAt", "updatedAt");
        }
    }

    @Test
    void AT04_hiddenDraftMissingAndInvalidIdsShareThe404Contract() throws Exception {
        List<String> ids = new ArrayList<>(List.of("0", "-1", "abc", "9223372036854775808", Long.toString(Long.MAX_VALUE)));
        ids.add(Long.toString(seed("BOOK", "DRAFT")));
        ids.add(Long.toString(seed("BOOK", "HIDDEN")));
        for (String id : ids) {
            mvc.perform(get(BASE + "/" + id).with(request -> {request.setRemoteAddr(ip); return request;}))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("REFERENCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.request_id").isNotEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"kind, OTHER", "hsk, 0", "hsk, 10", "hsk, abc", "page, -1", "page, 2147483648",
            "size, 0", "size, 51", "size, 1.5", "tag, abcdefghijklmnopqrstuvwxyz12345"})
    void FR004_invalidQueryReturns422(String field, String value) throws Exception {
        mvc.perform(get(BASE).param("kind", "BOOK").param(field, value)
                        .with(request -> {request.setRemoteAddr(ip); return request;}))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void FR004_missingKindReturns422AndPaginationDefaultsAreApplied() throws Exception {
        mvc.perform(get(BASE).with(request -> {request.setRemoteAddr(ip); return request;}))
                .andExpect(status().isUnprocessableEntity());
        JsonNode page = ok(get(BASE).param("kind", "BOOK"));
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertThat(page.path("items")).isEmpty();
    }

    @Test
    void FR001_postAndUnrelatedChildPathsAreNotPublic() throws Exception {
        mvc.perform(post(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/1/extra")).andExpect(status().isUnauthorized());
        String token = jwt.issueAccessToken(creator, Set.of("LEARNER"), false, UUID.randomUUID());
        mvc.perform(get(BASE + "/1/extra").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestTwoEndpointsAndInvalidParametersShareThe120RequestBudget() throws Exception {
        MockMvc fixed = MockMvcBuilders.standaloneSetup(controller(NOW, counters))
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        redis.opsForValue().set(key(NOW), "118", Duration.ofSeconds(35));
        fixed.perform(get(BASE).with(request -> {request.setRemoteAddr(ip); return request;}))
                .andExpect(status().isUnprocessableEntity());
        fixed.perform(get(BASE + "/0").with(request -> {request.setRemoteAddr(ip); return request;}))
                .andExpect(status().isNotFound());
        fixed.perform(get(BASE).param("kind", "BOOK").with(request -> {request.setRemoteAddr(ip); return request;}))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(35));
        assertThat(redis.opsForValue().get(key(NOW))).isEqualTo("121");
        assertThatCode(() -> controller(NOW, counters).countGuestRequest(request("192.0.2.2"), null))
                .doesNotThrowAnyException();
        controller(NOW.plusSeconds(35), counters).countGuestRequest(request(ip), null);
        assertThat(redis.opsForValue().get(key(NOW.plusSeconds(35)))).isEqualTo("1");
    }

    @Test
    void concurrentGuestsCompeteForOneRemainingSlotAndKeepTheTtl() throws Exception {
        redis.opsForValue().set(key(NOW), "119", Duration.ofSeconds(35));
        var controller = controller(NOW, counters);
        var pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) results.add(pool.submit(() -> {
                start.await();
                try {
                    controller.countGuestRequest(request(ip), null);
                    return true;
                } catch (ApiException error) {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    return false;
                }
            }));
            start.countDown();
            int accepted = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
            assertThat(redis.opsForValue().get(key(NOW))).isEqualTo("127");
            assertThat(redis.getExpire(key(NOW), TimeUnit.SECONDS)).isBetween(20L, 35L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void authenticatedRequestsDoNotCountAndRetryIsAtLeastOneSecond() {
        RateLimiter isolated = mock(RateLimiter.class);
        controller(NOW, isolated).countGuestRequest(request(ip), new AuthenticatedUser(creator, Set.of("LEARNER"),
                false, "test-token-id", UUID.randomUUID(), NOW));
        verifyNoInteractions(isolated);
        Instant lastFraction = Instant.parse("2026-11-13T12:34:59.999999Z");
        controller(lastFraction, counters).countGuestRequest(request(ip), null);
        assertThat(redis.getExpire(key(lastFraction), TimeUnit.MILLISECONDS)).isBetween(1L, 1000L);
    }

    @Test
    void redisFailureAllowsRequestAndLogsOnlyAFixedWarning() {
        RateLimiter failed = mock(RateLimiter.class);
        when(failed.increment(anyString(), any(Duration.class))).thenThrow(new IllegalStateException("PRIVATE_UPSTREAM"));
        Logger logger = (Logger) LoggerFactory.getLogger(PublicReferenceController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatCode(() -> controller(NOW, failed).countGuestRequest(request(ip), null)).doesNotThrowAnyException();
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .isEqualTo("Reference public rate limit unavailable; allowing request");
            assertThat(appender.list.get(0).getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private PublicReferenceController controller(Instant now, RateLimiter backend) {
        return new PublicReferenceController(service, backend, Clock.fixed(now, ZoneOffset.UTC));
    }

    private String key(Instant now) {
        return "reference:public:guest:" + ip + ":" + DateTimeFormatter.ofPattern("yyyyMMddHHmm")
                .withZone(ZoneOffset.UTC).format(now);
    }

    private MockHttpServletRequest request(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request.with(value -> {value.setRemoteAddr(ip); return value;}))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Set<String> fields(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private long seed(String kind, String state) {
        return jdbc.queryForObject("INSERT INTO learning.reference_items(kind,title,description,url,status,created_by,"
                        + "published_at,author,publisher,published_year,is_affiliate) VALUES (?, 'Demo', '<script>demo</script>',"
                        + "'https://fahasa.com/demo', ?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class,
                kind, state, creator, state.equals("PUBLISHED") ? Timestamp.from(NOW) : null,
                kind.equals("BOOK") ? "Tac gia" : null, kind.equals("BOOK") ? "NXB" : null,
                kind.equals("BOOK") ? 2026 : null, kind.equals("BOOK"));
    }
}
