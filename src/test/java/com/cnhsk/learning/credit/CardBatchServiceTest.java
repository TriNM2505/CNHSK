package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.credit.web.CreateBatchRequest;
import com.cnhsk.learning.credit.web.CreateBatchRequest.CardType;
import com.cnhsk.learning.credit.web.CreateBatchRequest.Channel;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class CardBatchServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
    private static final String BATCH = "B20261107-000001";
    private final AuthApi auth = mock(AuthApi.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private CardCodes codes;

    @BeforeEach
    void adminIdentity() {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.ADMIN), true));
        BillingConfig config = new BillingConfig();
        config.setCardCodePepper(UUID.randomUUID().toString());
        codes = new CardCodes(config, clock);
    }

    @Test
    void nullRequestFailsBeforeDatabaseOrAudit() {
        expectError(() -> service(() -> raw(1), () -> BATCH).create(null), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(jdbc, audit);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void validatesTheFullRequestBeforeDatabaseOrAudit(CreateBatchRequest request) {
        expectError(() -> service(() -> raw(1), () -> BATCH).create(request), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(jdbc, audit);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                request(null, 1, null, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.CREDIT, 1, null, 1, null, null, null, null, null),
                request(CardType.CREDIT, 1, null, null, Channel.DIRECT, null, null, null, null),
                request(CardType.CREDIT, 1, null, 0, Channel.DIRECT, null, null, null, null),
                request(CardType.CREDIT, 1, null, 101, Channel.DIRECT, null, null, null, null),
                request(CardType.CREDIT, null, null, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.CREDIT, 0, null, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.CREDIT, 10001, null, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.CREDIT, 1, 1, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.SUBSCRIPTION, null, null, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.SUBSCRIPTION, null, 0, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.SUBSCRIPTION, null, 366, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.SUBSCRIPTION, 1, 30, 1, Channel.DIRECT, null, null, null, null),
                request(CardType.CREDIT, 1, null, 1, Channel.PARTNER_BATCH, "  ", null, null, null),
                request(CardType.CREDIT, 1, null, 1, Channel.ECOSYSTEM_GIFT, null, null, "  ", null),
                request(CardType.CREDIT, 1, null, 1, Channel.DIRECT, "P".repeat(51), null, null, null),
                request(CardType.CREDIT, 1, null, 1, Channel.DIRECT, null, "C".repeat(51), null, null),
                request(CardType.CREDIT, 1, null, 1, Channel.DIRECT, null, null, "R".repeat(256), null),
                request(CardType.CREDIT, 1, null, 1, Channel.DIRECT, "bad\0input", null, null, null),
                request(CardType.CREDIT, 1, null, 1, Channel.DIRECT, null, null, null, NOW),
                request(CardType.CREDIT, 1, null, 1, Channel.DIRECT, null, null, null, NOW.minusNanos(1)))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "ACCOUNTANT"})
    void directServiceCallsAlsoRequireAdmin(String role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(42, Set.of(RoleCode.valueOf(role)), true));
        CardBatchService service = service(() -> raw(1), () -> BATCH);
        expectError(() -> service.create(credit(1)), ErrorCode.FORBIDDEN);
        expectError(() -> service.list(0, 20), ErrorCode.FORBIDDEN);
        expectError(() -> service.detail(BATCH), ErrorCode.FORBIDDEN);
        expectError(() -> service.voidBatch(BATCH), ErrorCode.FORBIDDEN);
        verifyNoInteractions(jdbc, audit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void missingOrInactivePremiumIsRejected(boolean missing) {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(missing ? List.of() : List.of(false));
        CreateBatchRequest request = request(CardType.SUBSCRIPTION, null, 30, 1, Channel.DIRECT, null, null, null, null);
        expectError(() -> service(() -> raw(1), () -> BATCH).create(request), ErrorCode.PLAN_NOT_AVAILABLE);
        verify(jdbc, never()).queryForObject(anyString(), anyMap(), eq(Boolean.class));
        verifyNoInteractions(audit);
    }

    @Test
    void duplicateCandidatesAreRegeneratedWithoutReducingQuantity() {
        availableBatch();
        echoInsertedHashes();
        AtomicInteger generated = new AtomicInteger();
        Supplier<String> source = () -> generated.incrementAndGet() <= 2 ? raw(1) : raw(2);
        var result = service(source, () -> BATCH).create(credit(2));
        assertThat(result.codes()).containsExactly(codes.format(raw(1)), codes.format(raw(2)));
        assertThat(generated).hasValue(3);
        verify(jdbc, times(2)).queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class));
    }

    @Test
    void persistedHashCollisionsRegenerateOnlyMissingCodes() {
        availableBatch();
        AtomicInteger inserts = new AtomicInteger();
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class))).thenAnswer(invocation -> {
            MapSqlParameterSource parameters = invocation.getArgument(1);
            return inserts.incrementAndGet() == 1 ? List.of(parameters.getValue("hash0")) : hashes(parameters);
        });
        AtomicInteger generated = new AtomicInteger();
        var result = service(() -> raw(generated.incrementAndGet()), () -> BATCH).create(credit(2));
        assertThat(result.codes()).containsExactly(codes.format(raw(1)), codes.format(raw(3)));
        assertThat(generated).hasValue(3);
        assertThat(inserts).hasValue(2);
    }

    @Test
    void persistedCollisionCanRecoverOnTheLastAllowedRetry() {
        availableBatch();
        AtomicInteger inserts = new AtomicInteger();
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class))).thenAnswer(invocation ->
                inserts.incrementAndGet() == 6 ? hashes(invocation.getArgument(1)) : List.of());
        AtomicInteger generated = new AtomicInteger();
        var result = service(() -> raw(generated.incrementAndGet()), () -> BATCH).create(credit(1));
        assertThat(result.codes()).containsExactly(codes.format(raw(6)));
        assertThat(generated).hasValue(6);
        assertThat(inserts).hasValue(6);
    }

    @Test
    void persistedCollisionRetriesAreBoundedAndDoNotAuditPartialCreation() {
        availableBatch();
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class))).thenReturn(List.of());
        AtomicInteger generated = new AtomicInteger();
        expectError(() -> service(() -> raw(generated.incrementAndGet()), () -> BATCH).create(credit(1)), ErrorCode.INTERNAL_ERROR);
        assertThat(generated).hasValue(6);
        verify(jdbc, times(6)).queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class));
        verifyNoInteractions(audit);
    }

    @Test
    void repeatedInMemoryDuplicatesCannotLoopForever() {
        availableBatch();
        echoInsertedHashes();
        AtomicInteger generated = new AtomicInteger();
        expectError(() -> service(() -> { generated.incrementAndGet(); return raw(1); }, () -> BATCH)
                .create(credit(2)), ErrorCode.INTERNAL_ERROR);
        assertThat(generated).hasValue(7);
        verify(jdbc).queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class));
        verifyNoInteractions(audit);
    }

    @Test
    void batchIdentifierCollisionCanRecoverOnTheLastAllowedRetry() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class)))
                .thenReturn(true, true, true, true, true, false);
        echoInsertedHashes();
        AtomicInteger batches = new AtomicInteger();
        var result = service(() -> raw(1), () -> "B20261107-00000" + batches.incrementAndGet()).create(credit(1));
        assertThat(result.batchId()).isEqualTo("B20261107-000006");
        assertThat(batches).hasValue(6);
    }

    @Test
    void batchIdentifierCollisionsHaveAFiniteRetryBudget() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class))).thenReturn(true);
        AtomicInteger batches = new AtomicInteger();
        expectError(() -> service(() -> raw(1), () -> { batches.incrementAndGet(); return BATCH; })
                .create(credit(1)), ErrorCode.INTERNAL_ERROR);
        assertThat(batches).hasValue(6);
        verify(jdbc, never()).queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class));
        verifyNoInteractions(audit);
    }

    @Test
    void databaseFailureDoesNotExposeCauseHashOrRawCode() {
        availableBatch();
        String confidential = raw(1) + " " + codes.hash(raw(1));
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenThrow(new DataIntegrityViolationException(confidential));
        assertThatThrownBy(() -> service(() -> raw(1), () -> BATCH).create(credit(1)))
                .isInstanceOf(ApiException.class).hasNoCause().satisfies(failure -> {
                    assertThat(((ApiException) failure).getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
                    assertThat(failure.getMessage().contains(raw(1))).isFalse();
                    assertThat(failure.getMessage().contains(codes.hash(raw(1)))).isFalse();
                });
        verifyNoInteractions(audit);
    }

    @Test
    void subscriptionUsesPlanDaysForStorageAndNullCreditValueInAudit() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class))).thenReturn(List.of(true));
        availableBatch();
        echoInsertedHashes();
        CreateBatchRequest request = request(CardType.SUBSCRIPTION, null, 365, 1, Channel.ECOSYSTEM_GIFT,
                null, "Synthetic campaign", "Synthetic recipient", NOW.plusSeconds(1));
        service(() -> raw(1), () -> BATCH).create(request);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(anyString(), parameters.capture(), eq(String.class));
        assertThat(parameters.getValue().getValue("value")).isEqualTo(365);
        assertThat(parameters.getValue().getValue("days")).isEqualTo(365);
        ArgumentCaptor<Map<String, ?>> details = mapCaptor();
        verify(audit).recordDetail(eq(42L), eq("CARD_BATCH_CREATE"), eq("CARD_BATCH"), details.capture());
        assertThat(details.getValue().get("value")).isNull();
        assertThat(details.getValue().get("planDays")).isEqualTo(365);
        assertThat(details.getValue().get("totalValue")).isNull();
    }

    @Test
    void maximumCreditBatchUsesOneInsertAndCorrectAuditTotal() {
        availableBatch();
        echoInsertedHashes();
        AtomicInteger generated = new AtomicInteger();
        var result = service(() -> raw(generated.incrementAndGet()), () -> BATCH).create(
                request(CardType.CREDIT, 10000, null, 100, Channel.PARTNER_BATCH, "Synthetic partner", null, null, null));
        assertThat(result.codes()).hasSize(100).doesNotHaveDuplicates();
        verify(jdbc).queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class));
        ArgumentCaptor<Map<String, ?>> details = mapCaptor();
        verify(audit).recordDetail(eq(42L), eq("CARD_BATCH_CREATE"), eq("CARD_BATCH"), details.capture());
        assertThat(details.getValue().get("value")).isEqualTo(10000);
        assertThat(details.getValue().get("planDays")).isNull();
        assertThat(details.getValue().get("totalValue")).isEqualTo(1_000_000);
    }

    @Test
    void voidAuditFailurePropagatesAsSafeInternalFailure() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class))).thenReturn(true);
        when(jdbc.update(anyString(), anyMap())).thenReturn(3);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit)
                .recordDetail(eq(42L), eq("CARD_BATCH_VOID"), eq("CARD_BATCH"), anyMap());
        expectError(() -> service(() -> raw(1), () -> BATCH).voidBatch(BATCH), ErrorCode.INTERNAL_ERROR);
    }

    @Test
    void listAndDetailRequireReadOnlyRepeatableReadSnapshots() throws Exception {
        for (String method : List.of("list", "detail")) {
            Transactional transaction = method.equals("list")
                    ? CardBatchService.class.getMethod(method, int.class, int.class).getAnnotation(Transactional.class)
                    : CardBatchService.class.getMethod(method, String.class).getAnnotation(Transactional.class);
            assertThat(transaction.readOnly()).isTrue();
            assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        }
    }

    private CardBatchService service(Supplier<String> raw, Supplier<String> batch) {
        return new CardBatchService(auth, jdbc, codes, clock, audit, raw, batch);
    }

    private void availableBatch() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Boolean.class))).thenReturn(false);
    }

    private void echoInsertedHashes() {
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenAnswer(invocation -> hashes(invocation.getArgument(1)));
    }

    private static List<String> hashes(MapSqlParameterSource parameters) {
        List<String> hashes = new ArrayList<>();
        parameters.getValues().forEach((key, value) -> { if (key.startsWith("hash")) hashes.add((String) value); });
        return hashes;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, ?>> mapCaptor() { return ArgumentCaptor.forClass((Class) Map.class); }

    private static void expectError(Runnable operation, ErrorCode expected) {
        assertThatThrownBy(operation::run).isInstanceOf(ApiException.class).hasNoCause()
                .satisfies(failure -> assertThat(((ApiException) failure).getCode()).isEqualTo(expected));
    }

    private static String raw(int value) { return String.format("%016d", value); }

    private static CreateBatchRequest credit(int quantity) {
        return request(CardType.CREDIT, 20, null, quantity, Channel.DIRECT, null, null, null, null);
    }

    private static CreateBatchRequest request(CardType type, Integer value, Integer days, Integer quantity,
            Channel channel, String partner, String campaign, String issued, Instant expires) {
        return new CreateBatchRequest(type, value, days, quantity, channel, partner, campaign, issued, expires);
    }
}
