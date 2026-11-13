package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.cnhsk.learning.credit.LedgerService.LedgerEntry;
import com.cnhsk.learning.credit.web.Transaction;
import com.cnhsk.learning.credit.web.TransactionPage;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CreditTransactionHistoryIntegrationTest extends AbstractIntegrationTest {
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final Instant CREATED = Instant.parse("2031-11-15T10:00:00Z");
    private static final Set<String> TRANSACTION_KEYS = Set.of("seq", "type", "amount", "balanceBefore", "balanceAfter", "featureCode", "reason", "createdAt");
    private static final String HISTORY = "/api/me/credit-transactions";
    @Autowired private WalletQueryService queries;
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
    private long otherUserId;
    private int createdCount;
    private List<Map<String, Object>> plansBefore;

    @BeforeEach void setUp() {
        plansBefore = jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code");
        userId = createUser();
        otherUserId = createUser();
        var identity = new AuthenticatedUser(userId, Set.of("LEARNER"), false, UUID.randomUUID().toString(), UUID.randomUUID(), CREATED);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
    }

    @AfterEach void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id IN (?, ?)", userId, otherUserId);
        jdbc.update("DELETE FROM auth.users WHERE id IN (?, ?)", userId, otherUserId);
        assertThat(jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code")).isEqualTo(plansBefore);
    }

    @Test void AT13_twentyFiveOwnedTransactionsUseSequenceDescendingRegardlessOfTimestampAndPreserveBeyondLastTotal() {
        seedTwentyFive();
        append(otherUserId, "TOPUP", 999, null, null, "Giao dịch người khác", null);
        Snapshot before = snapshot();
        TransactionPage first = queries.history(0, 20);
        TransactionPage second = queries.history(1, 20);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(20);
        assertThat(first.total()).isEqualTo(25);
        assertThat(first.items()).extracting(Transaction::seq).containsExactlyElementsOf(descending(25, 6));
        assertThat(second.total()).isEqualTo(25);
        assertThat(second.items()).extracting(Transaction::seq).containsExactly(5, 4, 3, 2, 1);
        assertThat(first.items().get(0).createdAt()).isBefore(second.items().get(4).createdAt());
        assertThat(queries.history(2, 20).items()).isEmpty();
        assertThat(queries.history(2, 20).total()).isEqualTo(25);
        assertThat(queries.history(0, 1).items()).extracting(Transaction::seq).containsExactly(25);
        assertThat(queries.history(0, 100).items()).hasSize(25);
        for (int size : List.of(1, 100)) {
            TransactionPage distant = queries.history(Integer.MAX_VALUE, size);
            assertThat(distant.items()).isEmpty();
            assertThat(distant.total()).isEqualTo(25);
            assertThat(distant.page()).isEqualTo(Integer.MAX_VALUE);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void emptyOwnedHistoryStaysEmptyWithZeroTotalWhenAnotherUserHasRows() {
        append(otherUserId, "TOPUP", 99, null, null, null, null);
        Snapshot before = snapshot();
        TransactionPage page = queries.history(0, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void sixStoredTypesKeepAmountsBalancesTimesNullableFeatureAndPublicReasonsWithExactlyEightJsonKeys() throws Exception {
        append(userId, "TOPUP", 100, null, null, null, null);
        LedgerEntry debit = append(userId, "DEDUCT", -5, "TRANSLATE", null, "Luyện dịch", null);
        append(userId, "REFUND", 5, "TRANSLATE", debit.id(), "Hoàn lượt dịch", null);
        String subscriptionReason = "PREMIUM +30 ngày, giá niêm yết 0.00";
        append(userId, "SUBSCRIPTION", 0, null, null, subscriptionReason, null);
        String publicReason = "Bổ sung điểm hỗ trợ học tập";
        append(userId, "ADJUSTMENT", 2, null, null, publicReason, otherUserId);
        append(userId, "TEACHER_PAYOUT", 3, "GRADING", null, "Chấm bài", null);
        Snapshot before = snapshot();
        JsonNode response = historyJson();
        assertThat(response.path("total").longValue()).isEqualTo(6);
        assertThat(response.path("items").size()).isEqualTo(6);
        Set<String> observedTypes = new LinkedHashSet<>();
        for (JsonNode item : response.path("items")) {
            assertThat(fields(item)).isEqualTo(TRANSACTION_KEYS);
            assertThat(item.path("seq").isIntegralNumber()).isTrue();
            assertThat(item.path("amount").isIntegralNumber()).isTrue();
            assertThat(item.path("balanceBefore").isIntegralNumber()).isTrue();
            assertThat(item.path("balanceAfter").intValue()).isEqualTo(item.path("balanceBefore").intValue() + item.path("amount").intValue());
            int seq = item.path("seq").intValue();
            assertThat(Instant.parse(item.path("createdAt").textValue())).isEqualTo(CREATED.minusSeconds(seq));
            observedTypes.add(item.path("type").textValue());
            switch (item.path("type").textValue()) {
                case "TOPUP" -> { assertThat(item.path("amount").intValue()).isEqualTo(100); assertThat(item.path("featureCode").isNull()).isTrue(); assertThat(item.path("reason").isNull()).isTrue(); }
                case "DEDUCT" -> { assertThat(item.path("amount").intValue()).isEqualTo(-5); assertThat(item.path("featureCode").textValue()).isEqualTo("TRANSLATE"); }
                case "REFUND" -> { assertThat(item.path("amount").intValue()).isEqualTo(5); assertThat(item.path("featureCode").textValue()).isEqualTo("TRANSLATE"); }
                case "SUBSCRIPTION" -> { assertThat(item.path("amount").intValue()).isZero(); assertThat(item.path("reason").textValue()).isEqualTo(subscriptionReason); assertThat(item.path("featureCode").isNull()).isTrue(); }
                case "ADJUSTMENT" -> assertThat(item.path("reason").textValue()).isEqualTo(publicReason);
                case "TEACHER_PAYOUT" -> assertThat(item.path("featureCode").textValue()).isEqualTo("GRADING");
                default -> throw new AssertionError("Unexpected transaction type.");
            }
        }
        assertThat(observedTypes).containsExactlyInAnyOrder("TOPUP", "DEDUCT", "REFUND", "ADJUSTMENT", "TEACHER_PAYOUT", "SUBSCRIPTION");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void unsafeSyntheticLegacyReasonsBecomeExplicitNullWithoutLeakingCardMaterialPiiOrTokens() throws Exception {
        List<String> unsafe = List.of("ABCD1234EFGH5678", "ABCD-1234-EFGH-5678", "O1IL2345ABCD6789",
                "A B C D 1 2 3 4 E F G H 5 6 7 8", "prefixABCD1234EFGH5678suffix", "a".repeat(64),
                "prefix" + "b".repeat(64) + "suffix", "legacy@example.test", "+84901234567",
                "eyJmb28.eyJiYXI.signature", "password: synthetic", "secret=synthetic", "token: synthetic", "api-key=synthetic");
        for (String reason : unsafe) append(userId, "TOPUP", 1, null, null, reason, null);
        Snapshot before = snapshot();
        JsonNode response = historyJson();
        assertThat(response.path("items").size()).isEqualTo(unsafe.size());
        for (JsonNode item : response.path("items")) {
            assertThat(fields(item)).isEqualTo(TRANSACTION_KEYS);
            assertThat(item.path("reason").isNull()).isTrue();
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void actualGetUsesOnlyJwtOwnerAndIgnoresClientUserFilterAndSortWithoutWritingState() throws Exception {
        seedTwentyFive();
        append(otherUserId, "TOPUP", 777, null, null, null, null);
        Snapshot before = snapshot();
        JsonNode response = mapper.readTree(mvc.perform(get(HISTORY).header("Authorization", bearer(userId))
                .param("userId", Long.toString(otherUserId)).param("filter", "REFUND").param("sort", "seq,asc"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(response.path("total").longValue()).isEqualTo(25);
        assertThat(response.path("items").size()).isEqualTo(20);
        assertThat(response.path("items").get(0).path("seq").intValue()).isEqualTo(25);
        for (JsonNode item : response.path("items")) assertThat(item.path("amount").intValue()).isEqualTo(1);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void signedButMissingAuthUserReturnsUnauthenticatedForBothActualGets() throws Exception {
        for (String route : List.of("/api/me/credits", HISTORY)) {
            mvc.perform(get(route).header("Authorization", bearer(Long.MAX_VALUE)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.request_id").isString());
        }
    }

    @Test void historyCountAndItemsShareRepeatableReadWhenAWriterCommitsBetweenThem() throws Exception {
        seedTwentyFive();
        var writer = Executors.newSingleThreadExecutor();
        NamedParameterJdbcTemplate observed = mock(NamedParameterJdbcTemplate.class, delegatesTo(namedJdbc));
        doAnswer(invocation -> {
            Long total = namedJdbc.queryForObject(invocation.getArgument(0, String.class),
                    invocation.getArgument(1, SqlParameterSource.class), Long.class);
            writer.submit(() -> append(userId, "TOPUP", 1, null, null, null, null)).get(20, TimeUnit.SECONDS);
            return total;
        }).when(observed).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));
        try {
            TransactionPage result = service(auth, observed).history(0, 100);
            assertThat(result.total()).isEqualTo(25);
            assertThat(result.items()).hasSize(25).extracting(Transaction::seq).containsExactlyElementsOf(descending(25, 1));
            assertThat(queries.history(0, 100).total()).isEqualTo(26);
        } finally { writer.shutdownNow(); assertThat(writer.awaitTermination(20, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void pageSizesOneAndOneHundredEachUseOneAuthReadOneCountAndOnePageSelect() {
        seedTwentyFive();
        AuthApi observedAuth = mock(AuthApi.class, delegatesTo(auth));
        NamedParameterJdbcTemplate observed = mock(NamedParameterJdbcTemplate.class, delegatesTo(namedJdbc));
        WalletQueryService service = service(observedAuth, observed);
        assertThat(service.history(0, 1).items()).hasSize(1);
        assertThat(service.history(0, 100).items()).hasSize(25);
        verify(observedAuth, times(2)).readPlan(userId);
        verify(observed, times(2)).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));
        verify(observed, times(2)).query(anyString(), any(SqlParameterSource.class), org.mockito.ArgumentMatchers.<RowMapper<?>>any());
    }

    private WalletQueryService service(AuthApi api, NamedParameterJdbcTemplate queryJdbc) {
        ProxyFactory proxy = new ProxyFactory(new WalletQueryService(api, ledger, queryJdbc, mapper, new BillingConfig(), Clock.systemUTC()));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (WalletQueryService) proxy.getProxy();
    }
    private void seedTwentyFive() { for (int row = 0; row < 25; row++) append(userId, "TOPUP", 1, null, null, null, null); }
    private LedgerEntry append(long owner, String type, int amount, String feature, Long reference, String reason, Long performer) {
        LedgerService writer = new LedgerService(auth, namedJdbc, Clock.fixed(CREATED.minusSeconds(++createdCount), ZoneOffset.UTC));
        return new TransactionTemplate(manager).execute(status -> writer.append(
                new AppendCommand(owner, type, amount, feature, null, null, reference, reason, performer, null)));
    }
    private long createUser() {
        return jdbc.queryForObject("INSERT INTO auth.users(email, password_hash, display_name) VALUES (?, ?, 'Lịch sử ví') RETURNING id",
                Long.class, "history-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH);
    }
    private String bearer(long owner) { return "Bearer " + jwt.issueAccessToken(owner, List.of("LEARNER"), false, UUID.randomUUID()); }
    private JsonNode historyJson() throws Exception {
        return mapper.readTree(mvc.perform(get(HISTORY).header("Authorization", bearer(userId)).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private static Set<String> fields(JsonNode node) { Set<String> names = new LinkedHashSet<>(); node.fieldNames().forEachRemaining(names::add); return names; }
    private static List<Integer> descending(int highest, int lowest) { return IntStream.rangeClosed(lowest, highest).map(value -> highest + lowest - value).boxed().toList(); }
    private Snapshot snapshot() {
        return new Snapshot(jdbc.queryForList("SELECT * FROM auth.users WHERE id IN (?, ?) ORDER BY id", userId, otherUserId),
                jdbc.queryForList("SELECT * FROM learning.credit_transactions WHERE user_id IN (?, ?) ORDER BY user_id, seq", userId, otherUserId),
                Set.copyOf(redis.keys("billing:*:" + userId + ":*")), jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs", Long.class));
    }
    private record Snapshot(List<Map<String, Object>> users, List<Map<String, Object>> ledger, Set<String> redisKeys, long audits) {}
}
