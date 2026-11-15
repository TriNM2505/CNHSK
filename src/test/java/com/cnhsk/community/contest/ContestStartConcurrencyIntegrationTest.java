package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class ContestStartConcurrencyIntegrationTest extends ContestFixtures {
    @Test
    void AT09_threeConcurrentUsersCanOnlyTakeTwoPlaces() throws Exception {
        jdbc.update("UPDATE community.contests SET max_participants=2 WHERE id=?", contest);
        var results = together(List.of(learner, user(true), user(true)), false);
        assertThat(results.stream().filter(result -> result.status() == 201).count()).isEqualTo(2);
        assertThat(results.stream().filter(result -> result.status() == 422).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> result.status() == 422).findFirst().orElseThrow().body().path("error_code").asText())
                .isEqualTo("CONTEST_FULL");
        assertThat(attempts.countAttempts(contest)).isEqualTo(2);
        for (var result : results) noPrivateFields(result.body());
    }

    @Test
    void FR011_twentyConcurrentRequestsFromOneUserCreateOnceAndResumeEvenAtCapacity() throws Exception {
        jdbc.update("UPDATE community.contests SET max_participants=1 WHERE id=?", contest);
        var results = together(Collections.nCopies(20, learner), false);
        assertThat(results.stream().filter(result -> result.status() == 201).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> result.status() == 200).count()).isEqualTo(19);
        long session = results.get(0).body().path("sessionId").asLong();
        for (var result : results) {
            assertThat(result.body().path("sessionId").asLong()).isEqualTo(session);
            assertThat(time(result.body(), "deadline")).isEqualTo(NOW.plusSeconds(900));
            assertThat(result.body().path("resumed").asBoolean()).isEqualTo(result.status() == 200);
        }
        assertThat(attempts.countAttempts(contest)).isEqualTo(1);
    }

    @Test
    void FR012_concurrentSubmissionsCommitOnceWithoutHoldingAnOuterAttemptLock() throws Exception {
        var session = response(enter(learner), 201);
        now = NOW.plusSeconds(30);
        var results = together(List.of(learner, learner), true);
        assertThat(results).extracting(Outcome::status).containsExactlyInAnyOrder(200, 409);
        var conflict = results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow();
        assertThat(conflict.body().path("error_code").asText()).isEqualTo("CONTEST_ALREADY_ATTEMPTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Long.class,
                session.path("sessionId").asLong())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id=?", Long.class, learner)).isEqualTo(1);
    }

    private List<Outcome> together(List<Long> callers, boolean submit) throws Exception {
        var executor = Executors.newFixedThreadPool(callers.size());
        var ready = new CountDownLatch(callers.size());
        var go = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (long caller : callers) {
                String token = bearer(caller);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                    var response = mvc.perform(post(BASE + "/{id}/attempts" + (submit ? "/submit" : ""), contest)
                            .header("Authorization", token)).andReturn().getResponse();
                    return new Outcome(response.getStatus(), mapper.readTree(response.getContentAsString()));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Outcome> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            go.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private record Outcome(int status, JsonNode body) { }
}
