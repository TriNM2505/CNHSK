package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.CommunityAuthors;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.UserRef;
import com.cnhsk.community.moderation.web.CommentModeration;
import com.cnhsk.community.moderation.web.CommentModeration.Action;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class CommentModerationServiceTest {
    private static final long TARGET = 17L;
    private static final long POST = 81L;
    private static final long MODERATOR = 42L;
    private static final long AUTHOR = 99L;
    private static final long PREVIOUS_MODERATOR = 88L;
    private static final Instant ORIGINAL = Instant.parse("2026-10-01T01:02:03Z");
    private static final String CONTENT = "Literal <b>body</b> **text** 😀";
    private CommentRepository comments;
    private PostRepository posts;
    private AuthApi auth;
    private CommunityAuthors authors;
    private AuditService audit;
    private CommentModerationService service;

    @BeforeEach
    void setUp() {
        comments = mock(CommentRepository.class);
        posts = mock(PostRepository.class);
        auth = mock(AuthApi.class);
        authors = mock(CommunityAuthors.class);
        audit = mock(AuditService.class);
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(RoleCode.MODERATOR), false));
        when(authors.resolve(anySet())).thenReturn(Map.of());
        service = new CommentModerationService(comments, posts, auth, authors, audit);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "MODERATOR", mode = EnumSource.Mode.EXCLUDE)
    void FR001_everyEntryPointRejectsOtherRolesBeforeValidationOrDatabaseWork(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(role), true));
        assertError(() -> service.listPostCommentsForModeration(POST), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        assertError(() -> service.moderateComment(TARGET, null), ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
        verifyNoInteractions(comments, posts, authors, audit);
        verify(auth, never()).requireVerifiedEmail(anyLong());
    }

    @Test
    void FR001_everyEntryPointPropagatesUnauthenticatedBeforeAnyWork() {
        when(auth.currentUser()).thenThrow(new ApiException(ErrorCode.UNAUTHENTICATED,
                HttpStatus.UNAUTHORIZED, "Synthetic authentication failure", Map.of()));
        assertError(() -> service.listPostCommentsForModeration(POST), ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        assertError(() -> service.moderateComment(TARGET, null), ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(comments, posts, authors, audit);
    }

    @Test
    void FR001_unverifiedModeratorWithAdditionalRolesNeedsNeitherLearnerNorVerifiedEmail() {
        when(auth.currentUser()).thenReturn(new CurrentUser(MODERATOR, Set.of(RoleCode.MODERATOR, RoleCode.ADMIN), false));
        when(posts.findById(POST)).thenReturn(Optional.of(post(PostStatus.DRAFT)));
        assertThat(service.listPostCommentsForModeration(POST)).isEmpty();
        Comment stored = comment(TARGET, AUTHOR, CommentStatus.VISIBLE);
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(stored));
        assertThat(service.moderateComment(TARGET, new CommentModeration(Action.RESTORE, "Reason")).status())
                .isEqualTo(CommentStatus.VISIBLE);
        verify(auth, org.mockito.Mockito.times(2)).currentUser();
        verifyNoMoreInteractions(auth);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(PostStatus.class)
    void FR063_everyPostStateReturnsAllCommentStatesWithRawContentOrderedAndOneDistinctIdentityBatch(PostStatus status) {
        Comment visible = comment(TARGET, AUTHOR, CommentStatus.VISIBLE);
        Comment hidden = comment(TARGET + 1, AUTHOR + 1, CommentStatus.HIDDEN);
        ReflectionTestUtils.setField(hidden, "parentId", TARGET);
        ReflectionTestUtils.setField(hidden, "depth", (short) 1);
        hidden.setModeratedBy(PREVIOUS_MODERATOR);
        hidden.setModerationReason("Stored hide reason");
        Comment deleted = comment(TARGET + 2, AUTHOR, CommentStatus.DELETED);
        deleted.setModeratedBy(AUTHOR);
        deleted.setModerationReason("AUTHOR_DELETED");
        when(posts.findById(POST)).thenReturn(Optional.of(post(status)));
        when(comments.findTreeByPostId(POST)).thenReturn(List.of(visible, hidden, deleted));
        Set<Long> identities = Set.of(AUTHOR, AUTHOR + 1, PREVIOUS_MODERATOR);
        when(authors.resolve(identities)).thenReturn(Map.of(AUTHOR, new UserRef(AUTHOR, "Author")));

        var result = service.listPostCommentsForModeration(POST);

        assertThat(result).extracting(item -> item.id()).containsExactly(TARGET, TARGET + 1, TARGET + 2);
        assertThat(result).extracting(item -> item.status())
                .containsExactly(CommentStatus.VISIBLE, CommentStatus.HIDDEN, CommentStatus.DELETED);
        assertThat(result).allSatisfy(item -> {
            assertThat(item.postId()).isEqualTo(POST);
            assertThat(item.content()).isEqualTo(CONTENT);
            assertThat(item.createdAt()).isEqualTo(ORIGINAL);
        });
        assertThat(result.get(0).parentId()).isNull();
        assertThat(result.get(0).depth()).isZero();
        assertThat(result.get(0).author()).isEqualTo(new UserRef(AUTHOR, "Author"));
        assertThat(result.get(0).moderatedBy()).isNull();
        assertThat(result.get(0).moderationReason()).isNull();
        assertThat(result.get(1).parentId()).isEqualTo(TARGET);
        assertThat(result.get(1).depth()).isEqualTo((short) 1);
        assertThat(result.get(1).author()).isEqualTo(new UserRef(AUTHOR + 1, null));
        assertThat(result.get(1).moderatedBy()).isEqualTo(new UserRef(PREVIOUS_MODERATOR, null));
        assertThat(result.get(1).moderationReason()).isEqualTo("Stored hide reason");
        assertThat(result.get(2).moderatedBy()).isEqualTo(new UserRef(AUTHOR, "Author"));
        assertThat(result.get(2).moderationReason()).isEqualTo("AUTHOR_DELETED");
        var order = inOrder(auth, posts, comments, authors);
        order.verify(auth).currentUser();
        order.verify(posts).findById(POST);
        order.verify(comments).findTreeByPostId(POST);
        order.verify(authors).resolve(identities);
        verifyNoMoreInteractions(auth, posts, comments, authors);
        verifyNoInteractions(audit);
    }

    @Test
    void FR063_existingPostWithNoCommentsSkipsIdentityLookup() {
        when(posts.findById(POST)).thenReturn(Optional.of(post(PostStatus.HIDDEN)));
        assertThat(service.listPostCommentsForModeration(POST)).isEmpty();
        verify(posts).findById(POST);
        verify(comments).findTreeByPostId(POST);
        verifyNoMoreInteractions(posts, comments);
        verifyNoInteractions(authors, audit);
    }

    @Test
    void FR063_missingPostReturns404WithoutReadingCommentsOrNames() {
        assertError(() -> service.listPostCommentsForModeration(POST), ErrorCode.POST_NOT_FOUND, HttpStatus.NOT_FOUND);
        verify(posts).findById(POST);
        verifyNoInteractions(comments, authors, audit);
    }

    @Test
    void FR052_independentInvalidRequestsFailBeforeAnyLookupIncludingMissingTargets() {
        for (CommentModeration request : List.of(new CommentModeration(null, "Reason"),
                new CommentModeration(Action.HIDE, null), new CommentModeration(Action.DELETE, ""),
                new CommentModeration(Action.RESTORE, " \t\u3000"),
                new CommentModeration(Action.HIDE, "😀".repeat(1001)),
                new CommentModeration(Action.DELETE, "\u3000AUTHOR_DELETED\t"))) {
            assertError(() -> service.moderateComment(TARGET, request), ErrorCode.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertError(() -> service.moderateComment(TARGET, null), ErrorCode.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(comments, posts, authors, audit);
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void FR052_missingCommentIs404AfterTheRowLockLookup(Action action) {
        assertError(() -> service.moderateComment(TARGET, new CommentModeration(action, "Reason")),
                ErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
        verify(comments).findByIdForUpdate(TARGET);
        verifyNoMoreInteractions(comments);
        verifyNoInteractions(posts, authors, audit);
    }

    @ParameterizedTest
    @CsvSource({"VISIBLE,RESTORE", "HIDDEN,HIDE", "DELETED,HIDE", "DELETED,DELETE", "DELETED,RESTORE"})
    void FR054_selfModerationFailsEvenForNoOpsOrAuthorDeletedRestore(CommentStatus status, Action action) {
        Comment stored = comment(TARGET, MODERATOR, status);
        stored.setModeratedBy(MODERATOR);
        stored.setModerationReason("AUTHOR_DELETED");
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(stored));
        assertError(() -> service.moderateComment(TARGET, new CommentModeration(action, "Reason")),
                ErrorCode.SELF_MODERATION, HttpStatus.FORBIDDEN);
        verify(comments).findByIdForUpdate(TARGET);
        verifyNoMoreInteractions(comments);
        verifyNoInteractions(posts, authors, audit);
    }

    @ParameterizedTest
    @CsvSource({
            "VISIBLE,false,HIDE,HIDDEN,true,false", "VISIBLE,false,DELETE,DELETED,true,false",
            "VISIBLE,false,RESTORE,VISIBLE,false,false", "HIDDEN,false,HIDE,HIDDEN,false,false",
            "HIDDEN,false,DELETE,DELETED,true,false", "HIDDEN,false,RESTORE,VISIBLE,true,false",
            "DELETED,false,HIDE,DELETED,false,false", "DELETED,false,DELETE,DELETED,false,false",
            "DELETED,false,RESTORE,VISIBLE,true,false", "DELETED,true,HIDE,DELETED,false,false",
            "DELETED,true,DELETE,DELETED,false,false", "DELETED,true,RESTORE,DELETED,false,true"})
    void FR053_FR055_completeTransitionMatrixPreservesNoOpsAndAuditsOnlyRealChanges(CommentStatus before,
            boolean authorDeleted, Action action, CommentStatus after, boolean changed, boolean conflict) {
        Comment stored = comment(TARGET, AUTHOR, before);
        long previousActor = authorDeleted ? AUTHOR : PREVIOUS_MODERATOR;
        String previousReason = authorDeleted ? "AUTHOR_DELETED" : "Stored previous reason";
        stored.setModeratedBy(previousActor);
        stored.setModerationReason(previousReason);
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(stored));
        if (changed) successfulUpdate(after, "Reason");

        if (conflict) {
            assertError(() -> service.moderateComment(TARGET, new CommentModeration(action, "Reason")),
                    ErrorCode.COMMENT_NOT_RESTORABLE, HttpStatus.CONFLICT);
            verify(comments).findByIdForUpdate(TARGET);
            verifyNoMoreInteractions(comments);
            verifyNoInteractions(posts, authors, audit);
        } else {
            var result = service.moderateComment(TARGET, new CommentModeration(action, "\u3000 Reason \t"));
            assertThat(result.id()).isEqualTo(TARGET);
            assertThat(result.postId()).isEqualTo(POST);
            assertThat(result.parentId()).isNull();
            assertThat(result.depth()).isZero();
            assertThat(result.status()).isEqualTo(after);
            assertThat(result.content()).isEqualTo(CONTENT);
            assertThat(result.author()).isEqualTo(new UserRef(AUTHOR, null));
            assertThat(result.createdAt()).isEqualTo(ORIGINAL);
            assertThat(result.moderatedBy()).isEqualTo(new UserRef(changed ? MODERATOR : previousActor, null));
            assertThat(result.moderationReason()).isEqualTo(changed ? "Reason" : previousReason);
            var order = inOrder(auth, comments, audit, authors);
            order.verify(auth).currentUser();
            order.verify(comments).findByIdForUpdate(TARGET);
            if (changed) {
                order.verify(comments).moderateDirect(TARGET, after.name(), MODERATOR, "Reason");
                order.verify(audit).recordDetail(MODERATOR, "COMMUNITY_COMMENT_" + action.name(), "COMMENT", TARGET,
                        Map.of("before", before.name(), "after", after.name(), "reason", "Reason"));
                order.verify(comments).findById(TARGET);
            }
            Set<Long> identities = changed ? Set.of(AUTHOR, MODERATOR)
                    : previousActor == AUTHOR ? Set.of(AUTHOR) : Set.of(AUTHOR, previousActor);
            order.verify(authors).resolve(identities);
            verifyNoMoreInteractions(auth, comments, audit, authors);
            verifyNoInteractions(posts);
        }
        assertThat(stored.getStatus()).isEqualTo(before);
        assertThat(stored.getModeratedBy()).isEqualTo(previousActor);
        assertThat(stored.getModerationReason()).isEqualTo(previousReason);
        assertThat(stored.getContent()).isEqualTo(CONTENT);
        assertThat(stored.getCreatedAt()).isEqualTo(ORIGINAL);
        assertThat(stored.getUpdatedAt()).isEqualTo(ORIGINAL);
    }

    @ParameterizedTest
    @MethodSource("restorableLegacyMarkers")
    void FR053_onlyTheExactDeletedAuthorSourceMarkerBlocksRestore(CommentStatus before, Long previousActor,
            String previousReason) {
        Comment stored = comment(TARGET, AUTHOR, before);
        stored.setModeratedBy(previousActor);
        stored.setModerationReason(previousReason);
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(stored));
        if (before != CommentStatus.VISIBLE) successfulUpdate(CommentStatus.VISIBLE, "Reason");
        assertThat(service.moderateComment(TARGET, new CommentModeration(Action.RESTORE, "Reason")).status())
                .isEqualTo(CommentStatus.VISIBLE);
        if (before == CommentStatus.VISIBLE) {
            verifyNoInteractions(audit);
            verify(comments, never()).moderateDirect(anyLong(), any(), anyLong(), any());
        } else {
            verify(comments).moderateDirect(TARGET, "VISIBLE", MODERATOR, "Reason");
            verify(audit).recordDetail(MODERATOR, "COMMUNITY_COMMENT_RESTORE", "COMMENT", TARGET,
                    Map.of("before", before.name(), "after", "VISIBLE", "reason", "Reason"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000})
    void FR052_reasonBoundariesCountUnicodeCodePointsAfterStripAndReachTheNativeUpdate(int count) {
        String reason = "😀".repeat(count);
        CommentModeration request = new CommentModeration(Action.HIDE, "\u3000" + reason + "\t");
        assertThat(request.reason()).isEqualTo(reason);
        assertThatCode(request::validateIndependent).doesNotThrowAnyException();
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(comment(TARGET, AUTHOR, CommentStatus.VISIBLE)));
        successfulUpdate(CommentStatus.HIDDEN, reason);
        assertThat(service.moderateComment(TARGET, request).moderationReason()).isEqualTo(reason);
        verify(comments).moderateDirect(TARGET, "HIDDEN", MODERATOR, reason);
    }

    @ParameterizedTest
    @ValueSource(strings = {"author_deleted", "Author_Deleted", "AUTHOR_DELETED suffix"})
    void FR052_reservedReasonMatchIsCaseSensitiveAndExact(String reason) {
        assertThatCode(new CommentModeration(Action.DELETE, reason)::validateIndependent).doesNotThrowAnyException();
    }

    @Test
    void FR052_beanValidationAndIndependentValidationRejectTheSameInvalidFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (CommentModeration request : List.of(new CommentModeration(null, "Reason"),
                    new CommentModeration(Action.HIDE, null), new CommentModeration(Action.HIDE, " \u3000"),
                    new CommentModeration(Action.HIDE, "😀".repeat(1001)),
                    new CommentModeration(Action.HIDE, "AUTHOR_DELETED"))) {
                assertThat(validator.validate(request)).isNotEmpty();
                assertError(request::validateIndependent, ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
            }
            assertThat(validator.validate(new CommentModeration(Action.RESTORE, "😀".repeat(1000)))).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "true", "42", "\"text\"", "{\"action\":42,\"reason\":\"Reason\"}",
            "{\"action\":true,\"reason\":\"Reason\"}", "{\"action\":[],\"reason\":\"Reason\"}",
            "{\"action\":\"hide\",\"reason\":\"Reason\"}", "{\"action\":\"BAN\",\"reason\":\"Reason\"}",
            "{\"action\":\"HIDE\",\"reason\":42}", "{\"action\":\"HIDE\",\"reason\":false}",
            "{\"action\":\"HIDE\",\"reason\":{}}", "{\"action\":\"HIDE\",\"reason\":\"Reason\"} {}"})
    void FR052_bodyDeserializerRejectsWrongShapesTypesEnumCaseAndTrailingTokens(String json) {
        assertThatThrownBy(() -> new ObjectMapper().readValue(json, CommentModeration.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":\"Reason\"}", "{\"action\":\"HIDE\"}",
            "{\"action\":null,\"reason\":\"Reason\"}", "{\"action\":\"HIDE\",\"reason\":null}",
            "{\"action\":\"HIDE\",\"reason\":\"　 \\t\"}",
            "{\"action\":\"HIDE\",\"reason\":\" AUTHOR_DELETED \"}"})
    void FR052_missingNullBlankAndReservedBodyFieldsFailIndependentValidation(String json) throws Exception {
        CommentModeration request = new ObjectMapper().readValue(json, CommentModeration.class);
        assertError(request::validateIndependent, ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void FR052_bodyDeserializerStripsReasonAndIgnoresClientSuppliedModerationMetadata() throws Exception {
        CommentModeration request = new ObjectMapper().readValue("""
                {"action":"HIDE","reason":"　 Reason ","status":"VISIBLE","moderatedBy":123,"authorId":123}
                """, CommentModeration.class);
        assertThat(request.action()).isEqualTo(Action.HIDE);
        assertThat(request.reason()).isEqualTo("Reason");
        assertThatCode(request::validateIndependent).doesNotThrowAnyException();
    }

    @Test
    void FR055_successResponseUsesTheFreshRowAfterAuditInsteadOfMutatingOrReturningTheLockedEntity() {
        Comment stored = comment(TARGET, AUTHOR, CommentStatus.VISIBLE);
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(stored));
        Comment fresh = successfulUpdate(CommentStatus.HIDDEN, "Reason");
        fresh.setContent("Fresh persisted content");
        ReflectionTestUtils.setField(fresh, "parentId", TARGET - 1);
        ReflectionTestUtils.setField(fresh, "depth", (short) 2);
        when(authors.resolve(Set.of(AUTHOR, MODERATOR))).thenReturn(Map.of(
                AUTHOR, new UserRef(AUTHOR, "Author"), MODERATOR, new UserRef(MODERATOR, "Moderator")));
        var result = service.moderateComment(TARGET, new CommentModeration(Action.HIDE, "Reason"));
        assertThat(result.content()).isEqualTo("Fresh persisted content");
        assertThat(result.parentId()).isEqualTo(TARGET - 1);
        assertThat(result.depth()).isEqualTo((short) 2);
        assertThat(result.author()).isEqualTo(new UserRef(AUTHOR, "Author"));
        assertThat(result.moderatedBy()).isEqualTo(new UserRef(MODERATOR, "Moderator"));
        assertThat(stored.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(stored.getContent()).isEqualTo(CONTENT);
        verify(comments, never()).save(any());
        verifyNoInteractions(posts);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void FR055_unexpectedAffectedCountFailsWithoutAuditOrFreshRead(int count) {
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(comment(TARGET, AUTHOR, CommentStatus.VISIBLE)));
        when(comments.moderateDirect(TARGET, "HIDDEN", MODERATOR, "Reason")).thenReturn(count);
        assertThatThrownBy(() -> service.moderateComment(TARGET, new CommentModeration(Action.HIDE, "Reason")))
                .isInstanceOf(IllegalStateException.class);
        verify(comments, never()).findById(anyLong());
        verifyNoInteractions(posts, authors, audit);
    }

    @Test
    void FR055_auditFailurePropagatesBeforeFreshReadAndNameResolution() {
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(comment(TARGET, AUTHOR, CommentStatus.VISIBLE)));
        successfulUpdate(CommentStatus.HIDDEN, "Reason");
        RuntimeException failure = new IllegalStateException("Synthetic audit failure");
        doThrow(failure).when(audit).recordDetail(MODERATOR, "COMMUNITY_COMMENT_HIDE", "COMMENT", TARGET,
                Map.of("before", "VISIBLE", "after", "HIDDEN", "reason", "Reason"));
        assertThatThrownBy(() -> service.moderateComment(TARGET, new CommentModeration(Action.HIDE, "Reason")))
                .isSameAs(failure);
        verify(comments, never()).findById(anyLong());
        verifyNoInteractions(posts, authors);
    }

    @Test
    void FR055_missingFreshRowFailsInsteadOfReturningTheLockedSnapshot() {
        when(comments.findByIdForUpdate(TARGET)).thenReturn(Optional.of(comment(TARGET, AUTHOR, CommentStatus.VISIBLE)));
        when(comments.moderateDirect(TARGET, "HIDDEN", MODERATOR, "Reason")).thenReturn(1);
        assertError(() -> service.moderateComment(TARGET, new CommentModeration(Action.HIDE, "Reason")),
                ErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND);
        verify(audit).recordDetail(MODERATOR, "COMMUNITY_COMMENT_HIDE", "COMMENT", TARGET,
                Map.of("before", "VISIBLE", "after", "HIDDEN", "reason", "Reason"));
        verifyNoInteractions(posts, authors);
    }

    @Test
    void FR053_readAndMutationTransactionContractsAndPessimisticLockAreExplicit() throws Exception {
        Transactional read = CommentModerationService.class.getMethod("listPostCommentsForModeration", long.class)
                .getAnnotation(Transactional.class);
        assertThat(read).isNotNull();
        assertThat(read.readOnly()).isTrue();
        Transactional write = CommentModerationService.class.getMethod("moderateComment", long.class, CommentModeration.class)
                .getAnnotation(Transactional.class);
        assertThat(write).isNotNull();
        assertThat(write.readOnly()).isFalse();
        assertThat(write.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(write.propagation()).isEqualTo(Propagation.REQUIRED);
        Lock lock = CommentRepository.class.getMethod("findByIdForUpdate", long.class).getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    private Comment successfulUpdate(CommentStatus after, String reason) {
        Comment fresh = comment(TARGET, AUTHOR, after);
        fresh.setModeratedBy(MODERATOR);
        fresh.setModerationReason(reason);
        when(comments.moderateDirect(TARGET, after.name(), MODERATOR, reason)).thenReturn(1);
        when(comments.findById(TARGET)).thenReturn(Optional.of(fresh));
        return fresh;
    }

    private static Stream<Arguments> restorableLegacyMarkers() {
        return Stream.of(Arguments.of(CommentStatus.VISIBLE, AUTHOR, "AUTHOR_DELETED"),
                Arguments.of(CommentStatus.HIDDEN, AUTHOR, "AUTHOR_DELETED"),
                Arguments.of(CommentStatus.DELETED, PREVIOUS_MODERATOR, "AUTHOR_DELETED"),
                Arguments.of(CommentStatus.DELETED, null, "AUTHOR_DELETED"),
                Arguments.of(CommentStatus.DELETED, AUTHOR, null),
                Arguments.of(CommentStatus.DELETED, AUTHOR, "author_deleted"),
                Arguments.of(CommentStatus.DELETED, AUTHOR, "AUTHOR_DELETED "));
    }

    private static Comment comment(long id, long author, CommentStatus status) {
        Comment comment = new Comment(POST, null, author, CONTENT, (short) 0, ORIGINAL);
        ReflectionTestUtils.setField(comment, "id", id);
        comment.setStatus(status);
        return comment;
    }

    private static Post post(PostStatus status) {
        Post post = new Post(AUTHOR, PostCategory.QUESTION, "Synthetic title", "Synthetic content", ORIGINAL);
        ReflectionTestUtils.setField(post, "id", POST);
        post.setStatus(status);
        return post;
    }

    private static void assertError(Runnable operation, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(code);
            assertThat(error.getStatus()).isEqualTo(status);
        });
    }
}
