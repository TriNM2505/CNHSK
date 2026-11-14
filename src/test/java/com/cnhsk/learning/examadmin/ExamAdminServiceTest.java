package com.cnhsk.learning.examadmin;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.cnhsk.learning.api.ExamUsageGuard;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;

class ExamAdminServiceTest {
    private final ExamAdminStore store = mock(ExamAdminStore.class);
    private final AuditService audit = mock(AuditService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private ExamAdminService service;
    private ExamRow row;
    private final List<Link> links = List.of(new Link(11, 0, 0, 1));

    @BeforeEach
    void setUp() throws Exception {
        service = new ExamAdminService(store, new ExamPublicationValidator(), audit, mapper, List.of());
        row = new ExamRow(7, "EXAM", "Đề tự soạn", 3, 1,
                mapper.readTree("[{\"index\":0,\"name\":\"Đọc\",\"type\":\"READING\",\"duration_min\":0}]"), ExamStatus.DRAFT, null);
        when(store.find(7, false)).thenReturn(row);
        when(store.find(7, true)).thenReturn(row);
        when(store.links(7)).thenReturn(links);
        when(store.questions(anyList())).thenReturn(List.of(new QuestionData(links.get(0), ExamPublicationValidatorTest.question(), "NP3-901")));
        when(store.existingQuestionIds(anyList())).thenAnswer(call -> {
            List<QuestionWrite> items = call.getArgument(0);
            return items.stream().map(QuestionWrite::questionId).collect(java.util.stream.Collectors.toSet());
        });
        when(store.insert(anyLong(), anyString(), anyString(), anyInt(), anyInt(), any())).thenReturn(7L);
    }

    @Test
    void creatingADraftPassesTheActorAndPreservesInputOrderAndPoints() {
        var input = new ExamWrite("EXAM", "Đề tự soạn", 3, 9, List.of(new Section("Đọc", SectionType.READING, 0)),
                List.of(new QuestionWrite(99, 0, 3), new QuestionWrite(11, 0, 6)));
        assertThat(service.create(42, input).status()).isEqualTo(ExamStatus.DRAFT);
        verify(store).insert(eq(42L), eq("EXAM"), eq("Đề tự soạn"), eq(3), eq(9), any());
        verify(store).replaceLinks(7, List.of(new Link(99, 0, 0, 3), new Link(11, 0, 1, 6)));
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @MethodSource("invalidWrites")
    void invalidDraftsNeverReachTheStore(ExamWrite input) {
        assertCode(() -> service.create(42, input), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(store, audit);
    }

    static Stream<ExamWrite> invalidWrites() {
        var valid = valid();
        var inputs = new ArrayList<ExamWrite>();
        inputs.add(null);
        for (String code : Arrays.asList(null, "", "lower", "SPACE HERE", "A".repeat(51)))
            inputs.add(new ExamWrite(code, valid.title(), 3, 1, valid.sections(), valid.questions()));
        for (String title : Arrays.asList(null, "", " ", "文".repeat(201)))
            inputs.add(new ExamWrite("EXAM", title, 3, 1, valid.sections(), valid.questions()));
        for (int hsk : new int[]{0, 7}) inputs.add(new ExamWrite("EXAM", "Đề", hsk, 1, valid.sections(), valid.questions()));
        for (int score : new int[]{0, 1001}) inputs.add(new ExamWrite("EXAM", "Đề", 3, score, valid.sections(), valid.questions()));
        for (List<Section> sections : Arrays.<List<Section>>asList(null, List.of(), java.util.Collections.nCopies(6, valid.sections().get(0)),
                Arrays.asList((Section) null), List.of(new Section("", SectionType.READING, 0)),
                List.of(new Section("Đọc", null, 0)), List.of(new Section("Đọc", SectionType.READING, -1)),
                List.of(new Section("Đọc", SectionType.READING, 181)), List.of(new Section("文".repeat(101), SectionType.READING, 0))))
            inputs.add(new ExamWrite("EXAM", "Đề", 3, 1, sections, valid.questions()));
        for (List<QuestionWrite> questions : Arrays.<List<QuestionWrite>>asList(null, Arrays.asList((QuestionWrite) null),
                java.util.Collections.nCopies(301, valid.questions().get(0)),
                List.of(new QuestionWrite(11, 0, 1), new QuestionWrite(11, 0, 2)),
                List.of(new QuestionWrite(0, 0, 1)), List.of(new QuestionWrite(11, -1, 1)),
                List.of(new QuestionWrite(11, 1, 1)), List.of(new QuestionWrite(11, 0, 0)), List.of(new QuestionWrite(11, 0, 101))))
            inputs.add(new ExamWrite("EXAM", "Đề", 3, 1, valid.sections(), questions));
        return inputs.stream();
    }

    @Test
    void validUpperAndLowerLimitsStillAllowIncompleteDrafts() {
        var parts = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> new Section("文".repeat(100), SectionType.WRITING, 180)).toList();
        var questions = java.util.stream.IntStream.rangeClosed(1, 300)
                .mapToObj(index -> new QuestionWrite(index, 4, 100)).toList();
        service.create(42, new ExamWrite("A".repeat(50), "文".repeat(200), 6, 1000, parts, questions));
        service.create(42, new ExamWrite("A", "文", 1, 1, List.of(new Section("文", SectionType.READING, 0)), List.of()));
        verify(store, times(2)).insert(eq(42L), anyString(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void invalidListBoundsAreRejectedBeforeQuerying() {
        for (int size : new int[]{0, 51}) assertCode(() -> service.list(null, null, null, 0, size), ErrorCode.VALIDATION_FAILED);
        for (int hsk : new int[]{0, 7}) assertCode(() -> service.list(null, hsk, null, 0, 20), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> service.list(null, null, null, -1, 20), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> service.list(null, null, "文".repeat(101), 0, 20), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(store);
    }

    @Test
    void missingQuestionsReturn404BeforeAnyWrite() {
        when(store.existingQuestionIds(anyList())).thenReturn(Set.of());
        assertCode(() -> service.create(42, valid()), ErrorCode.QUESTION_NOT_FOUND);
        verify(store, never()).insert(anyLong(), anyString(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void onlyTheExamCodeConstraintIsMappedToTheBusinessConflict() {
        var duplicate = new DuplicateKeyException("duplicate", new SQLException("constraint \"exams_code_key\"", "23505"));
        doThrow(duplicate).when(store).insert(anyLong(), anyString(), anyString(), anyInt(), anyInt(), any());
        assertCode(() -> service.create(42, valid()), ErrorCode.EXAM_CODE_TAKEN);
        var unrelated = new DuplicateKeyException("other", new SQLException("constraint \"other_key\"", "23505"));
        doThrow(unrelated).when(store).insert(anyLong(), anyString(), anyString(), anyInt(), anyInt(), any());
        assertThatThrownBy(() -> service.create(42, valid())).isSameAs(unrelated);
    }

    @ParameterizedTest
    @EnumSource(value = ExamStatus.class, names = {"PUBLISHED", "ARCHIVED"})
    void nonDraftGuardRunsBeforeReadingTheBodyOrEtag(ExamStatus status) {
        when(store.find(7, true)).thenReturn(new ExamRow(7, row.code(), row.title(), 3, 1, row.sections(), status, Instant.EPOCH));
        Supplier<ExamUpdate> body = mock(Supplier.class);
        assertCode(() -> service.update(7, body), ErrorCode.EXAM_NOT_EDITABLE);
        verifyNoInteractions(body);
        verify(store, never()).links(anyLong());
    }

    @Test
    void staleEtagNeverMutatesTheDraft() {
        assertCode(() -> service.update(7, () -> update(valid(), "stale")), ErrorCode.EDIT_CONFLICT);
        verify(store, never()).update(anyLong(), any());
        verify(store, never()).replaceLinks(anyLong(), anyList());
    }

    @Test
    void validUpdateLocksThenReplacesTheWholeDraft() {
        var input = valid();
        service.update(7, () -> update(input, service.etag(row, links)));
        var order = inOrder(store);
        order.verify(store).find(7, true);
        order.verify(store).links(7);
        order.verify(store).existingQuestionIds(input.questions());
        order.verify(store).update(7, input);
        order.verify(store).replaceLinks(7, links);
    }

    @Test
    void detailHasAStableEtagWithoutReturningQuestionAnswers() {
        var first = service.detail(7);
        assertThat(first.etag()).matches("[0-9a-f]{64}").isEqualTo(service.detail(7).etag());
        String json = mapper.valueToTree(first).toString();
        assertThat(json).doesNotContain("correctKey", "is_correct", "options", "explanation");
        assertThat(first.publicationErrors()).isEmpty();
        assertThat(first.questions().get(0).kid()).isEqualTo("NP3-901");
    }

    @Test
    void etagCanonicalizesJsonKeysAndTracksExamContentAndLinks() throws Exception {
        String original = service.etag(row, links);
        var reordered = mapper.readTree("[{\"duration_min\":0,\"type\":\"READING\",\"name\":\"Đọc\",\"index\":0}]");
        assertThat(service.etag(new ExamRow(7, "EXAM", row.title(), 3, 1, reordered, ExamStatus.DRAFT, null), links)).isEqualTo(original);
        for (ExamRow changed : List.of(new ExamRow(8, "EXAM", row.title(), 3, 1, row.sections(), ExamStatus.DRAFT, null),
                new ExamRow(7, "OTHER", row.title(), 3, 1, row.sections(), ExamStatus.DRAFT, null),
                new ExamRow(7, "EXAM", "Khác", 3, 1, row.sections(), ExamStatus.DRAFT, null),
                new ExamRow(7, "EXAM", row.title(), 2, 1, row.sections(), ExamStatus.DRAFT, null),
                new ExamRow(7, "EXAM", row.title(), 3, 2, row.sections(), ExamStatus.DRAFT, null),
                new ExamRow(7, "EXAM", row.title(), 3, 1, row.sections(), ExamStatus.PUBLISHED, Instant.EPOCH))) {
            assertThat(service.etag(changed, links)).isNotEqualTo(original);
        }
        assertThat(service.etag(row, List.of(new Link(12, 0, 0, 1)))).isNotEqualTo(original);
        assertThat(service.etag(row, List.of(new Link(11, 1, 0, 1)))).isNotEqualTo(original);
        assertThat(service.etag(row, List.of(new Link(11, 0, 1, 1)))).isNotEqualTo(original);
        assertThat(service.etag(row, List.of(new Link(11, 0, 0, 2)))).isNotEqualTo(original);
    }

    @Test
    void inUseComesFromTheRegisteredGuardsAndAnEmptyListMeansFalse() {
        when(store.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(new Listing(List.of(new SummaryRow(row, 1)), 1));
        assertThat(service.list(null, null, null, 0, 20).items().get(0).inUse()).isFalse();
        ExamUsageGuard first = mock(ExamUsageGuard.class);
        ExamUsageGuard second = mock(ExamUsageGuard.class);
        when(second.isInUse(7)).thenReturn(true);
        service = new ExamAdminService(store, new ExamPublicationValidator(), audit, mapper, List.of(first, second));
        assertThat(service.list(null, null, null, 0, 20).items().get(0).inUse()).isTrue();
        verify(first).isInUse(7);
        verify(second).isInUse(7);
    }

    @ParameterizedTest
    @EnumSource(ExamStatus.class)
    void cloneUsesTheActorAndRecordsItsSourceOnTheNewExam(ExamStatus status) {
        when(store.find(5, true)).thenReturn(new ExamRow(5, "SOURCE", row.title(), 3, 1, row.sections(), status, Instant.EPOCH));
        when(store.links(5)).thenReturn(links);
        service.clone(5, 99, new CloneWrite("COPY", null));
        verify(store).insert(99, "COPY", row.title(), 3, 1, row.sections());
        verify(audit).recordDetail(99L, "EXAM_CLONE", "EXAM", 7, java.util.Map.of("sourceExamId", 5L));
        verify(store).replaceLinks(7, links);
        verify(store, never()).update(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void cloneRejectsBlankTitles(String title) {
        assertCode(() -> service.clone(7, 42, new CloneWrite("COPY", title)), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(audit);
    }

    @Test
    void importedSectionsRemainUnknownAndTheirOriginalContentDeterminesTheEtag() throws Exception {
        var legacy = mapper.readTree("[{\"index\":0,\"name\":\"Phần cũ\"}]");
        when(store.find(7, false)).thenReturn(new ExamRow(7, "EXAM", row.title(), 3, 1, legacy, ExamStatus.PUBLISHED, Instant.EPOCH));
        var first = service.detail(7);
        assertThat(first.sections()).containsExactly(new SectionView("Phần cũ", null, null));
        assertThat(first.publicationErrors()).extracting(PublicationError::code).containsExactly(PublicationErrorCode.SECTION_TYPE_MISSING);
        assertThat(first.status()).isEqualTo(ExamStatus.PUBLISHED);
        assertThat(first.etag()).isEqualTo(service.detail(7).etag());
        assertThat(first.etag()).isEqualTo(service.etag(new ExamRow(7, "EXAM", row.title(), 3, 1, legacy, ExamStatus.PUBLISHED, Instant.EPOCH), links));
    }

    @Test
    void missingExamHasTheSame404ForAllOperations() {
        assertCode(() -> service.detail(404), ErrorCode.EXAM_NOT_FOUND);
        assertCode(() -> service.update(404, () -> null), ErrorCode.EXAM_NOT_FOUND);
        assertCode(() -> service.clone(404, 42, new CloneWrite("COPY", null)), ErrorCode.EXAM_NOT_FOUND);
    }

    @Test
    void publicationAuditsTheActorAndReturnsThePublishedDetail() {
        when(store.find(7, false)).thenReturn(new ExamRow(7, row.code(), row.title(), 3, 1,
                row.sections(), ExamStatus.PUBLISHED, Instant.EPOCH));
        var result = service.publish(7, 42, () -> new EtagBody(service.etag(row, links)));
        assertThat(result.status()).isEqualTo(ExamStatus.PUBLISHED);
        assertThat(result.publishedAt()).isEqualTo(Instant.EPOCH);
        var order = inOrder(store, audit);
        order.verify(store).find(7, true);
        order.verify(store).links(7);
        order.verify(store).questions(links);
        order.verify(store).publish(7);
        order.verify(audit).recordDetail(42L, "EXAM_PUBLISH", "EXAM", 7, java.util.Map.of("before", "DRAFT", "after", "PUBLISHED"));
        verify(store, never()).lockUsage(anyLong());
    }

    @ParameterizedTest
    @EnumSource(value = ExamStatus.class, names = {"PUBLISHED", "ARCHIVED"})
    void publicationRejectsNonDraftsBeforeReadingTheBody(ExamStatus status) {
        when(store.find(7, true)).thenReturn(new ExamRow(7, row.code(), row.title(), 3, 1, row.sections(), status, Instant.EPOCH));
        Supplier<EtagBody> body = mock(Supplier.class);
        assertCode(() -> service.publish(7, 42, body), ErrorCode.EXAM_NOT_EDITABLE);
        verifyNoInteractions(body, audit);
        verify(store, never()).publish(anyLong());
    }

    @Test
    void publicationRejectsMissingAndStaleEtagsBeforeValidationOrWriting() {
        assertCode(() -> service.publish(7, 42, () -> null), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> service.publish(7, 42, () -> new EtagBody(null)), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> service.publish(7, 42, () -> new EtagBody("stale")), ErrorCode.EDIT_CONFLICT);
        verify(store, never()).questions(anyList());
        verify(store, never()).publish(anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void publicationReturnsAllValidatorErrorsWithoutWriting() {
        when(store.questions(links)).thenReturn(List.of());
        assertThatThrownBy(() -> service.publish(7, 42, () -> new EtagBody(service.etag(row, links))))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.EXAM_PUBLISH_INVALID);
                    assertThat(mapper.valueToTree(exception.getExtra()).path("details").findValuesAsText("code"))
                            .containsExactly("EMPTY_SECTION", "SCORE_MISMATCH");
                });
        verify(store, never()).publish(anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void archiveTakesTheUsageLockBeforeTheRowAndAllGuards() {
        ExamUsageGuard first = mock(ExamUsageGuard.class);
        ExamUsageGuard second = mock(ExamUsageGuard.class);
        when(second.isInUse(7)).thenReturn(true);
        service = new ExamAdminService(store, new ExamPublicationValidator(), audit, mapper, List.of(first, second));
        assertCode(() -> service.archive(7, 42), ErrorCode.EXAM_IN_USE);
        var order = inOrder(store, first, second);
        order.verify(store).lockUsage(7);
        order.verify(store).find(7, true);
        order.verify(first).isInUse(7);
        order.verify(second).isInUse(7);
        verify(store, never()).archive(anyLong());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(value = ExamStatus.class, names = {"DRAFT", "PUBLISHED"})
    void unusedExamsCanBeArchivedWithAnAudit(ExamStatus status) {
        when(store.find(7, true)).thenReturn(new ExamRow(7, row.code(), row.title(), 3, 1, row.sections(), status, Instant.EPOCH));
        when(store.find(7, false)).thenReturn(new ExamRow(7, row.code(), row.title(), 3, 1, row.sections(), ExamStatus.ARCHIVED, Instant.EPOCH));
        assertThat(service.archive(7, 42).publishedAt()).isEqualTo(Instant.EPOCH);
        verify(store).archive(7);
        verify(audit).recordDetail(42L, "EXAM_ARCHIVE", "EXAM", 7, java.util.Map.of("before", status.name(), "after", "ARCHIVED"));
    }

    @Test
    void alreadyArchivedDoesNotConsultGuardsOrWriteAnotherAudit() {
        ExamUsageGuard guard = mock(ExamUsageGuard.class);
        service = new ExamAdminService(store, new ExamPublicationValidator(), audit, mapper, List.of(guard));
        when(store.find(7, true)).thenReturn(new ExamRow(7, row.code(), row.title(), 3, 1, row.sections(), ExamStatus.ARCHIVED, Instant.EPOCH));
        var result = service.archive(7, 42);
        assertThat(result.status()).isEqualTo(ExamStatus.ARCHIVED);
        assertThat(result.publishedAt()).isEqualTo(Instant.EPOCH);
        verifyNoInteractions(guard, audit);
        verify(store, never()).archive(anyLong());
    }

    @Test
    void lifecycleOperationsReturn404ForMissingExams() {
        assertCode(() -> service.publish(404, 42, () -> null), ErrorCode.EXAM_NOT_FOUND);
        assertCode(() -> service.archive(404, 42), ErrorCode.EXAM_NOT_FOUND);
        verifyNoInteractions(audit);
    }

    static ExamWrite valid() {
        return new ExamWrite("EXAM", "Đề tự soạn", 3, 1, List.of(new Section("Đọc", SectionType.READING, 0)),
                List.of(new QuestionWrite(11, 0, 1)));
    }

    static ExamUpdate update(ExamWrite value, String etag) {
        return new ExamUpdate(value.code(), value.title(), value.hskLevel(), value.totalScore(), value.sections(), value.questions(), etag);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
