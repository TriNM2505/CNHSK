package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingKey.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.cnhsk.learning.grading.GradingConfig;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SettingsGradingConsumerIntegrationTest extends SettingsConsumerTestSupport {
    @Autowired private GradingConfig config;

    @Test
    void policyAndEssayBoundsChangeAndResetWithoutChangingTheYamlCharacterLimit() throws Exception {
        JsonNode defaults = policy();
        assertThat(defaults.path("claimHours").asInt()).isEqualTo(defaultValue(GRADING_CLAIM_HOURS));
        assertThat(defaults.path("gradingHours").asInt()).isEqualTo(defaultValue(GRADING_GRADING_HOURS));
        assertThat(defaults.path("minHanzi").asInt()).isEqualTo(defaultValue(GRADING_MIN_HANZI));
        assertThat(defaults.path("maxHanzi").asInt()).isEqualTo(defaultValue(GRADING_MAX_HANZI));
        assertThat(defaults.path("maxChars").asInt()).isEqualTo(config.getMaxChars());
        change(GRADING_MIN_HANZI, 5);
        change(GRADING_MAX_HANZI, 10);
        assertThat(policy().path("minHanzi").asInt()).isEqualTo(5);
        assertThat(policy().path("maxHanzi").asInt()).isEqualTo(10);
        assertThat(submit(4, 422).path("error_code").asText()).isEqualTo("GRADING_INVALID_ESSAY");
        assertThat(submit(11, 422).path("error_code").asText()).isEqualTo("GRADING_INVALID_ESSAY");
        submit(5, 201);
        submit(10, 201);
        reset(GRADING_MAX_HANZI);
        reset(GRADING_MIN_HANZI);
        assertThat(submit(defaultValue(GRADING_MIN_HANZI) - 1, 422).path("error_code").asText()).isEqualTo("GRADING_INVALID_ESSAY");
        submit(defaultValue(GRADING_MIN_HANZI), 201);
        assertThat(policy().path("maxChars").asInt()).isEqualTo(config.getMaxChars());
    }

    @Test
    void claimAndGradingHoursApplyToNewTransitionsAndNeverRewriteStoredDeadlines() throws Exception {
        change(GRADING_CLAIM_HOURS, 2);
        Instant before = clock.instant();
        JsonNode first = submit(50, 201);
        Instant firstDeadline = time(first, "deadline");
        deadlineWithin(firstDeadline, before, 2);
        long firstId = first.path("id").asLong();
        change(GRADING_CLAIM_HOURS, 5);
        assertThat(time(learnerGet(firstId), "deadline")).isEqualTo(firstDeadline);
        before = clock.instant();
        JsonNode second = submit(50, 201);
        deadlineWithin(time(second, "deadline"), before, 5);
        reset(GRADING_CLAIM_HOURS);
        before = clock.instant();
        JsonNode third = submit(50, 201);
        deadlineWithin(time(third, "deadline"), before, defaultValue(GRADING_CLAIM_HOURS));
        change(GRADING_GRADING_HOURS, 3);
        before = clock.instant();
        JsonNode assigned = claim(firstId, 200);
        Instant stored = time(assigned, "gradingDeadline");
        deadlineWithin(stored, before, 3);
        change(GRADING_GRADING_HOURS, 6);
        assertThat(time(teacherGet(firstId), "gradingDeadline")).isEqualTo(stored);
        before = clock.instant();
        deadlineWithin(time(claim(second.path("id").asLong(), 200), "gradingDeadline"), before, 6);
        reset(GRADING_GRADING_HOURS);
        before = clock.instant();
        deadlineWithin(time(claim(third.path("id").asLong(), 200), "gradingDeadline"), before, defaultValue(GRADING_GRADING_HOURS));
        assertThat(time(teacherGet(firstId), "gradingDeadline")).isEqualTo(stored);
    }

    @Test
    void loweringAndRaisingTheActiveClaimLimitKeepsExistingAssignmentsAndResetAllowsMore() throws Exception {
        long first = submit(50, 201).path("id").asLong();
        long second = submit(50, 201).path("id").asLong();
        long third = submit(50, 201).path("id").asLong();
        change(GRADING_MAX_ACTIVE_CLAIMS, 1);
        claim(first, 200);
        assertThat(claim(second, 422).path("error_code").asText()).isEqualTo("GRADING_CLAIM_LIMIT");
        change(GRADING_MAX_ACTIVE_CLAIMS, 2);
        claim(second, 200);
        change(GRADING_MAX_ACTIVE_CLAIMS, 1);
        assertThat(claim(third, 422).path("error_code").asText()).isEqualTo("GRADING_CLAIM_LIMIT");
        assertThat(teacherGet(first).path("status").asText()).isEqualTo("ASSIGNED");
        assertThat(teacherGet(second).path("status").asText()).isEqualTo("ASSIGNED");
        reset(GRADING_MAX_ACTIVE_CLAIMS);
        claim(third, 200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.service_requests WHERE assignee_id=? AND status='ASSIGNED'", Integer.class, teacher)).isEqualTo(3);
    }

    @Test
    void payoutUsesTheCurrentSettingAtCompletionAndCompletedPayoutsRemainImmutable() throws Exception {
        change(GRADING_TEACHER_PAYOUT_CREDITS, 2);
        long first = submit(50, 201).path("id").asLong();
        assertThat(queueItem(first).path("payoutCredits").asInt()).isEqualTo(2);
        assertThat(claim(first, 200).path("payoutCredits").asInt()).isEqualTo(2);
        change(GRADING_TEACHER_PAYOUT_CREDITS, 3);
        assertThat(teacherGet(first).path("payoutCredits").asInt()).isEqualTo(3);
        assertThat(complete(first).path("payoutCredits").asInt()).isEqualTo(3);
        change(GRADING_TEACHER_PAYOUT_CREDITS, 7);
        call("GET", "/api/teacher/grading-requests/" + first, teacher, "TEACHER", null, 403);
        assertThat(jdbc.queryForObject("SELECT payout FROM learning.service_requests WHERE id=?", Integer.class, first)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM learning.credit_transactions WHERE user_id=? AND tx_type='TEACHER_PAYOUT'", Integer.class, teacher)).isEqualTo(3);
        long second = submit(50, 201).path("id").asLong();
        claim(second, 200);
        reset(GRADING_TEACHER_PAYOUT_CREDITS);
        assertThat(api.getInt(GRADING_TEACHER_PAYOUT_CREDITS)).isZero();
        assertThat(complete(second).path("payoutCredits").asInt()).isZero();
        assertThat(learnerGet(first).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=? AND tx_type='TEACHER_PAYOUT'", Integer.class, teacher)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payout FROM learning.service_requests WHERE id=?", Integer.class, first)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT payout FROM learning.service_requests WHERE id=?", Integer.class, second)).isZero();
    }

    private JsonNode policy() throws Exception { return call("GET", "/api/grading/policy", learner, "LEARNER", null, 200); }
    private JsonNode submit(int hanzi, int status) throws Exception {
        return call("POST", "/api/grading-requests", learner, "LEARNER", Map.of("essay", "字".repeat(hanzi) + " " + UUID.randomUUID()), status);
    }
    private JsonNode learnerGet(long id) throws Exception { return call("GET", "/api/grading-requests/" + id, learner, "LEARNER", null, 200); }
    private JsonNode teacherGet(long id) throws Exception { return call("GET", "/api/teacher/grading-requests/" + id, teacher, "TEACHER", null, 200); }
    private JsonNode claim(long id, int status) throws Exception { return call("POST", "/api/teacher/grading-requests/" + id + "/claim", teacher, "TEACHER", null, status); }
    private JsonNode complete(long id) throws Exception {
        return call("POST", "/api/teacher/grading-requests/" + id + "/complete", teacher, "TEACHER",
                Map.of("criteria", Map.of("grammar", 20, "vocabulary", 20, "coherence", 20, "writing", 20),
                        "feedback", "Nhận xét tự soạn", "corrections", List.of()), 200);
    }
    private JsonNode queueItem(long id) throws Exception {
        int page = 0;
        while (true) {
            JsonNode result = call("GET", "/api/teacher/grading-queue?page=" + page + "&size=50", teacher, "TEACHER", null, 200);
            for (JsonNode item : result.path("items")) if (item.path("id").asLong() == id) return item;
            assertThat((page + 1L) * 50).isLessThan(result.path("totalElements").asLong());
            page++;
        }
    }
    private Instant time(JsonNode node, String field) { return Instant.parse(node.path(field).asText()); }
    private void deadlineWithin(Instant deadline, Instant before, int hours) {
        assertThat(deadline).isBetween(before.plus(hours, ChronoUnit.HOURS).minusMillis(1), clock.instant().plus(hours, ChronoUnit.HOURS).plusMillis(1));
    }
}
