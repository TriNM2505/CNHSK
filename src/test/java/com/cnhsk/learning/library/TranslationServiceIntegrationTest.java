package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.Consumption.Source;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.library.web.TranslateRequest;
import com.cnhsk.learning.library.web.TranslateRequest.Language;
import com.cnhsk.learning.library.web.TranslateResult;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Import(TranslationServiceIntegrationTest.TimeConfiguration.class)
class TranslationServiceIntegrationTest extends AbstractIntegrationTest {
    static final Instant NOW = Instant.parse("2035-11-08T15:00:00Z");
    @Autowired private TranslationService service;
    @Autowired private ClaudeTranslator translator;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    @Autowired private Clock clock;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManagerFactory factory;
    @MockitoSpyBean private StubClaudeClient stub;
    @MockitoSpyBean private EntitlementApi entitlement;
    @MockitoSpyBean private Segmenter segmenter;
    private EntitlementApi entitlementTarget;
    private TranslationTestData data;
    private long user;
    private int calls;

    @BeforeEach
    void fixture() {
        data = new TranslationTestData(jdbc, redis, clock);
        user = data.user(0, 0);
        entitlementTarget = AopTestUtils.getUltimateTargetObject(entitlement);
        assertThat(mockingDetails(entitlementTarget).isSpy()).isTrue();
        calls = stub.callCount();
    }

    @AfterEach void cleanup() { data.cleanup(); }

    @Test
    void AT18_AT19_cacheContainsOnlyTranslationHasSevenDayTtlAndIsSharedWithoutBilling() throws Exception {
        var request = data.request("  我读书。 " + UUID.randomUUID() + "\n", Language.ZH);
        stub.enqueue(data.completion("Bản dịch tự soạn."));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(data.used(user)).isEqualTo(1);
            return invocation.callRealMethod();
        }).when(stub).complete(anyString(), anyList(), anyList(), eq(4096), eq(Duration.ofSeconds(10)));
        TranslateResult first = service.translate(user, request);
        assertThat(first.cached()).isFalse();
        assertThat(first.consumption()).isEqualTo(Source.QUOTA);
        assertThat(data.used(user)).isEqualTo(1);
        String key = data.key(request);
        assertThat(key).isEqualTo("translate:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(("ZH|VI|" + request.text()).getBytes(StandardCharsets.UTF_8))));
        var cached = mapper.readTree(redis.opsForValue().get(key));
        assertThat(cached.size()).isEqualTo(1);
        assertThat(cached.path("translation").textValue()).isEqualTo(first.translated());
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(604790L, 604800L);
        long other = data.user(10, 0);
        TranslateResult hit = service.translate(other, request);
        assertThat(hit.cached()).isTrue();
        assertThat(hit.consumption()).isNull();
        assertThat(hit.translated()).isEqualTo(first.translated());
        assertThat(data.used(other)).isEqualTo(10);
        assertThat(data.used(user)).isEqualTo(1);
        assertThat(stub.callCount() - calls).isEqualTo(1);
        verify(entitlementTarget, never()).consume(eq(other), any(), anyString());
    }

    @Test
    void AT19_noQuotaOrCreditsPreserves402AndNeverCallsProvider() {
        data.usage(user, 10);
        String before = data.usageJson(user);
        var request = data.uniqueRequest();
        assertThatThrownBy(() -> service.translate(user, request)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        });
        assertThat(data.usageJson(user)).isEqualTo(before);
        assertThat(stub.callCount() - calls).isZero();
        assertThat(redis.hasKey(data.key(request))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(AiUnavailableException.Reason.class)
    void AT19_allProviderFailuresRefundQuotaAndKeepTheErrorSanitized(AiUnavailableException.Reason reason) {
        var request = data.uniqueRequest();
        stub.enqueueFailure(reason);
        assertUnavailable(() -> service.translate(user, request));
        assertThat(data.used(user)).isZero();
        assertThat(stub.callCount() - calls).isEqualTo(1);
        assertThat(redis.hasKey(data.key(request))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"không phải JSON", "{\"translation\":\"\"}", "{\"translation\":null}", "[]"})
    void AT19_badProviderOutputRefundsWithoutCaching(String output) {
        var request = data.uniqueRequest();
        stub.enqueue(new ClaudeClient.Completion(output, 2, 3));
        assertUnavailable(() -> service.translate(user, request));
        assertThat(stub.callCount() - calls).isEqualTo(1);
        assertThat(data.used(user)).isZero();
        assertThat(redis.hasKey(data.key(request))).isFalse();
    }

    @Test
    void AT19_creditFailureWritesExactlyOneRefundReferencingTheDebit() {
        data.usage(user, 10);
        data.topup(user, 3);
        var request = data.uniqueRequest();
        stub.enqueueFailure(AiUnavailableException.Reason.TIMEOUT);
        assertUnavailable(() -> service.translate(user, request));
        assertThat(stub.callCount() - calls).isEqualTo(1);
        assertThat(data.transactions(user, "DEDUCT")).isEqualTo(1);
        assertThat(data.transactions(user, "REFUND")).isEqualTo(1);
        assertThat(data.balance(user)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM learning.credit_transactions r JOIN learning.credit_transactions d
                  ON r.ref_transaction_id=d.id WHERE r.user_id=? AND r.tx_type='REFUND'
                  AND r.amount=1 AND d.amount=-1 AND d.feature_code='TRANSLATE'
                """, Integer.class, user)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(Source.class)
    void accepted013Q1_retryAfterRefundInTheSameMinuteReturnsOldReceiptWithoutAnotherCharge(Source source) {
        if (source == Source.CREDIT) { data.usage(user, 10); data.topup(user, 3); }
        var request = data.uniqueRequest();
        stub.enqueueFailure(AiUnavailableException.Reason.TIMEOUT);
        assertUnavailable(() -> service.translate(user, request));
        stub.enqueue(data.completion("Thử lại thành công."));
        var retry = service.translate(user, request);
        assertThat(retry.cached()).isFalse();
        assertThat(retry.consumption()).isEqualTo(source);
        assertThat(data.used(user)).isEqualTo(source == Source.QUOTA ? 0 : 10);
        assertThat(data.transactions(user, "DEDUCT")).isEqualTo(source == Source.CREDIT ? 1 : 0);
        assertThat(data.transactions(user, "REFUND")).isEqualTo(source == Source.CREDIT ? 1 : 0);
        if (source == Source.CREDIT) assertThat(data.balance(user)).isEqualTo(3);
        assertThat(stub.callCount() - calls).isEqualTo(2);
        var hit = service.translate(user, request);
        assertThat(hit.cached()).isTrue();
        assertThat(hit.consumption()).isNull();
        assertThat(stub.callCount() - calls).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(Source.class)
    void concurrentCacheMissFailuresHaveOnlyOneEffectiveRefund(Source source) throws Exception {
        if (source == Source.CREDIT) { data.usage(user, 10); data.topup(user, 3); }
        var request = data.uniqueRequest();
        AtomicInteger quotaBeforeRefund = new AtomicInteger();
        CountDownLatch firstProviderEntered = new CountDownLatch(1);
        CyclicBarrier bothConsumed = new CyclicBarrier(2, () -> quotaBeforeRefund.set(data.used(user)));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            firstProviderEntered.countDown();
            try { bothConsumed.await(10, TimeUnit.SECONDS); }
            catch (Exception exception) { throw new IllegalStateException("Concurrent test barrier failed"); }
            return invocation.callRealMethod();
        }).when(stub).complete(anyString(), anyList(), anyList(), anyInt(), any(Duration.class));
        stub.enqueueFailure(AiUnavailableException.Reason.TIMEOUT);
        stub.enqueueFailure(AiUnavailableException.Reason.UPSTREAM_ERROR);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> assertUnavailable(() -> service.translate(user, request)));
            assertThat(firstProviderEntered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> assertUnavailable(() -> service.translate(user, request)));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(stub.callCount() - calls).isEqualTo(2);
        assertThat(data.used(user)).isEqualTo(source == Source.QUOTA ? 0 : 10);
        if (source == Source.QUOTA) assertThat(quotaBeforeRefund.get() - data.used(user)).isEqualTo(1);
        else {
            assertThat(data.transactions(user, "DEDUCT")).isEqualTo(1);
            assertThat(data.transactions(user, "REFUND")).isEqualTo(1);
            assertThat(data.balance(user)).isEqualTo(3);
        }
        assertThat(redis.hasKey(data.key(request))).isFalse();
    }

    @Test
    void AT20_longestMatchUsesTheChineseSourceAndPreservesNumbersAndPunctuation() {
        for (var word : List.of(new String[]{"我", "wǒ"}, new String[]{"爱", "ài"},
                new String[]{"我的", "wǒ de"}, new String[]{"家人", "jiā rén"})) data.lexeme(word[0], word[1]);
        var request = data.request("我爱我的家人 2026，HSK。", Language.ZH);
        stub.enqueue(data.completion("Tôi yêu gia đình mình."));
        var result = service.translate(user, request);
        assertThat(result.segments()).extracting(TranslateResult.Segment::text)
                .containsExactly("我", "爱", "我的", "家人", " 2026，HSK。");
        assertThat(result.segments().subList(0, 4)).allSatisfy(part -> {
            assertThat(part.lexemeId()).isNotNull(); assertThat(part.pinyin()).isNotBlank();
        });
        assertThat(result.segments().get(4).lexemeId()).isNull();
        assertThat(result.segments().get(4).pinyin()).isNull();
        assertThat(result.pinyin()).isEqualTo("wǒ ài wǒ de jiā rén");
        assertThat(stub.callCount() - calls).isEqualTo(1);
    }

    @Test
    void AT20_cacheHitRecalculatesDictionaryPinyinWithOneSelectAndNoProviderCall() {
        String chinese = "我" + new String(Character.toChars(0x24000 + Math.floorMod(UUID.randomUUID().hashCode(), 18000)));
        long lexeme = data.lexeme(chinese, "pinyin-before");
        var request = data.request("Nguồn tự soạn " + UUID.randomUUID(), Language.VI);
        stub.enqueue(data.completion(chinese + " 2026。"));
        assertThat(service.translate(user, request).pinyin()).isEqualTo("pinyin-before");
        jdbc.update("UPDATE learning.lexemes SET pinyin='pinyin-after' WHERE id=?", lexeme);
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var hit = service.translate(user, request);
            assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
            assertThat(hit.cached()).isTrue();
            assertThat(hit.pinyin()).isEqualTo("pinyin-after");
            assertThat(hit.segments().get(0).lexemeId()).isEqualTo(lexeme);
            assertThat(hit.segments().get(1).lexemeId()).isNull();
        } finally { statistics.setStatisticsEnabled(previous); }
        assertThat(stub.callCount() - calls).isEqualTo(1);
        assertThat(data.used(user)).isEqualTo(1);
    }

    @Test
    void AT20_segmenterFailureStillCachesTheTranslationWithoutRefund() {
        var request = data.uniqueRequest();
        doThrow(new IllegalStateException("private-segmentation-message")).when(segmenter).segment(anyString());
        stub.enqueue(data.completion("Bản dịch được giữ."));
        var result = service.translate(user, request);
        assertThat(result.translated()).isEqualTo("Bản dịch được giữ.");
        assertThat(result.segments()).isEmpty();
        assertThat(result.pinyin()).isNull();
        assertThat(data.used(user)).isEqualTo(1);
        assertThat(redis.hasKey(data.key(request))).isTrue();
        verify(entitlementTarget, never()).refund(eq(user), any());
        assertThat(stub.callCount() - calls).isEqualTo(1);
    }

    @Test
    void redisReadAndWriteFailuresAreBestEffortAndNeverRefundSuccessfulTranslation() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(broken.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisSystemException("private-redis-read", new IllegalStateException()));
        doThrow(new RedisSystemException("private-redis-write", new IllegalStateException()))
                .when(values).set(anyString(), anyString(), eq(Duration.ofDays(7)));
        var isolated = new TranslationService(entitlement, translator, segmenter, broken, mapper, clock, transactions);
        stub.enqueue(data.completion("Bản dịch khi cache lỗi."));
        var result = isolated.translate(user, data.uniqueRequest());
        assertThat(result.cached()).isFalse();
        assertThat(result.translated()).isEqualTo("Bản dịch khi cache lỗi.");
        assertThat(data.used(user)).isEqualTo(1);
        verify(entitlementTarget, never()).refund(eq(user), any());
        assertThat(stub.callCount() - calls).isEqualTo(1);
    }

    @Test
    void malformedCacheIsAMissAndAProviderFailureWithRefundFailureStillReturnsSanitized503() {
        var request = data.uniqueRequest();
        redis.opsForValue().set(data.key(request), "private-invalid-cache");
        stub.enqueueFailure(AiUnavailableException.Reason.TIMEOUT);
        doThrow(new IllegalStateException("private-refund-message")).when(entitlementTarget).refund(eq(user), any());
        assertUnavailable(() -> service.translate(user, request));
        assertThat(stub.callCount() - calls).isEqualTo(1);
        assertThat(data.used(user)).isEqualTo(1);
        assertThat(redis.opsForValue().get(data.key(request))).isEqualTo("private-invalid-cache");
    }

    @Test
    void NFR_P03_reportsStubCacheMissLatencyForTwentyWordInputsWithoutAHardTimingAssertion() {
        double[] samples = new double[20];
        for (int sample = 0; sample < samples.length; sample++) {
            long owner = data.user(0, 0);
            String input = "Hôm nay tôi cùng bạn đọc sách học tiếng Trung và viết lại những câu ngắn ở nhà mẫu " + UUID.randomUUID();
            assertThat(input.split(" ")).hasSize(20);
            var request = data.request(input, Language.VI);
            stub.enqueue(data.completion("我读书。"));
            long start = System.nanoTime();
            var result = service.translate(owner, request);
            samples[sample] = (System.nanoTime() - start) / 1_000_000.0;
            assertThat(result.cached()).isFalse();
            assertThat(result.consumption()).isEqualTo(Source.QUOTA);
        }
        double first = samples[0];
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT, "TRANSLATION_NFR_P03 samples=20 words=20 stub=true cache_miss=true first_ms=%.3f p50_ms=%.3f p95_ms=%.3f%n",
                first, samples[9], samples[18]);
        assertThat(stub.callCount() - calls).isEqualTo(20);
    }

    private void assertUnavailable(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.TRANSLATE_UNAVAILABLE);
            assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(error.getMessage()).isEqualTo(TranslationService.UNAVAILABLE_MESSAGE);
            assertThat(error.getCause()).isNull();
        });
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary Clock translationClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}

/** Owned, committed fixtures shared by the three T-14 integration classes. */
final class TranslationTestData {
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final List<Long> users = new ArrayList<>();
    private final List<Long> lexemes = new ArrayList<>();
    private final Set<String> cacheKeys = new HashSet<>();
    private final String budgetKey;
    private final String budgetBefore;
    private final Long budgetTtl;

    TranslationTestData(JdbcTemplate jdbc, StringRedisTemplate redis, Clock clock) {
        this.jdbc = jdbc; this.redis = redis; this.clock = clock;
        budgetKey = "ai:tokens:" + java.time.format.DateTimeFormatter.ofPattern("uuuuMM").format(clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        budgetBefore = redis.opsForValue().get(budgetKey);
        budgetTtl = redis.getExpire(budgetKey, TimeUnit.MILLISECONDS);
    }

    long user(int used, int credits) {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Translation fixture') RETURNING id",
                Long.class, "translation-" + UUID.randomUUID() + "@example.test", HASH);
        users.add(id);
        usage(id, used);
        if (credits > 0) topup(id, credits);
        return id;
    }

    void usage(long user, int used) {
        Instant month = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1).toLocalDate()
                .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        jdbc.update("UPDATE auth.users SET free_usage=cast(? AS jsonb),usage_reset_at=? WHERE id=?",
                "{\"TRANSLATE\":" + used + "}", Timestamp.from(month), user);
    }

    int used(long user) { return jdbc.queryForObject("SELECT COALESCE((free_usage->>'TRANSLATE')::int,0) FROM auth.users WHERE id=?", Integer.class, user); }
    String usageJson(long user) { return jdbc.queryForObject("SELECT free_usage::text FROM auth.users WHERE id=?", String.class, user); }
    void topup(long user, int credits) {
        jdbc.update("INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after) VALUES (?,1,'TOPUP',?,0,?)", user, credits, credits);
    }
    int balance(long user) { return jdbc.queryForObject("SELECT balance_after FROM learning.credit_transactions WHERE user_id=? ORDER BY seq DESC LIMIT 1", Integer.class, user); }
    int transactions(long user, String type) { return jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=? AND tx_type=?", Integer.class, user, type); }

    TranslateRequest request(String text, Language from) {
        var request = new TranslateRequest(text, from, from == Language.ZH ? Language.VI : Language.ZH);
        cacheKeys.add(key(request));
        return request;
    }
    TranslateRequest uniqueRequest() { return request("我读书。 " + UUID.randomUUID(), Language.ZH); }
    String key(TranslateRequest request) { return "translate:" + TranslationService.digest(request); }
    ClaudeClient.Completion completion(String translated) {
        try { return new ClaudeClient.Completion(new ObjectMapper().writeValueAsString(java.util.Map.of("translation", translated)), 2, 3); }
        catch (Exception exception) { throw new IllegalStateException("Fixture serialization failed"); }
    }

    long lexeme(String text, String pinyin) {
        var inserted = jdbc.queryForList("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi) VALUES ('WORD',?,?,'Mục tự soạn') ON CONFLICT(kind,text) DO NOTHING RETURNING id", Long.class, text, pinyin);
        lexemes.addAll(inserted);
        return inserted.isEmpty() ? jdbc.queryForObject("SELECT id FROM learning.lexemes WHERE kind='WORD' AND text=?", Long.class, text) : inserted.get(0);
    }

    void cleanup() {
        if (!cacheKeys.isEmpty()) redis.delete(cacheKeys);
        for (long user : users) {
            var keys = redis.keys("billing:*:" + user + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
            jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?", user);
            jdbc.update("DELETE FROM auth.users WHERE id=?", user);
        }
        for (long id : lexemes) jdbc.update("DELETE FROM learning.lexemes WHERE id=?", id);
        if (budgetBefore == null) redis.delete(budgetKey);
        else if (budgetTtl != null && budgetTtl > 0) redis.opsForValue().set(budgetKey, budgetBefore, Duration.ofMillis(budgetTtl));
        else redis.opsForValue().set(budgetKey, budgetBefore);
    }
}
