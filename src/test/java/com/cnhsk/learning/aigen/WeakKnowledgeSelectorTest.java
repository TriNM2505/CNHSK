package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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

import com.cnhsk.learning.aigen.WeakKnowledgeQueryRepository.ActiveRun;
import com.cnhsk.learning.aigen.WeakKnowledgeQueryRepository.NodeSummary;
import com.cnhsk.learning.aigen.WeakKnowledgeQueryRepository.WrongAnswer;
import com.cnhsk.learning.aigen.WeakKnowledgeSelector.Candidate;
import com.cnhsk.learning.mastery.MasteryReader;
import com.cnhsk.learning.mastery.MasteryReader.MasteryView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class WeakKnowledgeSelectorTest {

    private static final Instant NOW = Instant.parse("2026-11-15T04:00:00Z");
    private static final String A = "TV1-001";
    private static final String B = "TV1-002";
    private static final String C = "TV1-003";
    private static final String D = "TV1-004";
    private final WeakKnowledgeQueryRepository repository = mock(WeakKnowledgeQueryRepository.class);
    private final AiGenerateConfig config = mock(AiGenerateConfig.class);
    private final MasteryReader mastery = mock(MasteryReader.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ObjectMapper mapper = spy(new ObjectMapper());
    private WeakKnowledgeSelector selector;

    @BeforeEach
    void setUp() {
        when(config.windowDays()).thenReturn(14);
        when(config.minWrong()).thenReturn(20);
        when(config.minBank()).thenReturn(10);
        when(config.maxBatchesPerRun()).thenReturn(3);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        selector = new WeakKnowledgeSelector(repository, config, mastery, Clock.fixed(NOW, ZoneOffset.UTC),
                transactions, mapper);
    }

    @Test
    void selectsOnlyNodesMeetingRawThresholdBankAndReviewFilters() {
        wrong(row(1, A, 25), row(2, B, 25), row(3, C, 5), row(4, D, 30));
        when(repository.nodes(Set.of(A, B, D))).thenReturn(List.of(node(1, A, 4), node(2, B, 12),
                new NodeSummary(4, D, "Can do", 0, true, true)));

        assertThat(selector.select()).containsExactly(new Candidate(1, A, 25, 4, 0));
        verifyNoInteractions(mastery);
        verify(repository, times(1)).wrongAnswers(NOW.minusSeconds(14 * 86400L), NOW);
        verify(repository, times(1)).nodes(Set.of(A, B, D));
        verify(repository, times(1)).activeRuns();
    }

    @Test
    void thresholdIsInclusiveAndBankLimitIsStrict() {
        wrong(row(1, A, 20), row(2, B, 20));
        nodes(node(1, A, 9), node(2, B, 10));

        assertThat(selector.select()).extracting(Candidate::kid).containsExactly(A);
    }

    @Test
    void addsPrimaryAndOnlySelectedDistractorIndependentlyIncludingSameKid() {
        wrong(new WrongAnswer(1, 1, "B", A, options(B, C), 10),
                new WrongAnswer(1, 2, "B", A, options(A, D), 10));
        when(config.minWrong()).thenReturn(1);
        nodes(node(1, A, 0), node(2, B, 0), node(3, C, 0), node(4, D, 0));

        assertThat(selector.select()).containsExactly(new Candidate(1, A, 30, 0, 0),
                new Candidate(2, B, 10, 0, 0));
        verify(repository).nodes(Set.of(A, B));
    }

    @Test
    void preservesSelectedErrorWhenPrimaryIsAbsentAndIgnoresCorrectDistractorAndUnselectedKeys() {
        wrong(new WrongAnswer(1, 1, "B", null, options(B, C), 20),
                new WrongAnswer(1, 1, "A", null, options(B, C), 20),
                new WrongAnswer(1, 1, "unknown", null, options(B, C), 20));
        nodes(node(2, B, 0));

        assertThat(selector.select()).containsExactly(new Candidate(2, B, 20, 0, 0));
        verify(repository).nodes(Set.of(B));
    }

    @Test
    void supportsCountsBeyondIntegerRange() {
        long count = (long) Integer.MAX_VALUE + 10;
        wrong(new WrongAnswer(1, 1, "B", A, options(A, B), count));
        nodes(node(1, A, 0));

        assertThat(selector.select()).containsExactly(new Candidate(1, A, count * 2, 0, 0));
    }

    @Test
    void parsesOptionsOncePerQuestionAcrossUsersAndAnswers() throws Exception {
        String options = options(B, C);
        wrong(new WrongAnswer(1, 7, "B", A, options, 20),
                new WrongAnswer(2, 7, "C", A, options, 20));
        nodes(node(1, A, 0), node(2, B, 0), node(3, C, 0));

        selector.select();

        verify(mapper, times(1)).readValue(eq(options), any(TypeReference.class));
        verifyNoInteractions(mastery);
    }

    @Test
    void cleansBeforeZeroLimitWithoutReadingCandidatesOrMastery() {
        when(config.maxBatchesPerRun()).thenReturn(0);

        assertThat(selector.select()).isEmpty();
        verify(repository).failStale(NOW);
        verify(repository, never()).wrongAnswers(any(), any());
        verify(repository, never()).nodes(any());
        verify(repository, never()).activeRuns();
        verifyNoInteractions(mastery);
        verify(transactions, times(1)).commit(any());
    }

    @Test
    void emptyOrBelowThresholdRowsSkipMetadataAndActiveReads() {
        wrong(row(1, A, 19));

        assertThat(selector.select()).isEmpty();
        verify(repository, never()).nodes(any());
        verify(repository, never()).activeRuns();
        verifyNoInteractions(mastery);
    }

    @Test
    void emptyWrongHistoryReturnsNoCandidates() {
        wrong();

        assertThat(selector.select()).isEmpty();
        verify(repository).failStale(NOW);
        verify(repository, never()).nodes(any());
        verify(repository, never()).activeRuns();
        verifyNoInteractions(mastery);
    }

    @Test
    void readsEachRelevantSettingOnceAndUsesSeparateTransactionDefinitions() {
        wrong(row(1, A, 20));
        nodes(node(1, A, 0));

        selector.select();

        verify(config).windowDays();
        verify(config).minWrong();
        verify(config).minBank();
        verify(config).maxBatchesPerRun();
        verify(config, never()).systemUserId();
        verify(config, never()).batchSize();
        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactions, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allSatisfy(definition -> assertThat(definition.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        assertThat(definitions.getAllValues().get(0).isReadOnly()).isFalse();
        assertThat(definitions.getAllValues().get(0).getIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThat(definitions.getAllValues().get(1).isReadOnly()).isTrue();
        assertThat(definitions.getAllValues().get(1).getIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var order = inOrder(repository, transactions);
        order.verify(repository).failStale(NOW);
        order.verify(transactions).commit(any());
        order.verify(repository).wrongAnswers(any(), any());
    }

    @Test
    void cleanupFailureRollsBackAndPreventsSelection() {
        when(repository.failStale(NOW)).thenThrow(new IllegalStateException("cleanup failed"));

        assertThatThrownBy(selector::select).hasMessage("cleanup failed");
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
        verify(repository, never()).wrongAnswers(any(), any());
    }

    @Test
    void laterReadFailureDoesNotRollbackCommittedCleanup() {
        when(repository.wrongAnswers(any(), any())).thenThrow(new IllegalStateException("read failed"));

        assertThatThrownBy(selector::select).hasMessage("read failed");
        var order = inOrder(transactions, repository);
        order.verify(repository).failStale(NOW);
        order.verify(transactions).commit(any());
        order.verify(repository).wrongAnswers(any(), any());
        order.verify(transactions).rollback(any());
    }

    @Test
    void doesNotComputeMasteryWhenAllCandidatesFitEvenWithTies() {
        wrong(row(1, A, 20), row(2, B, 20), row(3, C, 20));
        nodes(node(3, C, 0), node(2, B, 0), node(1, A, 0));

        assertThat(selector.select()).extracting(Candidate::kid).containsExactly(A, B, C);
        verifyNoInteractions(mastery);
    }

    @Test
    void doesNotComputeMasteryForTiesAwayFromCutoff() {
        when(config.maxBatchesPerRun()).thenReturn(2);
        wrong(row(1, A, 30), row(2, B, 30), row(3, C, 20), row(4, D, 20));
        nodes(node(1, A, 0), node(2, B, 0), node(3, C, 0), node(4, D, 0));

        assertThat(selector.select()).extracting(Candidate::kid).containsExactly(A, B);
        verifyNoInteractions(mastery);
    }

    @Test
    void computesOnlyWholeBoundaryGroupAndCountsReviewUsersPerContributedKid() {
        when(config.maxBatchesPerRun()).thenReturn(2);
        wrong(row(9, D, 40), row(1, A, 20), row(2, B, 10), row(3, B, 10), row(4, C, 20));
        nodes(node(1, A, 0), node(2, B, 0), node(3, C, 0), node(4, D, 0));
        Set<String> tied = Set.of(A, B, C);
        when(mastery.valuesOf(1, tied)).thenReturn(Map.of(A, review(), B, review()));
        when(mastery.valuesOf(2, tied)).thenReturn(Map.of(B, review()));
        when(mastery.valuesOf(3, tied)).thenReturn(Map.of(B, review()));
        when(mastery.valuesOf(4, tied)).thenReturn(Map.of(C, new MasteryView(null, "insufficient")));

        assertThat(selector.select()).containsExactly(new Candidate(4, D, 40, 0, 0),
                new Candidate(2, B, 20, 0, 2));
        verify(mastery, never()).valuesOf(eq(9L), any());
        verify(mastery, times(4)).valuesOf(anyLong(), eq(tied));
    }

    @Test
    void repeatedItemsAndBothLabelsDoNotMultiplyReviewUserCount() {
        when(config.maxBatchesPerRun()).thenReturn(1);
        wrong(new WrongAnswer(1, 1, "B", A, options(A, C), 10), row(1, B, 20));
        nodes(node(1, A, 0), node(2, B, 0));
        when(mastery.valuesOf(1, Set.of(A, B))).thenReturn(Map.of(A, review(), B, review()));

        assertThat(selector.select()).containsExactly(new Candidate(1, A, 20, 0, 1));
        verify(mastery, times(1)).valuesOf(1, Set.of(A, B));
    }

    @Test
    void reviewCannotQualifyRawBelowThresholdOrOutrankGreaterRawTotal() {
        when(config.maxBatchesPerRun()).thenReturn(2);
        wrong(row(1, A, 19), row(2, B, 21), row(3, C, 20), row(4, D, 20));
        nodes(node(2, B, 0), node(3, C, 0), node(4, D, 0));
        when(mastery.valuesOf(3, Set.of(C, D))).thenReturn(Map.of(C, new MasteryView(null, "notyet")));
        when(mastery.valuesOf(4, Set.of(C, D))).thenReturn(Map.of(D, review()));

        assertThat(selector.select()).containsExactly(new Candidate(2, B, 21, 0, 0),
                new Candidate(4, D, 20, 0, 1));
        verify(repository).nodes(Set.of(B, C, D));
        verify(mastery, never()).valuesOf(eq(1L), any());
        verify(mastery, never()).valuesOf(eq(2L), any());
    }

    @Test
    void missingAndOtherMasteryStatesLeaveDeterministicKidOrder() {
        when(config.maxBatchesPerRun()).thenReturn(1);
        wrong(row(1, A, 20), row(2, B, 20), row(3, C, 20));
        nodes(node(3, C, 0), node(2, B, 0), node(1, A, 0));
        Set<String> tied = Set.of(A, B, C);
        when(mastery.valuesOf(1, tied)).thenReturn(Map.of());
        when(mastery.valuesOf(2, tied)).thenReturn(Map.of(B, new MasteryView(null, "notyet")));
        when(mastery.valuesOf(3, tied)).thenReturn(Map.of(C, new MasteryView(50, "consolidate")));

        assertThat(selector.select()).containsExactly(new Candidate(1, A, 20, 0, 0));
    }

    @Test
    void capsMasteryAtOneHundredUsersByUniqueItemsThenUserId() {
        when(config.maxBatchesPerRun()).thenReturn(1);
        List<WrongAnswer> rows = new ArrayList<>();
        // User 999 has four raw contributions but only two wrong items.
        rows.add(new WrongAnswer(999, 1, "B", A, options(A, C), 2));
        rows.add(new WrongAnswer(998, 2, "B", B, options(A, C), 3));
        for (long user = 1; user <= 200; user++) rows.add(row(user, A, 3));
        rows.add(row(1000, B, 604));
        wrong(rows.toArray(WrongAnswer[]::new));
        nodes(node(1, A, 0), node(2, B, 0));
        when(mastery.valuesOf(anyLong(), eq(Set.of(A, B)))).thenReturn(Map.of(A, review(), B, review()));

        assertThat(selector.select()).containsExactly(new Candidate(1, A, 607, 0, 99));
        ArgumentCaptor<Long> users = ArgumentCaptor.forClass(Long.class);
        verify(mastery, times(100)).valuesOf(users.capture(), eq(Set.of(A, B)));
        List<Long> expected = new ArrayList<>();
        expected.add(1000L);
        for (long user = 1; user <= 99; user++) expected.add(user);
        assertThat(users.getAllValues()).containsExactlyElementsOf(expected);
    }

    @Test
    void skipsMissingContentPendingAndActiveNodesBeforeMastery() {
        wrong(row(1, A, 20), row(2, B, 20), row(3, C, 20), row(4, D, 20));
        nodes(new NodeSummary(1, A, "  ", 0, true, false),
                new NodeSummary(2, B, "Can do", 0, false, false),
                new NodeSummary(3, C, "Can do", 0, true, true), node(4, D, 0));
        when(repository.activeRuns()).thenReturn(List.of(new ActiveRun(9,
                "{\"kid\":\"" + D + "\",\"knowledgePointId\":4}")));

        assertThat(selector.select()).isEmpty();
        verifyNoInteractions(mastery);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", "{}", "{\"kid\":\"TV1-001\"}",
            "{\"kid\":\"TV1-001\",\"knowledgePointId\":0}",
            "{\"kid\":\"TV1-001\",\"knowledgePointId\":1.5}",
            "{\"kid\":\"TV1-001\",\"knowledgePointId\":2}",
            "{\"kid\":\"TV1-002\",\"knowledgePointId\":1}",
            "{\"kid\":\"TV1-001\",\"knowledgePointId\":9223372036854775808}"})
    void malformedOrInconsistentActivePayloadFailsClosed(String json) {
        wrong(row(1, A, 20));
        nodes(node(1, A, 0));
        when(repository.activeRuns()).thenReturn(List.of(new ActiveRun(9, json)));

        assertThatThrownBy(selector::select).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(json).hasNoCause();
        verifyNoInteractions(mastery);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[null]", "[{\"key\":\"B\"},{\"key\":\"B\"}]"})
    void malformedOptionsFailClosedWithoutRawDataInException(String json) {
        wrong(new WrongAnswer(1, 1, "B", A, json, 20));

        assertThatThrownBy(selector::select).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(json).hasNoCause();
        verify(repository, never()).nodes(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[null]", "[{\"key\":\"B\"},{\"key\":\"B\"}]"})
    void legacyOptionsDropTheWholeQuestionAndWarnOnceAcrossGroupedUsers(String json,
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        wrong(new WrongAnswer(1, 77, "B", A, json, 50),
                new WrongAnswer(2, 77, "B", A, json, 50), row(3, B, 20));
        nodes(node(1, A, 0), node(2, B, 0));

        assertThat(selector.selectForGeneration()).containsExactly(new Candidate(2, B, 20, 0, 0));
        verify(repository).nodes(Set.of(B));
        verify(mapper, times(1)).readTree(json);
        assertThat(output.getAll().lines().filter(line -> line.contains("WARN") && line.contains("77"))).hasSize(1);
    }

    @Test
    void missingOptionsSkipPrimaryAndKeepValidSibling() {
        wrong(new WrongAnswer(1, 78, "B", A, null, 100), row(2, B, 20));
        nodes(node(1, A, 0), node(2, B, 0));

        assertThat(selector.selectForGeneration()).containsExactly(new Candidate(2, B, 20, 0, 0));
        verify(repository).nodes(Set.of(B));
    }

    @Test
    void generationSelectionPreservesStrictResultsAndBothValidContributions() {
        wrong(new WrongAnswer(1, 90, "B", A, options(A, C), 10), row(2, B, 20));
        nodes(node(1, A, 0), node(2, B, 0));
        var strict = selector.select();

        assertThat(selector.selectForGeneration()).isEqualTo(strict)
                .containsExactly(new Candidate(1, A, 20, 0, 0), new Candidate(2, B, 20, 0, 0));
    }

    private void wrong(WrongAnswer... rows) {
        when(repository.wrongAnswers(any(), any())).thenReturn(List.of(rows));
    }

    private void nodes(NodeSummary... nodes) {
        when(repository.nodes(any())).thenAnswer(invocation -> {
            Set<String> requested = invocation.getArgument(0);
            return List.of(nodes).stream().filter(node -> requested.contains(node.kid())).toList();
        });
    }

    private static WrongAnswer row(long user, String kid, long count) {
        return new WrongAnswer(user, user + 10000, "X", kid, "[]", count);
    }

    private static NodeSummary node(long id, String kid, long bank) {
        return new NodeSummary(id, kid, "Can do", bank, true, false);
    }

    private static MasteryView review() {
        return new MasteryView(30, "review");
    }

    private static String options(String selectedError, String otherError) {
        return "[{\"key\":\"A\",\"text\":\"Right\",\"is_correct\":true,\"error_kid\":\"" + D
                + "\"},{\"key\":\"B\",\"text\":\"Wrong\",\"is_correct\":false,\"error_kid\":\""
                + selectedError + "\"},{\"key\":\"C\",\"text\":\"Other\",\"is_correct\":false,"
                + "\"error_kid\":\"" + otherError + "\"}]";
    }
}
