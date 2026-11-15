package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.mastery.MasteryReader;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Bounded measurements on isolated PostgreSQL, including the real mastery reader. */
@Import(WeakKnowledgeSelectorIntegrationTest.SqlConfiguration.class)
class WeakKnowledgeSelectorPerformanceIntegrationTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(WeakKnowledgeSelectorPerformanceIntegrationTest.class);
    private static final Instant NOW = WeakKnowledgeSelectorIntegrationTest.NOW;
    private static final int EXPECTED_MASTERY_USER_LIMIT = 100;
    @Autowired WeakKnowledgeSelector selector;
    @Autowired AiGenerateConfig config;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired StubClaudeClient stub;
    @Autowired WeakKnowledgeSelectorIntegrationTest.SqlCapture sql;
    @MockitoSpyBean MasteryReader mastery;
    @MockitoSpyBean WeakKnowledgeQueryRepository repository;
    @MockitoBean Clock clock;
    private WeakKnowledgeSelectorIntegrationTest.Fixtures fixtures;
    private final Map<String, Integer> rowCounts = new LinkedHashMap<>();
    private int providerCalls;

    @BeforeEach
    void prepare() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.millis()).thenReturn(NOW.toEpochMilli());
        fixtures = new WeakKnowledgeSelectorIntegrationTest.Fixtures(jdbc);
        providerCalls = stub.callCount();
        doAnswer(invocation -> captureRows("wrongAnswers", invocation.callRealMethod())).when(repository).wrongAnswers(any(), any());
        doAnswer(invocation -> captureRows("nodes", invocation.callRealMethod())).when(repository).nodes(any());
        doAnswer(invocation -> captureRows("activeRuns", invocation.callRealMethod())).when(repository).activeRuns();
        assertThat(config.windowDays()).isEqualTo(14);
        assertThat(config.minWrong()).isEqualTo(20);
        assertThat(config.minBank()).isEqualTo(10);
        assertThat(config.maxBatchesPerRun()).isEqualTo(3);
    }

    @AfterEach
    void cleanOwnedFixtures() {
        if (fixtures != null) fixtures.close();
        assertThat(stub.callCount()).isEqualTo(providerCalls);
        assertThat(config.getCron()).isEqualTo("-");
    }

    @Test
    void measures100000WrongItemsWithTwentyContributorsAndRealReviewEvidence() throws Exception {
        Dataset data = seed(20, 500);
        verifyRealReviewState(data);
        // Setup, snapshot serialization, evidence validation and the warmup are outside measured samples.
        sample(data, "warmup", false);
        long[] samples = new long[3];
        for (int i = 0; i < samples.length; i++) samples[i] = sample(data, "measured-" + (i + 1), true);
        long[] ordered = samples.clone(); Arrays.sort(ordered);
        double p50 = millis(ordered[1]), p95 = millis(ordered[2]);
        log.info("SELECTOR_NFR summary users={} wrongItems={} samplesMs={} p50Ms={} p95Ms={} maxMs={} "
                        + "nearestRankP95EqualsMax=true lowStatisticalPower=true targetMs=3000 assessment={} "
                        + "databaseImage=postgres:18-alpine java={} processors={}",
                data.users().size(), data.wrongItems(), Arrays.stream(samples).mapToDouble(WeakKnowledgeSelectorPerformanceIntegrationTest::millis).toArray(),
                p50, p95, p95, p95 < 3000 ? "TARGET_OBSERVED_FOR_FIXTURE" : "TARGET_NOT_OBSERVED_FOR_FIXTURE",
                System.getProperty("java.version"), Runtime.getRuntime().availableProcessors());
    }

    @Test
    void measuresReaderCallGrowthForOneHundredContributorsWithoutRepeatingHeavyFixture() throws Exception {
        Dataset data = seed(100, 4);
        verifyRealReviewState(data);
        sample(data, "100-user-growth", true);
    }

    @Test
    void measuresTwoHundredContributorsAtTheBoundaryTieWithRealReviewEvidence() throws Exception {
        Dataset data = seed(200, 4);
        verifyRealReviewState(data);
        sample(data, "200-user-boundary-tie-warmup", false);
        long elapsed = sample(data, "200-user-boundary-tie", true);
        log.info("SELECTOR_NFR summary fixture=200-user-boundary-tie configuredCap={} users={} wrongItems={} "
                        + "singleSample=true measuredSamples=1 elapsedMs={} targetMs=3000 assessment={}",
                EXPECTED_MASTERY_USER_LIMIT, data.users().size(), data.wrongItems(), millis(elapsed),
                millis(elapsed) < 3000 ? "TARGET_OBSERVED_FOR_FIXTURE" : "TARGET_NOT_OBSERVED_FOR_FIXTURE");
    }

    private long sample(Dataset data, String name, boolean measured) {
        sql.clear(); rowCounts.clear(); clearInvocations(mastery, repository);
        long started = System.nanoTime();
        List<WeakKnowledgeSelector.Candidate> selected = selector.select();
        long elapsed = System.nanoTime() - started;
        assertThat(selected).extracting(WeakKnowledgeSelector.Candidate::knowledgePointId)
                .containsExactly(data.nodes().get(0), data.nodes().get(1), data.nodes().get(2));
        assertThat(selected).extracting(WeakKnowledgeSelector.Candidate::wrongCount)
                .containsExactly(data.wrongItems() * 3L / 10, data.wrongItems() / 5L, data.wrongItems() / 5L);
        assertThat(selected).extracting(WeakKnowledgeSelector.Candidate::weakUsers)
                .containsExactly(0, selectedCount(data), Math.min(data.users().size() / 2, selectedCount(data)));
        assertThat(selected).extracting(WeakKnowledgeSelector.Candidate::bankCount).containsOnly(1L);
        long calls = mockingDetails(mastery).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("valuesOf")).count();
        assertThat(calls).isEqualTo(selectedCount(data));
        assertThat(mockingDetails(mastery).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("valuesOf"))
                .map(invocation -> invocation.<Long>getArgument(0)).toList())
                .containsExactlyElementsOf(data.users().subList(0, selectedCount(data)));
        Set<String> tied = Set.of(fixtures.kid(data.nodes().get(1)), fixtures.kid(data.nodes().get(2)), fixtures.kid(data.nodes().get(3)));
        for (var invocation : mockingDetails(mastery).getInvocations()) {
            if (invocation.getMethod().getName().equals("valuesOf")) assertThat(invocation.getArgument(1, Set.class)).isEqualTo(tied);
        }
        assertThat(rowCounts).containsEntry("wrongAnswers", data.users().size() * 5)
                .containsEntry("nodes", 5).containsEntry("activeRuns", 0);
        long ownReads = mockingDetails(repository).getInvocations().stream()
                .filter(invocation -> Set.of("wrongAnswers", "nodes", "activeRuns").contains(invocation.getMethod().getName())).count();
        assertThat(ownReads).isEqualTo(3);
        log.info("SELECTOR_NFR sample={} measured={} elapsedMs={} configuredCap={} users={} kids=5 questions=5 prereqEdges=0 "
                        + "wrongItems={} controlItems=3 progressRows={} evidenceItems={} masteryApiCalls={} "
                        + "repositoryReads={} hibernateSqlStatements={} repositoryReturnedRows={} totalDbRows=NOT_CAPTURED "
                        + "settings=REAL_CACHE_WARM settingsJdbcSql=NOT_CAPTURED elapsedIncludesSettingsCalls=true",
                name, measured, millis(elapsed), EXPECTED_MASTERY_USER_LIMIT, data.users().size(), data.wrongItems(), data.users().size() * 3 / 2,
                data.users().size() * 12, calls, ownReads, sql.count(), Map.copyOf(rowCounts));
        return elapsed;
    }

    private Dataset seed(int userCount, int scale) throws Exception {
        List<Long> nodes = new ArrayList<>(), questions = new ArrayList<>(), users = new ArrayList<>();
        for (int i = 0; i < 5; i++) nodes.add(fixtures.node());
        for (long node : nodes) questions.add(fixtures.question(node, "IMPORT", "APPROVED", null));
        // Multiple SECONDARY labels must not multiply raw groups, bank counts or mastery on wrong answers.
        for (int i = 0; i < 3; i++) {
            fixtures.label(questions.get(i), nodes.get((i + 2) % 5), "SECONDARY");
            fixtures.label(questions.get(i), nodes.get((i + 3) % 5), "SECONDARY");
        }
        int[] distribution = {3 * scale, 2 * scale, 2 * scale, 2 * scale, scale};
        for (int u = 0; u < userCount; u++) {
            long user = fixtures.user(); users.add(user);
            for (int n = 0; n < 5; n++) {
                long attempt = fixtures.attempt(user);
                boolean hasEvidence = n == 1 || n == 2 && u < userCount / 2;
                List<ServedItem> served = new ArrayList<>();
                for (int no = 1; no <= 8; no++) served.add(served(no, questions.get(n)));
                jdbc.update("UPDATE learning.attempts SET suspicious=false,served_items=?::jsonb WHERE id=?",
                        mapper.writeValueAsString(new ServedSnapshot(Map.of(), served)), attempt);
                for (int offset = 0; offset < distribution[n]; offset += 500) {
                    List<Object[]> batch = new ArrayList<>();
                    for (int no = offset + 1; no <= Math.min(offset + 500, distribution[n]); no++) {
                        Instant answered = no <= 8 ? NOW.minusSeconds((no % 3 + 1L) * 86400) : NOW;
                        batch.add(new Object[] {attempt, questions.get(n), no, Timestamp.from(answered)});
                    }
                    jdbc.batchUpdate("INSERT INTO learning.attempt_items(attempt_id,question_id,order_index,answered_at,answer,is_correct,item_type,skill) "
                            + "VALUES (?,?,?,?,'B',false,'QUESTION_CHOICE','READING')", batch);
                }
                if (hasEvidence) {
                    jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,mp_state,evidence_item_ids,next_review_at) "
                                    + "VALUES (?,'KNOWLEDGE_POINT',?,'review',ARRAY(SELECT id FROM learning.attempt_items "
                                    + "WHERE attempt_id=? ORDER BY order_index LIMIT 8),?)",
                            user, nodes.get(n), attempt, Timestamp.from(NOW.plusSeconds(86400)));
                }
            }
        }
        fixtures.items(users.get(0), questions.get(0), "B", true, NOW, 1);
        fixtures.items(users.get(0), questions.get(0), "B", null, NOW, 1);
        fixtures.items(users.get(0), questions.get(0), "B", false, NOW.minusSeconds(15 * 86400L), 1);
        int wrongItems = userCount * 10 * scale;
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items ai JOIN learning.attempts a ON a.id=ai.attempt_id "
                + "WHERE a.user_id=?", Long.class, users.get(0))).isEqualTo(10L * scale + 3);
        return new Dataset(List.copyOf(users), List.copyOf(nodes), wrongItems);
    }

    private void verifyRealReviewState(Dataset data) {
        String b = fixtures.kid(data.nodes().get(1)), c = fixtures.kid(data.nodes().get(2));
        var values = mastery.valuesOf(data.users().get(0), Set.of(b, c));
        assertThat(values.get(b).state()).isEqualTo("review");
        assertThat(values.get(b).value()).isZero();
        assertThat(values.get(c).state()).isEqualTo("review");
        assertThat(masteryIsReal()).isTrue();
    }

    private boolean masteryIsReal() { return mockingDetails(mastery).isSpy(); }
    private static int selectedCount(Dataset data) { return Math.min(data.users().size(), EXPECTED_MASTERY_USER_LIMIT); }
    private Object captureRows(String name, Object value) {
        rowCounts.put(name, ((List<?>) value).size()); return value;
    }
    private static double millis(long nanos) { return nanos / (double) TimeUnit.MILLISECONDS.toNanos(1); }
    private static ServedItem served(int no, long question) {
        return new ServedItem(no, ItemType.QUESTION_CHOICE, Skill.READING, null,
                new ServedItem.Prompt("Self-authored evidence", null, null, null, null, null, null, null, null),
                List.of(new ServedItem.Option("A", "Correct"), new ServedItem.Option("B", "Wrong")),
                "A", null, null, new ServedItem.Rules(0, 0, false), question);
    }
    private record Dataset(List<Long> users, List<Long> nodes, int wrongItems) { }
}
