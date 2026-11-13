package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.cnhsk.learning.credit.web.QuotaLine;
import com.cnhsk.learning.credit.web.Wallet;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class WalletQueryIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Instant NOW = Instant.parse("2031-11-15T10:00:00Z");
    private static final Instant MONTH = Instant.parse("2031-10-31T17:00:00Z");
    @Autowired private AuthApi auth;
    @Autowired private LedgerService ledger;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JwtService jwt;
    @Autowired private MockMvc mvc;
    private long userId;
    private boolean ownedPremium;
    private List<Map<String, Object>> plansBefore;

    @BeforeEach void setUp() {
        plansBefore = plans();
        ownedPremium = jdbc.update("""
                INSERT INTO learning.plans(code, name, duration_days) VALUES ('PREMIUM', 'Ví thử nghiệm', 30)
                ON CONFLICT (code) DO NOTHING
                """) == 1;
        userId = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name)
                VALUES (?, ?, 'Ví thử nghiệm') RETURNING id
                """, Long.class, "wallet-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
        usage(Map.of(), MONTH);
        principal();
    }

    @AfterEach void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM auth.users WHERE id = ?", userId);
        if (ownedPremium) jdbc.update("DELETE FROM learning.plans WHERE code = 'PREMIUM'");
        for (Map<String, Object> row : plansBefore) {
            jdbc.update("UPDATE learning.plans SET benefits = CAST(? AS jsonb), is_active = ? WHERE code = ?",
                    row.get("benefits").toString(), row.get("is_active"), row.get("code"));
        }
        assertThat(plans()).isEqualTo(plansBefore);
    }

    @Test void AT12_currentFreeQuotaUsesConfiguredTenForExactlyFourFeaturesAndNoLedgerMeansZero() {
        jdbc.update("UPDATE learning.plans SET benefits = '{\"TRANSLATE\":999}'::jsonb WHERE code = 'FREE'");
        usage(Map.of("TRANSLATE", 3), MONTH);
        Snapshot before = snapshot();
        Wallet wallet = serviceAt(NOW).wallet();
        assertThat(wallet.balance()).isZero();
        assertThat(wallet.plan().code().name()).isEqualTo("FREE");
        assertThat(wallet.plan().expiresAt()).isNull();
        assertThat(wallet.plan().active()).isTrue();
        assertThat(wallet.quota()).extracting(QuotaLine::feature).containsExactly(PaidFeature.values());
        assertThat(wallet.quota()).allSatisfy(line -> assertThat(line.limit()).isEqualTo(10));
        assertThat(quota(wallet, PaidFeature.TRANSLATE)).isEqualTo(new QuotaLine(PaidFeature.TRANSLATE, 10, 3, 7));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void AT12_staleUsageIsProjectedWithoutResettingAuthOrAppendingLedgerAcrossRepeatedReads() {
        usage(Map.of("TRANSLATE", 37, "GRADING", 8), Instant.parse("2031-09-30T17:00:00Z"));
        append(40);
        Snapshot before = snapshot();
        for (int read = 0; read < 3; read++) {
            Wallet result = serviceAt(NOW).wallet();
            assertThat(result.balance()).isEqualTo(40);
            assertThat(result.quota()).allSatisfy(line -> {
                assertThat(line.used()).isZero();
                assertThat(line.remaining()).isEqualTo(10);
            });
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void premiumMissingBenefitsAndInactiveCatalogDoNotRevokeAnUnexpiredGrant() {
        jdbc.update("UPDATE learning.plans SET benefits = '{\"GRADING\":5}'::jsonb, is_active = false WHERE code = 'PREMIUM'");
        plan(NOW.plusSeconds(1));
        usage(Map.of("TRANSLATE", 3), MONTH);
        Snapshot before = snapshot();
        Wallet result = serviceAt(NOW).wallet();
        assertThat(result.plan().code().name()).isEqualTo("PREMIUM");
        assertThat(result.plan().expiresAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(result.plan().active()).isTrue();
        assertThat(quota(result, PaidFeature.TRANSLATE)).isEqualTo(new QuotaLine(PaidFeature.TRANSLATE, 0, 3, 0));
        assertThat(quota(result, PaidFeature.GRADING).limit()).isEqualTo(5);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void expiredAndExactlyExpiredPlansUseFreeWithoutWritingDowngradeAndKeepActualUsedAboveLimit() {
        usage(Map.of("TRANSLATE", 37), MONTH);
        for (Instant expiry : List.of(NOW.minusSeconds(1), NOW)) {
            plan(expiry);
            Snapshot before = snapshot();
            Wallet result = serviceAt(NOW).wallet();
            assertThat(result.plan().code().name()).isEqualTo("FREE");
            assertThat(result.plan().expiresAt()).isNull();
            assertThat(quota(result, PaidFeature.TRANSLATE)).isEqualTo(new QuotaLine(PaidFeature.TRANSLATE, 10, 37, 0));
            assertThat(snapshot()).isEqualTo(before);
            assertThat(auth.readPlan(userId).orElseThrow().planCode()).isEqualTo("PREMIUM");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "2031-10-31T16:59:59Z,2031-10-31T17:00:00Z",
            "2031-10-31T17:00:00Z,2031-11-30T17:00:00Z",
            "2031-10-31T17:00:01Z,2031-11-30T17:00:00Z",
            "2031-12-31T16:59:59Z,2031-12-31T17:00:00Z",
            "2031-12-31T17:00:00Z,2032-01-31T17:00:00Z",
            "2032-02-28T17:00:00Z,2032-02-29T17:00:00Z"})
    void resetAtIsTheNextVietnameseMonthAtMidnightIncludingYearAndLeapBoundaries(String now, String expected) {
        Snapshot before = snapshot();
        assertThat(serviceAt(Instant.parse(now)).wallet().resetAt()).isEqualTo(Instant.parse(expected));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void walletKeepsOneRepeatableReadSnapshotWhenWriterCommitsAfterPlanSelect() throws Exception {
        usage(Map.of("TRANSLATE", 3), MONTH);
        append(10);
        var writer = Executors.newSingleThreadExecutor();
        AuthApi observed = mock(AuthApi.class, delegatesTo(auth));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            var state = auth.readPlan(userId);
            writer.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                auth.lockPlan(userId);
                auth.updateUsage(userId, Map.of("TRANSLATE", 9), MONTH);
                ledger.append(new AppendCommand(userId, "TOPUP", 20, null, null, null, null, null, null, null));
            })).get(20, TimeUnit.SECONDS);
            return state;
        }).when(observed).readPlan(userId);
        try {
            Wallet result = serviceAt(NOW, observed, ledger, namedJdbc).wallet();
            assertThat(result.balance()).isEqualTo(10);
            assertThat(quota(result, PaidFeature.TRANSLATE).used()).isEqualTo(3);
            assertThat(ledger.balance(userId)).isEqualTo(30);
            assertThat(auth.readPlan(userId).orElseThrow().freeUsage()).containsEntry("TRANSLATE", 9);
        } finally { writer.shutdownNow(); assertThat(writer.awaitTermination(20, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void balanceUsesNewestSequenceAndWalletMakesOneReadPerDependencyWithoutQuotaNPlusOne() {
        append(10);
        append(15);
        AuthApi observedAuth = mock(AuthApi.class, delegatesTo(auth));
        LedgerService observedLedger = mock(LedgerService.class, delegatesTo(ledger));
        NamedParameterJdbcTemplate observedJdbc = mock(NamedParameterJdbcTemplate.class, delegatesTo(namedJdbc));
        Snapshot before = snapshot();
        Wallet result = serviceAt(NOW, observedAuth, observedLedger, observedJdbc).wallet();
        assertThat(result.balance()).isEqualTo(25);
        assertThat(result.quota()).hasSize(4);
        verify(observedAuth, times(1)).readPlan(userId);
        verify(observedLedger, times(1)).balance(userId);
        verify(observedJdbc, times(1)).query(anyString(), org.mockito.ArgumentMatchers.<Map<String, ?>>any(),
                org.mockito.ArgumentMatchers.<RowMapper<?>>any());
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void NFR_P03_realWalletGetRecordsTwentyWarmupsAndEveryOneOfOneHundredSamplesWithoutTimingGate() throws Exception {
        String bearer = "Bearer " + jwt.issueAccessToken(userId, List.of("LEARNER"), false, UUID.randomUUID());
        Snapshot before = snapshot();
        for (int warmup = 0; warmup < 20; warmup++) {
            assertThat(mvc.perform(get("/api/me/credits").header("Authorization", bearer))
                    .andReturn().getResponse().getStatus()).isEqualTo(200);
        }
        List<Long> samples = new ArrayList<>();
        List<Integer> statuses = new ArrayList<>();
        for (int sample = 0; sample < 100; sample++) {
            long start = System.nanoTime();
            var response = mvc.perform(get("/api/me/credits").header("Authorization", bearer)).andReturn().getResponse();
            samples.add(System.nanoTime() - start);
            statuses.add(response.getStatus());
        }
        long[] sorted = samples.stream().mapToLong(Long::longValue).sorted().toArray();
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "wallet-query-evidence");
        Files.createDirectories(directory);
        mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("wallet-get-nfr-" + UUID.randomUUID() + ".json").toFile(),
                Map.of("warmups", 20, "samplesNanoseconds", samples, "p50Nanoseconds", sorted[49],
                        "p95Nanoseconds", sorted[94], "maxNanoseconds", sorted[99], "statuses", statuses,
                        "referenceBudgetNanoseconds", 100_000_000L));
        assertThat(samples).hasSize(100).allSatisfy(value -> assertThat(value).isPositive());
        assertThat(statuses).containsOnly(200);
        assertThat(snapshot()).isEqualTo(before);
    }

    private WalletQueryService serviceAt(Instant instant) { return serviceAt(instant, auth, ledger, namedJdbc); }
    private WalletQueryService serviceAt(Instant instant, AuthApi api, LedgerService ledgerService, NamedParameterJdbcTemplate queryJdbc) {
        var target = new WalletQueryService(api, ledgerService, queryJdbc, mapper, new BillingConfig(), Clock.fixed(instant, ZoneOffset.UTC));
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (WalletQueryService) proxy.getProxy();
    }
    private void principal() {
        var identity = new AuthenticatedUser(userId, Set.of("LEARNER"), false, UUID.randomUUID().toString(), UUID.randomUUID(), NOW);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
    }
    private void usage(Map<String, Integer> values, Instant month) {
        new TransactionTemplate(manager).executeWithoutResult(status -> { auth.lockPlan(userId); auth.updateUsage(userId, values, month); });
    }
    private void plan(Instant expiry) {
        new TransactionTemplate(manager).executeWithoutResult(status -> { auth.lockPlan(userId); auth.updatePlan(userId, "PREMIUM", expiry); });
    }
    private void append(int amount) {
        new TransactionTemplate(manager).executeWithoutResult(status -> ledger.append(
                new AppendCommand(userId, "TOPUP", amount, null, null, null, null, null, null, null)));
    }
    private static QuotaLine quota(Wallet wallet, PaidFeature feature) {
        return wallet.quota().stream().filter(line -> line.feature() == feature).findFirst().orElseThrow();
    }
    private List<Map<String, Object>> plans() { return jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code"); }
    private Snapshot snapshot() {
        return new Snapshot(jdbc.queryForMap("SELECT * FROM auth.users WHERE id = ?", userId),
                jdbc.queryForList("SELECT * FROM learning.credit_transactions WHERE user_id = ? ORDER BY seq", userId),
                Set.copyOf(redis.keys("billing:*:" + userId + ":*")), jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs", Long.class));
    }
    private record Snapshot(Map<String, Object> user, List<Map<String, Object>> ledger, Set<String> redisKeys, long audits) {}
}
