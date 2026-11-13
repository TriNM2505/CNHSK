package com.cnhsk.learning.reference;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.reference.ReferenceReportService.Reason;
import com.cnhsk.learning.reference.ReferenceReportService.ReportCommand;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReferenceReportIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T16:30:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    @Autowired private RateLimiter counters;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AuditService audit;
    @Autowired private PlatformTransactionManager manager;
    private final List<Long> items = new ArrayList<>();
    private long user;
    private long otherUser;
    private long item;

    @BeforeEach
    void setup() {
        user = user();
        otherUser = user();
        item = item("PUBLISHED");
    }

    @AfterEach
    void cleanOnlyOwnedFixtures() {
        named.update("DELETE FROM shared.audit_logs WHERE actor_id IN (:users) AND action='REFERENCE_REPORTED'",
                Map.of("users", List.of(user, otherUser)));
        if (!items.isEmpty()) named.update("DELETE FROM learning.reference_items WHERE id IN (:ids)", Map.of("ids", items));
        named.update("DELETE FROM auth.users WHERE id IN (:users)", Map.of("users", List.of(user, otherUser)));
    }

    @Test
    void AT12_firstReportWritesOneAuditAndTheSecondReturns429() {
        report(user, item, NOW, counters, audit);
        assertThat(auditCount()).isEqualTo(1);
        assertThatThrownBy(() -> report(user, item, NOW, counters, audit))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        assertThat(auditCount()).isEqualTo(1);
        assertThat(redis.opsForValue().get(key(user, item, NOW))).isEqualTo("2");
        assertThat(redis.getExpire(key(user, item, NOW), TimeUnit.SECONDS)).isBetween(1700L, 1800L);
    }

    @Test
    void FR010_userItemAndVietnamDayAreIndependent() {
        report(user, item, NOW, counters, audit);
        report(otherUser, item, NOW, counters, audit);
        long another = item("PUBLISHED");
        report(user, another, NOW, counters, audit);
        Instant nextDay = NOW.plusSeconds(1800);
        report(user, item, nextDay, counters, audit);
        assertThat(auditCount()).isEqualTo(4);
        assertThat(key(user, item, NOW)).endsWith(":20261114");
        assertThat(key(user, item, nextDay)).endsWith(":20261115");
        assertThat(redis.opsForValue().get(key(user, item, nextDay))).isEqualTo("1");
        assertThat(redis.getExpire(key(user, item, nextDay), TimeUnit.SECONDS)).isBetween(86300L, 86400L);
    }

    @Test
    void AT12_twoSimultaneousRequestsProduceExactlyOneAudit() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Boolean>> requests = new ArrayList<>();
            for (int index = 0; index < 2; index++) requests.add(pool.submit(() -> {
                start.await();
                try {
                    report(user, item, NOW, counters, audit);
                    return true;
                } catch (ApiException error) {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    return false;
                }
            }));
            start.countDown();
            int accepted = 0;
            for (var request : requests) if (request.get(20, TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
            assertThat(auditCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void FR009_nonPublishedItemsDoNotReserveQuotaOrWriteAudit() {
        for (String status : List.of("DRAFT", "HIDDEN")) {
            long hidden = item(status);
            assertThatThrownBy(() -> report(user, hidden, NOW, counters, audit))
                    .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode())
                            .isEqualTo(ErrorCode.REFERENCE_NOT_FOUND));
            assertThat(redis.hasKey(key(user, hidden, NOW))).isFalse();
        }
        assertThat(auditCount()).isZero();
    }

    @Test
    void FR010_auditFailureRollsBackTheDatabaseButKeepsTheReservedRedisSlot() {
        AuditService broken = mock(AuditService.class);
        doThrow(new IllegalStateException("PRIVATE_NOTE")).when(broken)
                .recordDetail(anyLong(), anyString(), anyString(), anyLong(), anyMap());
        assertThatThrownBy(() -> report(user, item, NOW, counters, broken))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
        assertThat(redis.opsForValue().get(key(user, item, NOW))).isEqualTo("1");
        assertThat(auditCount()).isZero();
        assertThatThrownBy(() -> report(user, item, NOW, counters, audit))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED));
        assertThat(auditCount()).isZero();
    }

    @Test
    void FR009_publishedRowRemainsLockedUntilAuditTransactionCompletes() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        RateLimiter paused = mock(RateLimiter.class);
        when(paused.increment(anyString(), any(Duration.class))).thenAnswer(invocation -> {
            locked.countDown();
            if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            return counters.increment(invocation.getArgument(0), invocation.getArgument(1));
        });
        var pool = Executors.newSingleThreadExecutor();
        var pending = pool.submit(() -> report(user, item, NOW, paused, audit));
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> jdbc.queryForObject(
                    "SELECT id FROM learning.reference_items WHERE id=? FOR UPDATE NOWAIT", Long.class, item)))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            release.countDown();
            try {
                pending.get(20, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
                assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertThat(auditCount()).isEqualTo(1);
    }

    private void report(long caller, long target, Instant now, RateLimiter limiter, AuditService writer) {
        AuthApi identity = mock(AuthApi.class);
        when(identity.currentUser()).thenReturn(new CurrentUser(caller, Set.of(RoleCode.LEARNER), false));
        var service = new ReferenceReportService(identity, named, limiter, writer, Clock.fixed(now, ZoneOffset.UTC));
        new TransactionTemplate(manager).executeWithoutResult(status -> service.report(Long.toString(target),
                new ReportCommand(Reason.OTHER, "Ghi chu tu soan")));
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Tu soan");
    }

    private long item(String status) {
        long id = jdbc.queryForObject("INSERT INTO learning.reference_items(kind,title,url,status,created_by,published_at) "
                        + "VALUES ('BOOK','Demo','https://fahasa.com/demo',?,?,?) RETURNING id", Long.class,
                status, user, status.equals("PUBLISHED") ? Timestamp.from(NOW) : null);
        items.add(id);
        return id;
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id IN (?,?) "
                + "AND action='REFERENCE_REPORTED'", Long.class, user, otherUser);
    }

    private String key(long caller, long target, Instant now) {
        return "reference:report:" + caller + ":" + target + ":" + DateTimeFormatter.BASIC_ISO_DATE
                .format(now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
    }
}
