package com.cnhsk.learning.grading.web;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.credit.LedgerService;
import com.cnhsk.learning.grading.GradingRepository;
import com.cnhsk.learning.grading.GradingRequestService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(TeacherGradingControllerIntegrationTest.TimeConfiguration.class)
class TeacherGradingControllerIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2035-11-14T10:00:00Z");
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    @Autowired private GradingRequestService requests;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoSpyBean private GradingRepository repository;
    @MockitoSpyBean private EntitlementApi entitlement;
    @MockitoSpyBean private LedgerService ledger;
    @MockitoSpyBean private com.cnhsk.shared.settings.SettingsService settings;
    private GradingRepository repositoryTarget;
    private EntitlementApi entitlementTarget;
    private LedgerService ledgerTarget;
    private com.cnhsk.shared.settings.SettingsService settingsTarget;
    private final List<Long> users = new ArrayList<>();
    private long learner;
    private long teacher;
    private long other;
    private int essayNumber;

    @BeforeEach void setup() {
        repositoryTarget = AopTestUtils.getUltimateTargetObject(repository);
        entitlementTarget = AopTestUtils.getUltimateTargetObject(entitlement);
        ledgerTarget = AopTestUtils.getUltimateTargetObject(ledger);
        settingsTarget = AopTestUtils.getUltimateTargetObject(settings);
        learner = user(); teacher = user(); other = user();
    }

    @AfterEach void cleanup() {
        reset(repositoryTarget, entitlementTarget, ledgerTarget, settingsTarget);
        for (long id : users) {
            var keys = redis.keys("billing:*:" + id + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
            jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?", id);
        }
        for (long id : users) jdbc.update("DELETE FROM learning.service_requests WHERE user_id=?", id);
        for (long id : users) jdbc.update("DELETE FROM auth.users WHERE id=?", id);
    }

    @Test void AT06_queueFiltersBeforePagingAndExposesOnlyMetadata() throws Exception {
        long baseline = repository.countQueue(teacher, NOW);
        long self = pending(teacher);
        long expired = pending(learner); deadline(expired, NOW.minusSeconds(1));
        long exact = pending(learner); deadline(exact, NOW);
        long held = pending(learner); claim(teacher, held, 200);
        long cancelled = pending(learner); requests.cancel(learner, cancelled);
        long late = pending(learner); deadline(late, NOW.plusSeconds(30));
        long first = pending(learner); deadline(first, NOW.plusSeconds(10));
        long second = pending(learner); deadline(second, NOW.plusSeconds(10));
        long imported = jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload,deadline_at) VALUES (?,'IMPORT','PENDING','{}',?) RETURNING id",
                Long.class, learner, Timestamp.from(NOW.plusSeconds(1)));
        var firstPage = json(mvc.perform(get("/api/teacher/grading-queue?page=0&size=2").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(baseline + 3))
                .andExpect(jsonPath("$.items.length()").value(2)).andReturn());
        assertThat(firstPage.path("page").intValue()).isZero(); assertThat(firstPage.path("size").intValue()).isEqualTo(2);
        List<JsonNode> items = new ArrayList<>();
        for (int page = 0; page * 50L < baseline + 3; page++) {
            var response = json(mvc.perform(get("/api/teacher/grading-queue?page=" + page + "&size=50")
                    .header("Authorization", bearer(teacher))).andExpect(status().isOk()).andReturn());
            response.get("items").forEach(items::add); noPrivateFields(response, true);
        }
        var ownIds = Set.of(first, second, late);
        assertThat(items.stream().map(item -> item.path("id").longValue()).filter(ownIds::contains).toList()).containsExactly(first, second, late);
        assertThat(items.stream().map(item -> item.path("id").longValue()).toList()).doesNotContain(self, expired, exact, held, cancelled, imported);
        var item = items.stream().filter(value -> value.path("id").longValue() == first).findFirst().orElseThrow();
        assertThat(item.path("reference").textValue()).isEqualTo(String.format("GR-%06d", first));
        assertThat(item.path("hanziCount").intValue()).isEqualTo(50); assertThat(item.path("payoutCredits").intValue()).isZero();
        assertThat(item.path("hskLevel").intValue()).isEqualTo(3); assertThat(item.path("sentAt").textValue()).isEqualTo(NOW.toString());
        assertThat(item.path("claimDeadline").textValue()).isEqualTo(NOW.plusSeconds(10).toString());
    }

    @Test void AT07_tenTeachersClaimTheSameRequestUsingRealParallelHttp() throws Exception {
        long id = pending(learner);
        var teachers = new ArrayList<Long>();
        var calls = new ArrayList<Callable<Integer>>();
        for (int index = 0; index < 10; index++) {
            long candidate = user(); teachers.add(candidate);
            calls.add(() -> claimStatus(candidate, id));
        }
        clearInvocations(entitlementTarget);
        var statuses = race(calls);
        assertThat(statuses).filteredOn(value -> value == 200).hasSize(1);
        assertThat(statuses).filteredOn(value -> value == 409).hasSize(9);
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("ASSIGNED"); assertThat(teachers).contains(row.assigneeId());
        assertThat(row.assignedAt()).isEqualTo(NOW); assertThat(row.deadline()).isEqualTo(NOW.plusSeconds(24 * 3600));
        assertThat(Instant.parse(row.payload().path("claimDeadline").textValue())).isEqualTo(NOW.plusSeconds(48 * 3600));
        assertThat(used(learner)).isEqualTo(1);
        for (long candidate : teachers) { assertThat(used(candidate)).isZero(); assertThat(ledgerRows(candidate)).isZero(); }
        verifyNoInteractions(entitlementTarget);
    }

    @Test void AT08_selfGradingIs403EvenWhenTheTeacherAlreadyHoldsThree() throws Exception {
        for (int index = 0; index < 3; index++) claim(teacher, pending(learner), 200);
        long self = pending(teacher);
        claim(teacher, self, 403);
        assertThat(repository.find(self).orElseThrow().status()).isEqualTo("PENDING");
        long fourth = pending(learner);
        claim(teacher, fourth, 422);
        assertThat(repository.countActiveClaims(teacher)).isEqualTo(3);
    }

    @Test void AT08_sameTeacherCompetesForTheLastSlotWithoutExceedingTheLimit() throws Exception {
        claim(teacher, pending(learner), 200); claim(teacher, pending(learner), 200);
        long first = pending(learner); long second = pending(learner);
        clearInvocations(entitlementTarget);
        assertThat(race(List.of(() -> claimStatus(teacher, first), () -> claimStatus(teacher, second))))
                .containsExactlyInAnyOrder(200, 422);
        assertThat(repository.countActiveClaims(teacher)).isEqualTo(3);
        assertThat(List.of(repository.find(first).orElseThrow().status(), repository.find(second).orElseThrow().status()))
                .containsExactlyInAnyOrder("ASSIGNED", "PENDING");
        assertThat(used(learner)).isEqualTo(4); verifyNoInteractions(entitlementTarget);
    }

    @Test void AT09_detailChecksAssignmentBeforeReturningContentAndNeverLeaksIdentity() throws Exception {
        long id = pending(learner);
        mvc.perform(get("/api/teacher/grading-requests/{id}", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
        var claimed = claim(teacher, id, 200);
        assertThat(claimed.path("essay").textValue()).startsWith("字".repeat(50));
        assertThat(claimed.path("status").textValue()).isEqualTo("ASSIGNED");
        assertThat(claimed.has("prompt")).isTrue(); assertThat(claimed.path("prompt").isNull()).isTrue();
        assertThat(claimed.has("result")).isTrue(); assertThat(claimed.path("result").isNull()).isTrue();
        noPrivateFields(claimed, false);
        JsonNode detail = json(mvc.perform(get("/api/teacher/grading-requests/{id}", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isOk()).andReturn());
        assertThat(detail).isEqualTo(claimed);
        mvc.perform(get("/api/teacher/grading-requests/{id}", id).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"))
                .andExpect(jsonPath("$.essay").doesNotExist());
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
        assertThat(repository.find(id).orElseThrow().assigneeId()).isEqualTo(teacher);
        var completedResult = mapper.createObjectNode();
        completedResult.putObject("criteria").put("grammar", 20).put("vocabulary", 20).put("coherence", 20).put("writing", 20);
        completedResult.putArray("corrections");
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThat(repository.complete(id, teacher, NOW, 80, "Nhận xét fixture tự soạn.", completedResult, 0)).isTrue());
        mvc.perform(get("/api/teacher/grading-requests/{id}", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
    }

    @Test void FR010_mineUsesDeadlineAndIdOrderAndKeepsOverdueAssignedRows() throws Exception {
        long later = pending(learner); long first = pending(learner); long second = pending(learner); long foreign = pending(learner);
        claim(teacher, later, 200); claim(teacher, first, 200); claim(teacher, second, 200); claim(other, foreign, 200);
        deadline(first, NOW.minusSeconds(1)); deadline(second, NOW.minusSeconds(1));
        var items = json(mvc.perform(get("/api/teacher/grading-requests/mine").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk()).andReturn());
        assertThat(ids(items)).containsExactly(first, second, later); noPrivateFields(items, true);
        assertThat(items.get(0).path("gradingDeadline").textValue()).isEqualTo(NOW.minusSeconds(1).toString());
        assertThat(items.get(0).path("assignedAt").textValue()).isEqualTo(NOW.toString());
        claim(teacher, pending(learner), 422);
    }

    @Test void AT10_releaseToPendingRestoresClaimDeadlineClearsAssignmentAndDoesNotRefund() throws Exception {
        long id = pending(learner); claim(teacher, id, 200);
        clearInvocations(repositoryTarget, entitlementTarget);
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("PENDING"); assertThat(row.assigneeId()).isNull(); assertThat(row.assignedAt()).isNull();
        assertThat(row.deadline()).isEqualTo(NOW.plusSeconds(48 * 3600)); assertThat(row.completedAt()).isNull();
        assertThat(used(learner)).isEqualTo(1); verifyNoInteractions(entitlementTarget);
        verify(repositoryTarget, never()).lockTeacherClaim(anyLong());
        mvc.perform(get("/api/teacher/grading-requests/mine").header("Authorization", bearer(teacher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
        claim(other, id, 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTA", "CREDIT"})
    void AT10_expiredReleaseKeepsTheAssigneeAndRefundsTheLearnerOnce(String source) throws Exception {
        boolean credit = source.equals("CREDIT"); if (credit) { usage(learner, 10); topup(learner); }
        long id = pending(learner); assignedWithPastClaimDeadline(id, NOW.minusSeconds(1));
        clearInvocations(repositoryTarget, entitlementTarget);
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("EXPIRED"); assertThat(row.assigneeId()).isEqualTo(teacher);
        assertThat(row.assignedAt()).isEqualTo(NOW.minusSeconds(2 * 3600)); assertThat(row.completedAt()).isEqualTo(NOW);
        assertThat(row.deadline()).isEqualTo(NOW.plusSeconds(22 * 3600));
        assertThat(used(learner)).isEqualTo(credit ? 10 : 0); assertThat(balance(learner)).isEqualTo(credit ? 1 : 0);
        assertThat(refunds(learner)).isEqualTo(credit ? 1 : 0); assertThat(ledgerRows(teacher)).isZero();
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
        mvc.perform(get("/api/teacher/grading-requests/{id}", id).header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        mvc.perform(get("/api/grading-requests/{id}", id).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED")).andExpect(jsonPath("$.refunded").value(true));
        verify(entitlementTarget, times(1)).refund(eq(learner), any());
        verify(entitlementTarget, never()).consume(anyLong(), any(), anyString());
        verify(repositoryTarget, never()).lockTeacherClaim(anyLong());
    }

    @Test void AT10_releaseAtTheOriginalDeadlineAlsoExpires() throws Exception {
        long id = pending(learner); assignedWithPastClaimDeadline(id, NOW);
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isNoContent());
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo("EXPIRED"); assertThat(used(learner)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTA", "CREDIT"})
    void AT10_refundFailureRollsBackTheEntireReleaseAndRetrySucceeds(String source) throws Exception {
        boolean credit = source.equals("CREDIT"); if (credit) { usage(learner, 10); topup(learner); }
        long id = pending(learner); assignedWithPastClaimDeadline(id, NOW.minusSeconds(1));
        var before = repository.find(id).orElseThrow();
        doThrow(new IllegalStateException("fixture refund failure")).when(entitlementTarget).refund(eq(learner), any());
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isInternalServerError());
        assertThat(repository.find(id).orElseThrow()).isEqualTo(before);
        assertThat(used(learner)).isEqualTo(credit ? 10 : 1); assertThat(balance(learner)).isZero(); assertThat(refunds(learner)).isZero();
        doCallRealMethod().when(entitlementTarget).refund(eq(learner), any());
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isNoContent());
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo("EXPIRED");
        assertThat(used(learner)).isEqualTo(credit ? 10 : 0); assertThat(balance(learner)).isEqualTo(credit ? 1 : 0);
    }

    @Test void AT10_twoReleasesHaveOneWinnerAndOneCreditRefundWithoutAClaimLock() throws Exception {
        usage(learner, 10); topup(learner);
        long id = pending(learner); assignedWithPastClaimDeadline(id, NOW.minusSeconds(1));
        clearInvocations(repositoryTarget, entitlementTarget);
        Callable<Integer> release = () -> mvc.perform(post("/api/teacher/grading-requests/{id}/release", id)
                .header("Authorization", bearer(teacher))).andReturn().getResponse().getStatus();
        assertThat(race(List.of(release, release))).containsExactlyInAnyOrder(204, 403);
        assertThat(refunds(learner)).isEqualTo(1); assertThat(balance(learner)).isEqualTo(1);
        verify(entitlementTarget, times(1)).refund(eq(learner), any());
        verify(repositoryTarget, never()).lockTeacherClaim(anyLong());
    }

    @Test void AT10_lostConditionalReleaseDoesNotCallRefund() throws Exception {
        long id = pending(learner); assignedWithPastClaimDeadline(id, NOW.minusSeconds(1));
        clearInvocations(entitlementTarget);
        doReturn(false).when(repositoryTarget).release(eq(id), eq(teacher), any());
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo("ASSIGNED"); verifyNoInteractions(entitlementTarget);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808", "9223372036854775807"})
    void Q_T03_02_invalidAndMissingIdsUseTheContractStatus(String id) throws Exception {
        mvc.perform(post("/api/teacher/grading-requests/" + id + "/claim").header("Authorization", bearer(teacher)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("GRADING_ALREADY_CLAIMED"));
        mvc.perform(get("/api/teacher/grading-requests/" + id).header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
        mvc.perform(post("/api/teacher/grading-requests/" + id + "/release").header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
    }

    @Test void Q_T03_02_otherRequestKindsAreNotGradingRequests() throws Exception {
        long id = jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload) VALUES (?,'IMPORT','PENDING','{}') RETURNING id", Long.class, learner);
        claim(teacher, id, 409);
        mvc.perform(get("/api/teacher/grading-requests/{id}", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=", "page=abc", "size=0", "size=51", "size=abc", "size="})
    void FR009_invalidPagingIs422(String query) throws Exception {
        mvc.perform(get("/api/teacher/grading-queue?" + query).header("Authorization", bearer(teacher)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "ADMIN", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT"})
    void AT17_eachOfTheFiveEndpointsRequiresTeacher(String role) throws Exception {
        for (var request : List.of(get("/api/teacher/grading-queue"), get("/api/teacher/grading-requests/mine"),
                get("/api/teacher/grading-requests/1"), post("/api/teacher/grading-requests/1/claim"), post("/api/teacher/grading-requests/1/release"))) {
            mvc.perform(request.header("Authorization", bearer(teacher, role))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test void AT17_cookieWritesNeedCsrfAndCompleteRequiresAValidBody() throws Exception {
        long id = pending(learner);
        mvc.perform(get("/api/teacher/grading-queue")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/teacher/grading-requests/{id}/claim", id)).andExpect(status().isUnauthorized());
        Cookie access = new Cookie("access_token", token(teacher, "TEACHER"));
        mvc.perform(post("/api/teacher/grading-requests/{id}/claim", id).cookie(access))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post("/api/teacher/grading-requests/{id}/claim", id).cookie(access, new Cookie("XSRF-TOKEN", "teacher-csrf"))
                .header("X-XSRF-TOKEN", "wrong")).andExpect(status().isForbidden());
        mvc.perform(post("/api/teacher/grading-requests/{id}/claim", id).cookie(access, new Cookie("XSRF-TOKEN", "teacher-csrf"))
                .header("X-XSRF-TOKEN", "teacher-csrf")).andExpect(status().isOk());
        mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("GRADING_INVALID_RESULT"));
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).cookie(access)).andExpect(status().isForbidden());
        mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).cookie(access, new Cookie("XSRF-TOKEN", "teacher-csrf"))
                .header("X-XSRF-TOKEN", "teacher-csrf")).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTA", "CREDIT"})
    void AT12_completeUsesServerScoresAndPayoutAndLearnerSeesOnlyTheResult(String source) throws Exception {
        boolean credit = source.equals("CREDIT"); if (credit) { usage(learner, 10); topup(learner); }
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long id = pending(learner); claim(teacher, id, 200);
        var receipt = repository.find(id).orElseThrow().payload().get("consumption").deepCopy();
        clearInvocations(repositoryTarget, entitlementTarget, ledgerTarget);
        var completed = json(mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id)
                .header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON).content(resultBody()))
                .andExpect(status().isOk()).andReturn());
        assertThat(completed.path("status").textValue()).isEqualTo("COMPLETED");
        assertThat(completed.path("payoutCredits").intValue()).isEqualTo(2);
        assertThat(completed.path("result").path("score").intValue()).isEqualTo(78);
        assertThat(completed.path("result").path("feedback").textValue()).isEqualTo(resultFeedback());
        assertThat(completed.path("result").path("corrections").get(0).path("original").textValue()).isEqualTo(" 字 \n");
        var row = repository.find(id).orElseThrow();
        assertThat(row.score()).isEqualTo(78); assertThat(row.payout()).isEqualTo(2); assertThat(row.completedAt()).isEqualTo(NOW);
        assertThat(row.payload().get("consumption")).isEqualTo(receipt);
        assertThat(row.result().path("criteria").path("grammar").intValue()).isEqualTo(20);
        var entry = jdbc.queryForMap("SELECT user_id,amount,feature_code,request_id FROM learning.credit_transactions "
                + "WHERE tx_type='TEACHER_PAYOUT' AND request_id=?", id);
        assertThat(((Number) entry.get("user_id")).longValue()).isEqualTo(teacher);
        assertThat(((Number) entry.get("amount")).intValue()).isEqualTo(2);
        assertThat(entry.get("feature_code")).isEqualTo("GRADING");
        assertThat(((Number) entry.get("request_id")).longValue()).isEqualTo(id);
        assertThat(balance(teacher)).isEqualTo(2); assertThat(payoutRows(id)).isEqualTo(1);
        assertThat(used(learner)).isEqualTo(credit ? 10 : 1); assertThat(balance(learner)).isZero();
        assertThat(refunds(learner)).isZero(); assertThat(repository.countActiveClaims(teacher)).isZero();
        var learnerView = json(mvc.perform(get("/api/grading-requests/{id}", id).header("Authorization", bearer(learner, "LEARNER")))
                .andExpect(status().isOk()).andReturn());
        assertThat(learnerView.path("result")).isEqualTo(completed.path("result"));
        assertThat(learnerView.path("refunded").booleanValue()).isFalse(); noPrivateFields(learnerView, false);
        assertThat(learnerView.has("payoutCredits")).isFalse(); assertThat(learnerView.has("assignedAt")).isFalse();
        assertThat(completeStatus(id)).isEqualTo(403); assertThat(payoutRows(id)).isEqualTo(1);
        mvc.perform(get("/api/teacher/grading-requests/{id}", id).header("Authorization", bearer(teacher)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
        verifyNoInteractions(entitlementTarget); verify(repositoryTarget, never()).lockTeacherClaim(anyLong());
        verify(ledgerTarget, times(1)).append(any());
    }

    @Test void AT12_twoRealParallelHttpSubmissionsCreateExactlyOnePayout() throws Exception {
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long id = pending(learner); claim(teacher, id, 200); clearInvocations(entitlementTarget, ledgerTarget);
        assertThat(race(List.of(() -> completeStatus(id), () -> completeStatus(id)))).containsExactlyInAnyOrder(200, 403);
        assertThat(payoutRows(id)).isEqualTo(1); assertThat(balance(teacher)).isEqualTo(2);
        assertThat(repository.find(id).orElseThrow().score()).isEqualTo(78);
        verify(ledgerTarget, times(1)).append(any()); verifyNoInteractions(entitlementTarget);
    }

    @Test void SC003_oneHundredSubmissionsInParallelBatchesStillProduceOnePayout() throws Exception {
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long id = pending(learner); claim(teacher, id, 200); clearInvocations(entitlementTarget, ledgerTarget);
        var results = new ArrayList<Integer>();
        for (int batch = 0; batch < 10; batch++) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int index = 0; index < 10; index++) calls.add(() -> completeStatus(id));
            results.addAll(race(calls));
        }
        assertThat(results).hasSize(100); assertThat(results).filteredOn(value -> value == 200).hasSize(1);
        assertThat(results).filteredOn(value -> value == 403).hasSize(99);
        assertThat(payoutRows(id)).isEqualTo(1); assertThat(balance(teacher)).isEqualTo(2);
        verify(ledgerTarget, times(1)).append(any()); verifyNoInteractions(entitlementTarget);
    }

    @Test void AT12_zeroPayoutDoesNotCallOrWriteTheLedger() throws Exception {
        long id = pending(learner); claim(teacher, id, 200); clearInvocations(entitlementTarget, ledgerTarget);
        assertThat(completeStatus(id)).isEqualTo(200);
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("COMPLETED"); assertThat(row.payout()).isZero();
        assertThat(row.score()).isEqualTo(78); assertThat(ledgerRows(teacher)).isZero();
        assertThat(used(learner)).isEqualTo(1); verifyNoInteractions(ledgerTarget, entitlementTarget);
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTA", "CREDIT"})
    void FR015_failureAfterRealLedgerInsertRollsBackEverythingAndRetryPaysOnce(String source) throws Exception {
        boolean credit = source.equals("CREDIT"); if (credit) { usage(learner, 10); topup(learner); }
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long id = pending(learner); claim(teacher, id, 200);
        var before = repository.find(id).orElseThrow(); clearInvocations(entitlementTarget);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Fixture payout unavailable"); })
                .when(ledgerTarget).append(any());
        var response = json(mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id)
                .header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON).content(resultBody()))
                .andExpect(status().isInternalServerError()).andReturn());
        assertThat(response.path("error_code").textValue()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.toString()).doesNotContain(resultFeedback(), "字", "词", "Fixture payout", "Exception");
        assertThat(repository.find(id).orElseThrow()).isEqualTo(before);
        assertThat(payoutRows(id)).isZero(); assertThat(balance(teacher)).isZero();
        assertThat(used(learner)).isEqualTo(credit ? 10 : 1); assertThat(balance(learner)).isZero();
        doCallRealMethod().when(ledgerTarget).append(any());
        assertThat(completeStatus(id)).isEqualTo(200); assertThat(completeStatus(id)).isEqualTo(403);
        assertThat(payoutRows(id)).isEqualTo(1); assertThat(balance(teacher)).isEqualTo(2);
        verifyNoInteractions(entitlementTarget);
    }

    @Test void AT11_exactDeadlineIsAcceptedButLateSubmissionDoesNotPay() throws Exception {
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long exact = pending(learner); long late = pending(learner);
        claim(teacher, exact, 200); claim(teacher, late, 200);
        deadline(exact, NOW); deadline(late, NOW.minusMillis(1)); clearInvocations(entitlementTarget, ledgerTarget);
        var before = repository.find(late).orElseThrow();
        mvc.perform(post("/api/teacher/grading-requests/{id}/complete", late).header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(resultBody()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("GRADING_DEADLINE_MISSED"));
        assertThat(repository.find(late).orElseThrow()).isEqualTo(before); assertThat(payoutRows(late)).isZero();
        verifyNoInteractions(ledgerTarget, entitlementTarget);
        assertThat(completeStatus(exact)).isEqualTo(200); assertThat(payoutRows(exact)).isEqualTo(1);
    }

    @Test void AT11_invalidResultNeverChangesTheAssignedRequestOrBilling() throws Exception {
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long id = pending(learner); claim(teacher, id, 200);
        var before = repository.find(id).orElseThrow(); clearInvocations(repositoryTarget, entitlementTarget, ledgerTarget);
        var bodies = new ArrayList<>(List.of("", "null", "[]", "{}", "{", "{} {}", "42"));
        for (String field : List.of("grammar", "vocabulary", "coherence", "writing")) {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(resultBody());
            ((com.fasterxml.jackson.databind.node.ObjectNode) node.get("criteria")).put(field, 26); bodies.add(node.toString());
        }
        for (String feedback : List.of("", "\u00a0\u3000", "😀".repeat(5001))) {
            var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(resultBody()); node.put("feedback", feedback); bodies.add(node.toString());
        }
        var many = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(resultBody());
        var items = many.putArray("corrections"); for (int index = 0; index < 51; index++) items.addObject().put("original", "字").put("corrected", "词");
        bodies.add(many.toString());
        for (String body : bodies) mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("GRADING_INVALID_RESULT"));
        assertThat(repository.find(id).orElseThrow()).isEqualTo(before); assertThat(payoutRows(id)).isZero();
        verify(repositoryTarget, never()).complete(anyLong(), anyLong(), any(), anyInt(), anyString(), any(), anyInt());
        verifyNoInteractions(ledgerTarget, entitlementTarget);
    }

    @Test void AT09_completeCannotAccessMissingForeignUnassignedOrOtherRequestKinds() throws Exception {
        long id = pending(learner);
        completeForbidden(Long.toString(id), teacher);
        claim(teacher, id, 200); completeForbidden(Long.toString(id), other);
        long wrongKind = jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload,assignee_id,deadline_at) "
                + "VALUES (?,'IMPORT','ASSIGNED','{}',?,?) RETURNING id", Long.class, learner, teacher, Timestamp.from(NOW));
        for (String value : List.of("abc", "0", "-1", "9223372036854775808", Long.toString(Long.MAX_VALUE), Long.toString(wrongKind))) {
            completeForbidden(value, teacher);
        }
        assertThat(payoutRows(id)).isZero(); assertThat(repository.find(id).orElseThrow().status()).isEqualTo("ASSIGNED");
    }

    @Test void FR015_lostConditionalUpdateNeverCallsLedgerOrRefund() throws Exception {
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long id = pending(learner); claim(teacher, id, 200); var before = repository.find(id).orElseThrow();
        doReturn(false).when(repositoryTarget).complete(eq(id), eq(teacher), any(), anyInt(), anyString(), any(), anyInt());
        clearInvocations(entitlementTarget, ledgerTarget);
        assertThat(completeStatus(id)).isEqualTo(403); assertThat(repository.find(id).orElseThrow()).isEqualTo(before);
        verifyNoInteractions(entitlementTarget, ledgerTarget);
    }

    @ParameterizedTest
    @CsvSource({"QUOTA,true", "QUOTA,false", "CREDIT,true", "CREDIT,false"})
    void AT12_completionAndReleaseOverlapButOnlyTheWinnerCanPayOrRefund(String source, boolean completionWins) throws Exception {
        boolean credit = source.equals("CREDIT"); if (credit) { usage(learner, 10); topup(learner); }
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long id = pending(learner); assignedWithPastClaimDeadline(id, NOW.minusMillis(1));
        clearInvocations(repositoryTarget, entitlementTarget, ledgerTarget);
        overlappingCompletion(id, completionWins, false);
        assertMoneyOutcome(id, credit, completionWins);
        verify(repositoryTarget, never()).lockTeacherClaim(anyLong());
    }

    @ParameterizedTest
    @CsvSource({"QUOTA,true", "QUOTA,false", "CREDIT,true", "CREDIT,false"})
    void AT12_completionAndExpiryOverlapButNeverProduceBothPayoutAndRefund(String source, boolean completionWins) throws Exception {
        boolean credit = source.equals("CREDIT"); if (credit) { usage(learner, 10); topup(learner); }
        doReturn(2).when(settingsTarget).getInt(com.cnhsk.shared.settings.SettingKey.GRADING_TEACHER_PAYOUT_CREDITS);
        long id = pending(learner); claim(teacher, id, 200); deadline(id, NOW);
        clearInvocations(repositoryTarget, entitlementTarget, ledgerTarget);
        overlappingCompletion(id, completionWins, true);
        assertMoneyOutcome(id, credit, completionWins);
        verify(repositoryTarget, never()).lockTeacherClaim(anyLong());
    }

    @Test void AT17_completeHasExactTeacherRoleAndCookieCsrfProtection() throws Exception {
        long id = pending(learner); claim(teacher, id, 200);
        mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).contentType(MediaType.APPLICATION_JSON).content(resultBody()))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("LEARNER", "ADMIN", "CONTENT_EDITOR", "MODERATOR", "ACCOUNTANT")) {
            mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).header("Authorization", bearer(teacher, role))
                            .contentType(MediaType.APPLICATION_JSON).content(resultBody()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
        Cookie access = new Cookie("access_token", token(teacher, "TEACHER"));
        mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).cookie(access).contentType(MediaType.APPLICATION_JSON).content(resultBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).cookie(access, new Cookie("XSRF-TOKEN", "complete-csrf"))
                        .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content(resultBody())).andExpect(status().isForbidden());
        mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).cookie(access, new Cookie("XSRF-TOKEN", "complete-csrf"))
                        .header("X-XSRF-TOKEN", "complete-csrf").contentType(MediaType.APPLICATION_JSON).content(resultBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.score").value(78));
    }

    private void overlappingCompletion(long id, boolean completionWins, boolean expiry) throws Exception {
        var entered = new CountDownLatch(1); var proceed = new CountDownLatch(1);
        org.mockito.stubbing.Answer<Object> pause = call -> {
            entered.countDown(); if (!proceed.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture overlap timeout");
            return call.callRealMethod();
        };
        if (!completionWins) doAnswer(pause).when(repositoryTarget).complete(eq(id), eq(teacher), any(), anyInt(), anyString(), any(), anyInt());
        else if (expiry) doAnswer(pause).when(repositoryTarget).expire(eq(id), eq("ASSIGNED"), any());
        else doAnswer(pause).when(repositoryTarget).release(eq(id), eq(teacher), any());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var loser = executor.submit(() -> !completionWins ? completeStatus(id) : expiry ? expireAndRefund(id) : releaseStatus(id));
            assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
            int winner = completionWins ? completeStatus(id) : expiry ? expireAndRefund(id) : releaseStatus(id);
            assertThat(winner).isEqualTo(completionWins ? 200 : 204);
            proceed.countDown(); assertThat(loser.get(30, TimeUnit.SECONDS)).isEqualTo(403);
        } finally { proceed.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }

    private int expireAndRefund(long id) {
        return new TransactionTemplate(transactions).execute(status -> {
            var row = repository.find(id).orElseThrow();
            boolean won = repository.expire(id, "ASSIGNED", NOW.plusMillis(1));
            if (won) {
                try { entitlement.refund(row.userId(), mapper.treeToValue(row.payload().get("consumption"), Consumption.class)); }
                catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("Fixture receipt invalid"); }
            }
            return won ? 204 : 403;
        });
    }
    private void assertMoneyOutcome(long id, boolean credit, boolean completed) {
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo(completed ? "COMPLETED" : "EXPIRED");
        assertThat(payoutRows(id)).isEqualTo(completed ? 1 : 0); assertThat(balance(teacher)).isEqualTo(completed ? 2 : 0);
        assertThat(refunds(learner)).isEqualTo(!completed && credit ? 1 : 0);
        assertThat(used(learner)).isEqualTo(credit ? 10 : completed ? 1 : 0);
        assertThat(balance(learner)).isEqualTo(!completed && credit ? 1 : 0);
        verify(entitlementTarget, never()).consume(anyLong(), any(), anyString());
        verify(entitlementTarget, times(completed ? 0 : 1)).refund(eq(learner), any());
        verify(ledgerTarget, times(completed || credit ? 1 : 0)).append(any());
    }
    private void completeForbidden(String id, long actor) throws Exception {
        mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(resultBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("GRADING_NOT_ASSIGNEE"));
    }
    private String resultFeedback() { return " \nNhận xét tự soạn <script>mẫu</script> 😀\t "; }
    private String resultBody() {
        var root = mapper.createObjectNode(); root.putObject("criteria").put("grammar", 20).put("vocabulary", 18).put("coherence", 21).put("writing", 19);
        root.put("feedback", resultFeedback()); root.putArray("corrections").addObject().put("original", " 字 \n").put("corrected", " 词\t").put("note", "");
        root.put("score", 999).put("payout", 999); return root.toString();
    }
    private int completeStatus(long id) throws Exception {
        var result = mvc.perform(post("/api/teacher/grading-requests/{id}/complete", id).header("Authorization", bearer(teacher))
                .contentType(MediaType.APPLICATION_JSON).content(resultBody())).andReturn();
        if (result.getResponse().getStatus() == 403) assertThat(json(result).path("error_code").textValue()).isEqualTo("GRADING_NOT_ASSIGNEE");
        return result.getResponse().getStatus();
    }
    private int releaseStatus(long id) throws Exception {
        return mvc.perform(post("/api/teacher/grading-requests/{id}/release", id).header("Authorization", bearer(teacher))).andReturn().getResponse().getStatus();
    }
    private int payoutRows(long id) { return jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE request_id=? AND tx_type='TEACHER_PAYOUT'", Integer.class, id); }

    private long user() {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,'Teacher grading fixture') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", HASH);
        users.add(id); usage(id, 0); return id;
    }
    private long pending(long owner) { return requests.submit(owner, new GradingRequestWrite("字".repeat(50) + ++essayNumber, null, 3)).id(); }
    private void deadline(long id, Instant value) { jdbc.update("UPDATE learning.service_requests SET deadline_at=? WHERE id=?", Timestamp.from(value), id); }
    private void assignedWithPastClaimDeadline(long id, Instant original) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("UPDATE learning.service_requests SET created_at=?,deadline_at=? WHERE id=?",
                    Timestamp.from(original.minusSeconds(48 * 3600)), Timestamp.from(original), id);
            assertThat(repository.claim(id, teacher, NOW.minusSeconds(2 * 3600), NOW.plusSeconds(22 * 3600))).isTrue();
        });
    }
    private JsonNode claim(long actor, long id, int expected) throws Exception {
        var result = mvc.perform(post("/api/teacher/grading-requests/{id}/claim", id).header("Authorization", bearer(actor)))
                .andExpect(status().is(expected)).andReturn();
        var body = json(result);
        if (expected == 403) assertThat(body.path("error_code").textValue()).isEqualTo("GRADING_SELF_GRADING");
        if (expected == 422) assertThat(body.path("error_code").textValue()).isEqualTo("GRADING_CLAIM_LIMIT");
        if (expected == 409) assertThat(body.path("error_code").textValue()).isEqualTo("GRADING_ALREADY_CLAIMED");
        return body;
    }
    private int claimStatus(long actor, long id) throws Exception {
        return mvc.perform(post("/api/teacher/grading-requests/{id}/claim", id).header("Authorization", bearer(actor)))
                .andReturn().getResponse().getStatus();
    }
    private void usage(long id, int used) {
        Instant month = NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        jdbc.update("UPDATE auth.users SET free_usage=CAST(? AS jsonb),usage_reset_at=? WHERE id=?", "{\"GRADING\":" + used + "}", Timestamp.from(month), id);
    }
    private int used(long id) { return jdbc.queryForObject("SELECT COALESCE((free_usage->>'GRADING')::int,0) FROM auth.users WHERE id=?", Integer.class, id); }
    private int balance(long id) { return jdbc.queryForList("SELECT balance_after FROM learning.credit_transactions WHERE user_id=? ORDER BY seq DESC LIMIT 1", Integer.class, id).stream().findFirst().orElse(0); }
    private int refunds(long id) { return jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=? AND tx_type='REFUND'", Integer.class, id); }
    private int ledgerRows(long id) { return jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id=?", Integer.class, id); }
    private void topup(long id) { jdbc.update("INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after) VALUES (?,1,'TOPUP',1,0,1)", id); }
    private String token(long id, String... roles) { return jwt.issueAccessToken(id, List.of(roles), false, UUID.randomUUID()); }
    private String bearer(long id, String... roles) { return "Bearer " + token(id, roles.length == 0 ? new String[]{"TEACHER"} : roles); }
    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
    private List<Long> ids(JsonNode array) { var result = new ArrayList<Long>(); array.forEach(item -> result.add(item.path("id").longValue())); return result; }
    private void noPrivateFields(JsonNode json, boolean metadataOnly) {
        Set<String> forbidden = new HashSet<>(List.of("userId", "user_id", "assigneeId", "assignee_id", "email", "displayName", "consumption", "payload", "essayHash", "payout"));
        if (metadataOnly) forbidden.addAll(List.of("essay", "prompt", "feedback", "result"));
        for (String field : forbidden) assertThat(json.findValues(field)).as(field).isEmpty();
    }
    private List<Integer> race(List<Callable<Integer>> calls) throws Exception {
        var executor = Executors.newFixedThreadPool(calls.size());
        var ready = new CountDownLatch(calls.size()); var go = new CountDownLatch(1);
        try {
            var futures = calls.stream().map(call -> executor.submit(() -> { ready.countDown(); if (!go.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture timeout"); return call.call(); })).toList();
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue(); go.countDown();
            var results = new ArrayList<Integer>(); for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS)); return results;
        } finally { go.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration { @Bean @Primary Clock teacherGradingClock() { return Clock.fixed(NOW, ZoneOffset.UTC); } }
}
