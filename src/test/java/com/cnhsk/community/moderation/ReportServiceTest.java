package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.CommunityLimits;
import com.cnhsk.community.common.CommunityProperties;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.ReactionKind;
import com.cnhsk.community.common.ReportReason;
import com.cnhsk.community.common.TargetType;
import com.cnhsk.community.moderation.web.ReportReceived;
import com.cnhsk.community.moderation.web.ReportWrite;
import com.cnhsk.community.moderation.web.ReportWrite.ReportTargetType;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.community.reaction.ReactionId;
import com.cnhsk.community.reaction.ReactionRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

class ReportServiceTest {
    private static final long USER = 42L;
    private static final long AUTHOR = 99L;
    private static final long TARGET = 17L;
    private static final Instant NOW = Instant.parse("2026-11-05T16:59:59.123456789Z");
    private static final Instant STORED_NOW = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";
    private AuthApi auth;
    private PostRepository posts;
    private CommentRepository comments;
    private ReactionRepository reactions;
    private ModerationCaseRepository cases;
    private CommunityLimits limits;
    private NamedParameterJdbcTemplate jdbc;
    private ObjectMapper mapper;
    private CommunityLimits.Snapshot snapshot;
    private ReportService service;

    @BeforeEach
    void setUp() {
        auth = mock(AuthApi.class);
        posts = mock(PostRepository.class);
        comments = mock(CommentRepository.class);
        reactions = mock(ReactionRepository.class);
        cases = mock(ModerationCaseRepository.class);
        limits = mock(CommunityLimits.class);
        jdbc = mock(NamedParameterJdbcTemplate.class);
        mapper = new ObjectMapper().findAndRegisterModules();
        snapshot = new CommunityLimits(Clock.fixed(NOW, ZoneOffset.UTC), new CommunityProperties()).snapshot();
        when(auth.currentUser()).thenReturn(new CurrentUser(USER, Set.of(RoleCode.LEARNER), true));
        when(limits.snapshot()).thenReturn(snapshot);
        when(reactions.insertReport(eq(USER), anyString(), eq(TARGET), eq(STORED_NOW))).thenReturn(1);
        when(cases.upsertPendingReport(anyString(), eq(TARGET), anyString(), eq(STORED_NOW))).thenReturn(1);
        service = new ReportService(auth, posts, comments, reactions, cases, limits, jdbc, mapper);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "LEARNER", mode = EnumSource.Mode.EXCLUDE)
    void FR001_onlyLearnersPassTheRoleGateBeforeEmailOrDatabaseWork(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(USER, Set.of(role), true));

        assertError(() -> service.report(request(ReportTargetType.POST)), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);

        verify(auth, never()).requireVerifiedEmail(anyLong());
        verifyNoInteractions(posts, comments, reactions, cases, limits, jdbc);
    }

    @Test
    void FR001_authenticationFailurePreventsAllDatabaseAndClockWork() {
        when(auth.currentUser()).thenThrow(api(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED));

        assertError(() -> service.report(request(ReportTargetType.POST)), ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(posts, comments, reactions, cases, limits, jdbc);
    }

    @Test
    void FR002_verifiedEmailIsRequiredEvenIfTheCurrentUserClaimSaysVerified() {
        doThrow(api(ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN)).when(auth).requireVerifiedEmail(USER);

        assertError(() -> service.report(request(ReportTargetType.POST)), ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN);

        var order = inOrder(auth);
        order.verify(auth).currentUser();
        order.verify(auth).requireVerifiedEmail(USER);
        verifyNoInteractions(posts, comments, reactions, cases, limits, jdbc);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void FR033_missingTargetsStopBeforeLocksClockDuplicateOrQuotaChecks(ReportTargetType target) {
        if (target == ReportTargetType.POST) {
            when(posts.findPublished(TARGET, null, PageRequest.of(0, 1))).thenReturn(List.of());
        } else {
            when(comments.findReplyParentOnPublishedPost(TARGET)).thenReturn(Optional.empty());
        }

        assertError(() -> service.report(request(target)), ErrorCode.TARGET_NOT_FOUND, HttpStatus.NOT_FOUND);

        verifyNoInteractions(reactions, cases, limits, jdbc);
        assertNoTargetLocks();
    }

    @ParameterizedTest
    @EnumSource(value = CommentStatus.class, names = {"HIDDEN", "DELETED"})
    void FR033_nonvisibleCommentsAreNotReportableEvenOnAPublishedPost(CommentStatus status) {
        Comment comment = comment(AUTHOR);
        comment.setStatus(status);
        when(comments.findReplyParentOnPublishedPost(TARGET)).thenReturn(Optional.of(comment));

        assertError(() -> service.report(request(ReportTargetType.COMMENT)), ErrorCode.TARGET_NOT_FOUND, HttpStatus.NOT_FOUND);

        verifyNoInteractions(posts, reactions, cases, limits, jdbc);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void FR035_selfReportsStopAfterVisibilityAndBeforeQuotaLockOrClock(ReportTargetType target) {
        visible(target, USER);

        assertError(() -> service.report(request(target)), ErrorCode.SELF_REPORT, HttpStatus.UNPROCESSABLE_ENTITY);

        verifyNoInteractions(reactions, cases, limits, jdbc);
        assertNoTargetLocks();
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void FR035_FR036_reportLocksTheUserQuotaBeforeOneClockSnapshotAndDuplicateBeforeCounting(ReportTargetType target) {
        visible(target, AUTHOR);
        when(reactions.countReportsInDay(USER, snapshot.dayStart(), snapshot.nextDayStart())).thenReturn(19L);

        assertThat(service.report(request(target))).isEqualTo(new ReportReceived(true));

        var order = inOrder(auth, posts, comments, jdbc, limits, reactions, cases);
        order.verify(auth).currentUser();
        order.verify(auth).requireVerifiedEmail(USER);
        if (target == ReportTargetType.POST) {
            order.verify(posts).findPublished(TARGET, null, PageRequest.of(0, 1));
        } else {
            order.verify(comments).findReplyParentOnPublishedPost(TARGET);
        }
        order.verify(jdbc).query(eq(LOCK_SQL), eq(Map.of("key", "community:report-quota:" + USER)),
                any(ResultSetExtractor.class));
        order.verify(limits).snapshot();
        order.verify(reactions).existsById(reportId(target));
        order.verify(reactions).countReportsInDay(USER, snapshot.dayStart(), snapshot.nextDayStart());
        order.verify(limits).assertCanReport(19L, snapshot);
        order.verify(reactions).insertReport(USER, target.name(), TARGET, STORED_NOW);
        order.verify(cases).upsertPendingReport(eq(target.name()), eq(TARGET), anyString(), eq(STORED_NOW));
        verify(limits).snapshot();
        verifyNoMoreInteractions(auth, posts, comments, jdbc, limits, reactions, cases);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void FR036_duplicatePrecheckWinsOverAFullDailyQuotaWithoutAnInsertOrUpsert(ReportTargetType target) {
        visible(target, AUTHOR);
        when(reactions.existsById(reportId(target))).thenReturn(true);

        assertError(() -> service.report(request(target)), ErrorCode.DUPLICATE_REPORT, HttpStatus.CONFLICT);

        verify(limits).snapshot();
        verify(reactions, never()).countReportsInDay(anyLong(), any(), any());
        verify(limits, never()).assertCanReport(anyLong(), any());
        verify(reactions, never()).insertReport(anyLong(), anyString(), anyLong(), any());
        verifyNoInteractions(cases);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void FR035_twentiethExistingReportRejectsTheNextDistinctTargetWithoutWriting(ReportTargetType target) {
        visible(target, AUTHOR);
        when(reactions.countReportsInDay(USER, snapshot.dayStart(), snapshot.nextDayStart())).thenReturn(20L);
        doThrow(api(ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS))
                .when(limits).assertCanReport(20L, snapshot);

        assertError(() -> service.report(request(target)), ErrorCode.TOO_MANY_REQUESTS, HttpStatus.TOO_MANY_REQUESTS);

        verify(reactions, never()).insertReport(anyLong(), anyString(), anyLong(), any());
        verifyNoInteractions(cases);
    }

    @ParameterizedTest
    @EnumSource(ReportTargetType.class)
    void FR036_insertConflictBecomesDuplicateWithoutAppendingToTheCase(ReportTargetType target) {
        visible(target, AUTHOR);
        when(reactions.insertReport(USER, target.name(), TARGET, STORED_NOW)).thenReturn(0);

        assertError(() -> service.report(request(target)), ErrorCode.DUPLICATE_REPORT, HttpStatus.CONFLICT);

        verifyNoInteractions(cases);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n\u3000"})
    void FR032_FR036_nonOtherEmptyDetailsBecomeExplicitJsonNull(String detail) throws Exception {
        visible(ReportTargetType.POST, AUTHOR);

        service.report(new ReportWrite(ReportTargetType.POST, TARGET, ReportReason.SPAM, detail));

        JsonNode entry = capturedEntry(ReportTargetType.POST);
        assertThat(entry.path("detail").isNull()).isTrue();
    }

    @Test
    void FR036_entryUsesOneElementArrayAndPreservesUnicodeEscapingAndSharedMicrosecondInstant() throws Exception {
        visible(ReportTargetType.COMMENT, AUTHOR);
        String text = "举报 tiếng Việt \"quoted\" \\ line\n第二行 😀";

        assertThat(service.report(new ReportWrite(ReportTargetType.COMMENT, TARGET, ReportReason.OTHER,
                "\u3000 " + text + " \u3000"))).isEqualTo(new ReportReceived(true));

        JsonNode entry = capturedEntry(ReportTargetType.COMMENT);
        assertThat(entry.path("detail").asText()).isEqualTo(text);
        assertThat(entry.path("reason").asText()).isEqualTo("OTHER");
        verify(reactions).insertReport(USER, "COMMENT", TARGET, STORED_NOW);
    }

    @Test
    void FR001_FR002_userWithLearnerAndAnotherRoleStillUsesTheVerifiedEmailGate() {
        when(auth.currentUser()).thenReturn(new CurrentUser(USER, Set.of(RoleCode.LEARNER, RoleCode.ADMIN), false));
        visible(ReportTargetType.POST, AUTHOR);

        assertThat(service.report(request(ReportTargetType.POST)).received()).isTrue();

        verify(auth).requireVerifiedEmail(USER);
    }

    @Test
    void FR035_clockIsActuallyReadOnlyAfterTheQuotaLockHasBeenAcquired() {
        visible(ReportTargetType.POST, AUTHOR);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> {
            verify(jdbc).query(eq(LOCK_SQL), eq(Map.of("key", "community:report-quota:" + USER)), any(ResultSetExtractor.class));
            return NOW;
        });
        service = new ReportService(auth, posts, comments, reactions, cases,
                new CommunityLimits(clock, new CommunityProperties()), jdbc, mapper);

        service.report(request(ReportTargetType.POST));

        verify(clock).instant();
        verifyNoMoreInteractions(clock);
    }

    @Test
    void FR036_failedReactionInsertNeverCreatesOrAppendsACase() {
        visible(ReportTargetType.POST, AUTHOR);
        RuntimeException failure = new IllegalStateException("Synthetic insert failure");
        when(reactions.insertReport(USER, "POST", TARGET, STORED_NOW)).thenThrow(failure);

        assertThatThrownBy(() -> service.report(request(ReportTargetType.POST))).isSameAs(failure);

        verifyNoInteractions(cases);
    }

    @Test
    void FR036_serializationFailureNeverWritesAModerationCase() throws Exception {
        visible(ReportTargetType.POST, AUTHOR);
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("Synthetic JSON failure") { });
        service = new ReportService(auth, posts, comments, reactions, cases, limits, jdbc, failing);

        assertThatThrownBy(() -> service.report(request(ReportTargetType.POST))).isInstanceOf(RuntimeException.class);

        verifyNoInteractions(cases);
    }

    @Test
    void FR035_FR036_lockFailurePreventsTheClockAndAllMutationWork() {
        visible(ReportTargetType.POST, AUTHOR);
        when(jdbc.query(eq(LOCK_SQL), eq(Map.of("key", "community:report-quota:" + USER)), any(ResultSetExtractor.class)))
                .thenThrow(new IllegalStateException("Synthetic lock failure"));

        assertThatThrownBy(() -> service.report(request(ReportTargetType.POST))).isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(limits, reactions, cases);
    }

    @Test
    void FR036_reportUsesAReadCommittedWriteTransaction() throws Exception {
        Transactional transaction = ReportService.class.getMethod("report", ReportWrite.class).getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(transaction.readOnly()).isFalse();
    }

    private JsonNode capturedEntry(ReportTargetType target) throws Exception {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(cases).upsertPendingReport(eq(target.name()), eq(TARGET), json.capture(), eq(STORED_NOW));
        JsonNode entries = mapper.readTree(json.getValue());
        assertThat(entries.isArray()).isTrue();
        assertThat(entries).hasSize(1);
        JsonNode entry = entries.get(0);
        assertThat(java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(entry.fieldNames(), 0), false).toList())
                .containsExactlyInAnyOrder("userId", "reason", "detail", "reportedAt");
        assertThat(entry.path("userId").asLong()).isEqualTo(USER);
        assertThat(entry.path("reportedAt").isTextual()).isTrue();
        assertThat(Instant.parse(entry.path("reportedAt").asText())).isEqualTo(STORED_NOW);
        return entry;
    }

    private void visible(ReportTargetType type, long author) {
        if (type == ReportTargetType.POST) {
            Post post = new Post(author, PostCategory.QUESTION, "Synthetic question", "Synthetic content", NOW);
            post.setStatus(PostStatus.APPROVED);
            when(posts.findPublished(TARGET, null, PageRequest.of(0, 1))).thenReturn(List.of(post));
        } else {
            when(comments.findReplyParentOnPublishedPost(TARGET)).thenReturn(Optional.of(comment(author)));
        }
    }

    private Comment comment(long author) {
        return new Comment(81L, null, author, "Synthetic comment", (short) 0, NOW);
    }

    private void assertNoTargetLocks() {
        verify(posts, never()).findByIdForUpdate(anyLong());
        verify(comments, never()).findOwnedForUpdate(anyLong(), anyLong(), anyLong());
    }

    private static ReportWrite request(ReportTargetType type) { return new ReportWrite(type, TARGET, ReportReason.SPAM, null); }
    private static ReactionId reportId(ReportTargetType target) {
        return new ReactionId(USER, TargetType.valueOf(target.name()), TARGET, ReactionKind.REPORT);
    }
    private static ApiException api(ErrorCode code, HttpStatus status) { return new ApiException(code, status, "Synthetic error", Map.of()); }
    private static void assertError(Runnable action, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getStatus()).isEqualTo(status);
        });
    }
}
