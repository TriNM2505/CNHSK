package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingKey.*;
import static com.cnhsk.shared.settings.SettingsAdminModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.cnhsk.shared.api.ActorNameLookup;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SettingsAdminServiceTest {
    private final SettingsAdminStore store = mock(SettingsAdminStore.class);
    private final AuditService audit = mock(AuditService.class);
    private final ActorNameLookup actors = mock(ActorNameLookup.class);
    private final SettingsService settings = spy(new SettingsService(mock(JdbcTemplate.class), SettingKeyTest.yamlDefaults(),
            Clock.systemUTC(), mock(PlatformTransactionManager.class)));
    private final Map<SettingKey, OverrideRow> rows = new EnumMap<>(SettingKey.class);
    private SettingsAdminService service;

    @BeforeEach
    void setup() {
        service = new SettingsAdminService(store, settings, actors, audit);
        when(store.overrides()).thenAnswer(call -> Map.copyOf(rows));
        when(actors.displayNames(anySet())).thenReturn(Map.of(42L, "Quản trị"));
        doAnswer(call -> { rows.put(call.getArgument(0), new OverrideRow(Integer.toString(call.getArgument(1)), call.getArgument(2), Instant.EPOCH)); return null; })
                .when(store).put(any(), anyInt(), anyLong());
        doAnswer(call -> { rows.remove(call.getArgument(0)); return null; }).when(store).reset(any());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void auditIdentifiersArePermanentExplicitUniqueAndPositive() {
        var expected = Map.ofEntries(Map.entry("billing.free-monthly-limit", 1L), Map.entry("assistant.daily-limit", 2L),
                Map.entry("ai.monthly-token-budget", 3L), Map.entry("library.guest-lookups-per-hour", 4L),
                Map.entry("library.authenticated-lookups-per-hour", 5L), Map.entry("library.max-notes", 6L),
                Map.entry("library.max-decks", 7L), Map.entry("library.max-cards", 8L), Map.entry("library.flashcard-reviews-per-hour", 9L),
                Map.entry("quiz.question-count", 10L), Map.entry("quiz.min-seconds-per-question", 11L),
                Map.entry("grading.claim-hours", 12L), Map.entry("grading.grading-hours", 13L), Map.entry("grading.teacher-payout-credits", 14L),
                Map.entry("grading.min-hanzi", 15L), Map.entry("grading.max-hanzi", 16L), Map.entry("grading.max-active-claims", 17L),
                Map.entry("contest.submit-grace-seconds", 18L), Map.entry("ai-generate.system-user-id", 19L),
                Map.entry("ai-generate.window-days", 20L), Map.entry("ai-generate.min-wrong", 21L),
                Map.entry("ai-generate.min-bank", 22L), Map.entry("ai-generate.batch-size", 23L),
                Map.entry("ai-generate.max-batches-per-run", 24L),
                Map.entry("learning.free-monthly-limit", 25L));
        assertThat(Arrays.stream(SettingKey.values()).collect(Collectors.toMap(SettingKey::key, SettingKey::auditId))).isEqualTo(expected);
        assertThat(Arrays.stream(SettingKey.values()).map(SettingKey::auditId).toList()).hasSize(25).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).isPositive());
    }

    @Test
    void listUsesDefaultsAndBatchesNamesWithoutReadingTheBusinessCache() {
        rows.put(BILLING_FREE_MONTHLY_LIMIT, new OverrideRow("30", 42, Instant.EPOCH));
        rows.put(ASSISTANT_DAILY_LIMIT, new OverrideRow("5", 42, Instant.EPOCH));
        var items = service.list();
        assertThat(items).hasSize(25).extracting(SettingItem::key).containsExactlyElementsOf(Arrays.stream(SettingKey.values()).map(SettingKey::key).toList());
        assertThat(items.get(0).currentValue()).isEqualTo(30);
        assertThat(items.get(0).updatedBy()).isEqualTo(new UserRef(42, "Quản trị"));
        assertThat(items.get(2).currentValue()).isEqualTo(2_000_000);
        assertThat(items.get(2).updatedBy()).isNull();
        assertThat(items.get(2).updatedAt()).isNull();
        assertThat(items).extracting(SettingItem::group).contains("Thanh toán", "Trợ lý & AI", "Thư viện", "Quiz", "Chấm bài", "Cuộc thi", "AI sinh câu", "Học tập");
        verify(actors).displayNames(Set.of(42L));
        verify(settings, never()).getInt(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "billing.card-code-pepper", "ai.api-key", "jwt.secret", "DB_PASSWORD", "BILLING.FREE-MONTHLY-LIMIT",
            "ai.generate-model", "ai-generate.cron", "ai-generate.timeout"})
    void unknownOrSensitiveKeysAreRejectedBeforeBodyAndDatabase(String key) {
        Supplier<SettingWrite> body = mock(Supplier.class);
        assertCode(() -> service.change(key, 42, body), ErrorCode.SETTING_NOT_FOUND);
        assertCode(() -> service.reset(key, 42, () -> { throw new AssertionError(); }), ErrorCode.SETTING_NOT_FOUND);
        assertCode(() -> service.history(key, 0, 20), ErrorCode.SETTING_NOT_FOUND);
        verifyNoInteractions(store, audit, actors, body);
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void everySettingRejectsValuesOutsideItsOwnBounds(SettingKey key, Integer value) {
        assertCode(() -> service.change(key.key(), 42, () -> new SettingWrite(value, "Lý do")), ErrorCode.SETTING_OUT_OF_RANGE);
        verifyNoInteractions(store, audit);
    }

    static Stream<Arguments> invalidValues() {
        return Arrays.stream(SettingKey.values()).flatMap(key -> Stream.of(Arguments.of(key, key.min() - 1),
                Arguments.of(key, key.max() + 1), Arguments.of(key, null)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void reasonMustBePresentAndNotBlank(String reason) {
        assertCode(() -> service.change(BILLING_FREE_MONTHLY_LIMIT.key(), 42, () -> new SettingWrite(12, reason)), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> service.reset(BILLING_FREE_MONTHLY_LIMIT.key(), 42, () -> new SettingReset(reason)), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(store, audit);
    }

    @Test
    void reasonLengthIsCheckedByCodePoint() {
        assertCode(() -> service.change(BILLING_FREE_MONTHLY_LIMIT.key(), 42, () -> new SettingWrite(12, "文".repeat(501))), ErrorCode.VALIDATION_FAILED);
        service.change(BILLING_FREE_MONTHLY_LIMIT.key(), 42, () -> new SettingWrite(12, "文".repeat(500)));
        verify(store).put(BILLING_FREE_MONTHLY_LIMIT, 12, 42);
    }

    @Test
    void writeAuditsTheEffectiveOldValueAndInvalidatesOnlyAfterCommit() {
        var item = service.change(BILLING_FREE_MONTHLY_LIMIT.key(), 42, () -> new SettingWrite(30, "Điều chỉnh hạn mức"));
        assertThat(item.currentValue()).isEqualTo(30);
        verify(store).lock();
        var detail = ArgumentCaptor.forClass(Map.class);
        verify(audit).recordDetail(eq(42L), eq("SETTING_CHANGE"), eq("SETTING"), eq(BILLING_FREE_MONTHLY_LIMIT.auditId()), detail.capture());
        assertThat(detail.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of("key", BILLING_FREE_MONTHLY_LIMIT.key(),
                "oldValue", 10, "newValue", 30, "reason", "Điều chỉnh hạn mức"));
        verify(settings, never()).invalidate(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(settings).invalidate(BILLING_FREE_MONTHLY_LIMIT);
    }

    @Test
    void sameValueAndRepeatedResetsStillProduceEveryAudit() {
        service.change(BILLING_FREE_MONTHLY_LIMIT.key(), 42, () -> new SettingWrite(10, "Giữ hạn mức"));
        service.reset(BILLING_FREE_MONTHLY_LIMIT.key(), 42, () -> new SettingReset("Về mặc định"));
        var second = service.reset(BILLING_FREE_MONTHLY_LIMIT.key(), 42, () -> new SettingReset("Xác nhận mặc định"));
        assertThat(second.currentValue()).isEqualTo(10);
        assertThat(second.overridden()).isFalse();
        assertThat(second.updatedBy()).isNull();
        verify(audit, times(3)).recordDetail(eq(42L), anyString(), eq("SETTING"), eq(BILLING_FREE_MONTHLY_LIMIT.auditId()), anyMap());
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(audit, times(2)).recordDetail(eq(42L), eq("SETTING_RESET"), eq("SETTING"), anyLong(), captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(detail -> assertThat(detail).containsEntry("oldValue", 10).containsEntry("newValue", null));
    }

    @Test
    void crossSettingValidationIncludesResetsAndUsesFreshRows() {
        assertCode(() -> service.change(GRADING_MIN_HANZI.key(), 42, () -> new SettingWrite(500, "Thử")), ErrorCode.SETTING_OUT_OF_RANGE);
        assertCode(() -> service.change(GRADING_MAX_HANZI.key(), 42, () -> new SettingWrite(50, "Thử")), ErrorCode.SETTING_OUT_OF_RANGE);
        rows.put(GRADING_MIN_HANZI, new OverrideRow("600", 42, Instant.EPOCH));
        rows.put(GRADING_MAX_HANZI, new OverrideRow("700", 42, Instant.EPOCH));
        assertCode(() -> service.reset(GRADING_MAX_HANZI.key(), 42, () -> new SettingReset("Thử")), ErrorCode.SETTING_OUT_OF_RANGE);
        verify(store, never()).put(any(), anyInt(), anyLong());
        verify(store, never()).reset(any());
        verifyNoInteractions(audit);
    }

    @Test
    void historyBatchesActorNamesAndKeepsResetNull() {
        when(store.history(BILLING_FREE_MONTHLY_LIMIT, 1, 2)).thenReturn(new AuditPage(
                List.of(new AuditRow(12, null, "Về mặc định", 42, Instant.EPOCH), new AuditRow(10, 12, "Đổi", 99, Instant.EPOCH)), 4));
        var page = service.history(BILLING_FREE_MONTHLY_LIMIT.key(), 1, 2);
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.items().get(0).newValue()).isNull();
        assertThat(page.items().get(1).actor()).isEqualTo(new UserRef(99, null));
        verify(actors).displayNames(Set.of(42L, 99L));
    }

    @Test
    void invalidPaginationIsRejectedBeforeQuerying() {
        for (int size : new int[]{0, 51}) assertCode(() -> service.history(BILLING_FREE_MONTHLY_LIMIT.key(), 0, size), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> service.history(BILLING_FREE_MONTHLY_LIMIT.key(), -1, 20), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(store);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
