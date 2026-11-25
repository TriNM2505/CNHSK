package com.cnhsk.learning.aigen;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiReviewServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AuthApi auth = mock(AuthApi.class);
    private final AiReviewViewedStore viewed = mock(AiReviewViewedStore.class);
    private final AuditService audit = mock(AuditService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private AiReviewService service;

    @BeforeEach
    void setup() {
        when(auth.currentUser()).thenReturn(new CurrentUser(51, Set.of(RoleCode.TEACHER), true));
        service = new AiReviewService(jdbc, new ObjectMapper(), auth, viewed, audit, Clock.fixed(NOW, ZoneOffset.UTC), transactions);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"LEARNER", "MODERATOR", "ACCOUNTANT", "ADMIN"})
    void serviceRejectsEveryUnprivilegedRoleBeforeValidationOrStorage(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(51, Set.of(role), true));
        assertError(() -> service.list(null, 0, null, null, -1, 0), ErrorCode.FORBIDDEN);
        assertError(() -> service.approve(0), ErrorCode.FORBIDDEN);
        assertError(() -> service.reject(0, null), ErrorCode.FORBIDDEN);
        assertError(() -> service.bulkApprove(null), ErrorCode.FORBIDDEN);
        verifyNoInteractions(jdbc, viewed, audit, transactions);
    }

    @Test
    void contentEditorListsOnlyQuestionsAnotherTeacherAlreadyChecked() {
        when(auth.currentUser()).thenReturn(new CurrentUser(51, Set.of(RoleCode.CONTENT_EDITOR, RoleCode.ADMIN), true));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(List.of());
        when(viewed.viewed(51, List.of())).thenReturn(Set.of());
        service.list(null, null, null, null, 0, 20);
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("q.reviewed_by IS NOT NULL AND q.reviewed_by<>:me"),
                any(SqlParameterSource.class), eq(Long.class));
    }

    @Test
    void contentEditorCannotTakeTheTeacherStep() throws Exception {
        when(auth.currentUser()).thenReturn(new CurrentUser(51, Set.of(RoleCode.CONTENT_EDITOR), true));
        reviewRows("AI", "PENDING_REVIEW", true, null);
        assertError(() -> service.approve(5), ErrorCode.FORBIDDEN);
        assertError(() -> service.reject(5, "Sai nghĩa"), ErrorCode.FORBIDDEN);
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
        verifyNoInteractions(audit);
    }

    @Test
    void contentEditorPublishesAQuestionAnotherTeacherChecked() throws Exception {
        when(auth.currentUser()).thenReturn(new CurrentUser(51, Set.of(RoleCode.CONTENT_EDITOR), true));
        reviewRows("AI", "PENDING_REVIEW", true, 40L);
        assertThat(service.approve(5)).isEqualTo(new AiReviewService.ReviewResult(5, "APPROVED", NOW));
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("reviewed_by IS NOT DISTINCT FROM :checkedBy"), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo("APPROVED");
        assertThat(parameters.getValue().getValue("checkedBy")).isEqualTo(40L);
        verify(audit).record(eq(51L), eq("AI_QUESTION_APPROVE"), eq("question:5"), any(), any());
    }

    @Test
    void theTeacherWhoCheckedAQuestionCannotAlsoPublishIt() throws Exception {
        when(auth.currentUser()).thenReturn(new CurrentUser(51, Set.of(RoleCode.CONTENT_EDITOR, RoleCode.TEACHER), true));
        reviewRows("AI", "PENDING_REVIEW", true, 51L);
        assertError(() -> service.approve(5), ErrorCode.FORBIDDEN);
        verifyNoInteractions(audit);
    }

    @Test
    void aQuestionAnotherTeacherCheckedIsNoLongerPendingForATeacher() throws Exception {
        reviewRows("AI", "PENDING_REVIEW", true, 40L);
        assertError(() -> service.approve(5), ErrorCode.QUESTION_NOT_PENDING);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"TEACHER"})
    void allowedRolesCanReachTheQueueWithoutAnEmailVerificationRequirement(RoleCode role) throws Exception {
        when(auth.currentUser()).thenReturn(new CurrentUser(51, Set.of(role, RoleCode.ADMIN), false));
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(List.of());
        when(viewed.viewed(51, List.of())).thenReturn(Set.of());
        assertThat(service.list(null, null, null, null, 0, 20).items()).isEmpty();
        verifyNoInteractions(audit, transactions);
    }

    @Test
    void directServiceQueueValidationRejectsRangeAndPagingErrorsBeforeSql() {
        assertError(() -> service.list(null, null, null, null, -1, 20), ErrorCode.VALIDATION_FAILED);
        assertError(() -> service.list(null, null, null, null, 0, 0), ErrorCode.VALIDATION_FAILED);
        assertError(() -> service.list(null, null, null, null, 0, 51), ErrorCode.VALIDATION_FAILED);
        assertError(() -> service.list(null, 0, null, null, 0, 20), ErrorCode.VALIDATION_FAILED);
        assertError(() -> service.list(null, 10, null, null, 0, 20), ErrorCode.VALIDATION_FAILED);
        assertError(() -> service.list("x".repeat(21), null, null, null, 0, 20), ErrorCode.VALIDATION_FAILED);
        assertError(() -> service.list(null, null, NOW, NOW.minusSeconds(1), 0, 20), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(jdbc, viewed, audit, transactions);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u00a0\u2003"})
    void rejectionRequiresANonBlankReasonAtTheServiceBoundary(String reason) {
        assertError(() -> service.reject(5, reason), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(jdbc, audit, viewed);
    }

    @Test
    void rejectionCountsSupplementaryCharactersAndTrimsUnicodeSpaces() throws Exception {
        reviewRows("AI", "PENDING_REVIEW", true);
        assertError(() -> service.reject(5, "𠀀".repeat(1001)), ErrorCode.VALIDATION_FAILED);
        String reason = "𠀀".repeat(1000);
        assertThat(service.reject(5, "\u00a0 " + reason + " \u2003").status()).isEqualTo("REJECTED");
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(anyString(), parameters.capture());
        assertThat(parameters.getValue().getValue("reason")).isEqualTo(reason);
        assertThat(parameters.getValue().getValue("actor")).isEqualTo(51L);
        assertThat(parameters.getValue().getValue("now")).isEqualTo(NOW.atOffset(ZoneOffset.UTC));
        verify(audit).record(eq(51L), eq("AI_QUESTION_REJECT"), eq("question:5"), any(), any());
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void directBulkValidationPreventsStorageAccessForInvalidIds(List<Long> ids) {
        assertError(() -> service.bulkApprove(ids), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(jdbc, viewed, audit, transactions);
    }

    static Stream<List<Long>> invalidIds() {
        return Stream.of(null, List.of(), Arrays.asList((Long) null), List.of(0L), List.of(-1L), List.of(1L, 1L),
                java.util.stream.LongStream.rangeClosed(1, 51).boxed().toList());
    }

    @Test
    void bulkReadsMarkersOnceAndNeverQueriesOrBeginsATransactionForUnseenIds() {
        when(viewed.viewed(51, List.of(7L, 2L))).thenReturn(Set.of());
        assertThat(service.bulkApprove(List.of(7L, 2L)).results()).containsExactly(
                new AiReviewService.BulkItem(7, "NOT_VIEWED"), new AiReviewService.BulkItem(2, "NOT_VIEWED"));
        verify(viewed, times(1)).viewed(51, List.of(7L, 2L));
        verifyNoInteractions(jdbc, transactions, audit);
    }

    @Test
    void viewedBulkRowsUseOneRequiresNewTransactionAndOneAuditEach() throws Exception {
        reviewRows("AI", "PENDING_REVIEW", true);
        when(viewed.viewed(51, List.of(7L, 2L))).thenReturn(Set.of(2L, 7L));
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        var result = service.bulkApprove(List.of(7L, 2L));
        assertThat(result.results()).extracting(AiReviewService.BulkItem::outcome)
                .containsExactly("TEACHER_APPROVED", "TEACHER_APPROVED");
        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allSatisfy(value -> assertThat(value.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(transactions, times(2)).commit(any());
        verify(audit, times(2)).record(eq(51L), eq("AI_QUESTION_TEACHER_APPROVE"), anyString(), any(), any());
    }

    @Test
    void absentAndNonPendingQuestionsHaveDistinctErrorsAndCannotProduceAudit() throws Exception {
        reviewRows("IMPORT", "PENDING_REVIEW", true);
        assertError(() -> service.approve(5), ErrorCode.QUESTION_NOT_PENDING);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any())).thenReturn(List.of());
        assertError(() -> service.approve(5), ErrorCode.QUESTION_NOT_FOUND);
        assertError(() -> service.approve(0), ErrorCode.QUESTION_NOT_FOUND);
        verifyNoInteractions(audit, viewed);
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    void conditionalUpdateLosingThePendingStateReturnsConflictWithoutAudit() throws Exception {
        reviewRows("AI", "PENDING_REVIEW", true);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);
        assertError(() -> service.approve(5), ErrorCode.QUESTION_NOT_PENDING);
        verifyNoInteractions(audit);
    }

    @Test
    void approveLocksThenChecksPrimaryAndWritesTheConditionalTransition() throws Exception {
        reviewRows("AI", "PENDING_REVIEW", true);
        var result = service.approve(5);
        // The teacher step keeps the row pending and only records who checked it.
        assertThat(result).isEqualTo(new AiReviewService.ReviewResult(5, "TEACHER_APPROVED", NOW));
        var ordering = org.mockito.Mockito.inOrder(jdbc, audit);
        ordering.verify(jdbc).query(org.mockito.ArgumentMatchers.contains("FOR UPDATE"), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any());
        ordering.verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("role='PRIMARY'"), any(SqlParameterSource.class), eq(Long.class));
        ArgumentCaptor<SqlParameterSource> parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        ordering.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("source='AI' AND status='PENDING_REVIEW'"), parameters.capture());
        assertThat(parameters.getValue().getValue("status")).isEqualTo("PENDING_REVIEW");
        assertThat(parameters.getValue().getValue("actor")).isEqualTo(51L);
        ordering.verify(audit).record(eq(51L), eq("AI_QUESTION_TEACHER_APPROVE"), eq("question:5"), any(), any());
    }

    @Test
    void missingPrimaryReturnsMalformedWithDetailsAndNeverUpdates() throws Exception {
        reviewRows("AI", "PENDING_REVIEW", false);
        assertThatThrownBy(() -> service.approve(5)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.MALFORMED_QUESTION);
            assertThat(error.getExtra()).containsKey("details");
        });
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
        verifyNoInteractions(audit);
    }

    @Test
    void viewedStoreUsesOneMgetAndExactActorSpecificTwentyFourHourMarkers() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = operations(redis);
        AiReviewViewedStore store = new AiReviewViewedStore(redis);
        store.markViewed(51, 7);
        verify(operations).set("ai-review:viewed:51:7", "1", Duration.ofHours(24));
        when(operations.multiGet(List.of("ai-review:viewed:51:7", "ai-review:viewed:51:2", "ai-review:viewed:51:3")))
                .thenReturn(Arrays.asList("1", null, "unexpected"));
        assertThat(store.viewed(51, List.of(7L, 2L, 3L))).containsExactly(7L);
        verify(operations, times(1)).multiGet(any());
        assertThat(store.viewed(52, List.of())).isEmpty();
        verify(operations, times(1)).multiGet(any());
    }

    @Test
    void viewedStoreFailsClosedForEligibilityWhenRedisReadOrWriteFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> operations = operations(redis);
        AiReviewViewedStore store = new AiReviewViewedStore(redis);
        RedisConnectionFailureException outage = new RedisConnectionFailureException("Synthetic store outage");
        doThrow(outage).when(operations).set(anyString(), anyString(), any(Duration.class));
        when(operations.multiGet(any())).thenThrow(outage);
        store.markViewed(51, 7);
        assertThat(store.hasViewed(51, 7)).isFalse();
        assertThat(store.viewed(51, List.of(7L, 2L))).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> operations(StringRedisTemplate redis) {
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations); return operations;
    }

    private void reviewRows(String source, String state, boolean primary) throws Exception {
        reviewRows(source, state, primary, null);
    }

    /** {@code checkedBy} is the teacher who already checked the question, or null before the teacher step. */
    private void reviewRows(String source, String state, boolean primary, Long checkedBy) throws Exception {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(primary ? 1L : 0L);
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        when(jdbc.query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any())).thenAnswer(invocation -> {
            ResultSet rs = mock(ResultSet.class);
            SqlParameterSource parameters = invocation.getArgument(1);
            when(rs.getLong("id")).thenReturn((Long) parameters.getValue("id"));
            when(rs.getString("source")).thenReturn(source); when(rs.getString("status")).thenReturn(state);
            when(rs.getString("qtype")).thenReturn("MULTIPLE_CHOICE"); when(rs.getString("stem")).thenReturn("他是老师。");
            when(rs.getString("explanation")).thenReturn("Giải thích tự soạn");
            when(rs.getString("options")).thenReturn(AiReviewControllerIntegrationTest.Fixture.OPTIONS);
            when(rs.getString("correct_key")).thenReturn("A");
            when(rs.getObject("reviewed_by", Long.class)).thenReturn(checkedBy);
            RowMapper<?> mapper = invocation.getArgument(2); return List.of(mapper.mapRow(rs, 0));
        });
    }

    private static void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
