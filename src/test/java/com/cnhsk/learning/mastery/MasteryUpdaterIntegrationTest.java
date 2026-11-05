package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.mastery.MasteryListenerIntegrationTest.Fixtures;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class MasteryUpdaterIntegrationTest extends AbstractIntegrationTest {
    @Autowired PracticeService practice;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean Clock clock;
    @MockitoBean(name="topicTestHandler") PracticeSourceHandler handler;
    Fixtures fixtures;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(MasteryListenerIntegrationTest.NOW);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_TEST);
        when(handler.onFinish(any())).thenReturn(Map.of());
        fixtures = new Fixtures(jdbc,practice);
    }
    @Test void AT12_concurrentFinishOfNewRowKeepsBothEvidenceIds() throws Exception {
        long user = fixtures.user();
        long kid = fixtures.node();
        concurrent(user, List.of(fixtures.question(kid)), List.of(fixtures.question(kid)));
        assertThat(ids(user,kid)).hasSize(2).doesNotHaveDuplicates();
    }
    @Test void AT12_reverseItemOrderAcrossTwoKidsDoesNotDeadlock() throws Exception {
        long user = fixtures.user();
        long a = fixtures.node();
        long b = fixtures.node();
        concurrent(user,List.of(fixtures.question(a),fixtures.question(b)),
                List.of(fixtures.question(b),fixtures.question(a)));
        assertThat(ids(user,a)).hasSize(2);
        assertThat(ids(user,b)).hasSize(2);
    }
    @Test void AT12_ninthQuizEvidenceEvictsOldestAndSnapshotsCompareArrayValues() {
        long user = fixtures.user();
        long kid = fixtures.node();
        long session = fixtures.session(user,IntStream.range(0,9).mapToObj(i -> fixtures.question(kid)).toList());
        practice.finish(user,session,IntStream.rangeClosed(1,9).mapToObj(Fixtures::answer).toList());
        List<Long> all = jdbc.queryForList("SELECT id FROM learning.attempt_items WHERE attempt_id=? ORDER BY answered_at DESC,id DESC",Long.class,session);
        assertThat(ids(user,kid)).containsExactlyElementsOf(all.subList(0,8));
        var first = snapshot(user,kid);
        var second = snapshot(user,kid);
        assertThat(second).isEqualTo(first);
    }
    private void concurrent(long user,List<Long> a,List<Long> b) throws Exception {
        long first = fixtures.session(user,a);
        long second = fixtures.session(user,b);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> { start.await(); return practice.finish(user,first,IntStream.rangeClosed(1,a.size()).mapToObj(Fixtures::answer).toList()); });
            var right = executor.submit(() -> { start.await(); return practice.finish(user,second,IntStream.rangeClosed(1,b.size()).mapToObj(Fixtures::answer).toList()); });
            start.countDown();
            assertThat(left.get(30,TimeUnit.SECONDS).status()).isEqualTo("SUBMITTED");
            assertThat(right.get(30,TimeUnit.SECONDS).status()).isEqualTo("SUBMITTED");
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    private List<Long> ids(long user,long kid) {
        return jdbc.queryForObject("SELECT evidence_item_ids FROM learning.user_progress WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=?",
                (row,no) -> Arrays.asList((Long[])row.getArray(1).getArray()),user,kid);
    }
    private Map<String,Object> snapshot(long user,long kid) {
        Map<String,Object> row = jdbc.queryForMap("SELECT * FROM learning.user_progress WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=?",user,kid);
        row.replaceAll((key,value) -> {
            if (value instanceof java.sql.Array array) {
                try { return Arrays.asList((Object[])array.getArray()); }
                catch (java.sql.SQLException error) { throw new AssertionError(error); }
            }
            return value;
        });
        return row;
    }
}
