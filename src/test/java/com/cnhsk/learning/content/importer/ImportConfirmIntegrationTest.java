package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.write.TopicRowWriter;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.shared.audit.AuditLog;
import com.cnhsk.shared.audit.AuditLogRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ImportConfirmIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String ROOT = "/api/admin/imports";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private TopicRepository topics;
    @Autowired private LexemeRepository lexemes;
    @Autowired private TopicItemRepository items;
    @Autowired private PronStageRepository stages;
    @Autowired private AuditLogRepository auditLogs;
    @MockitoSpyBean private TopicRowWriter topicWriter;
    @MockitoSpyBean private AuditService audit;
    @MockitoSpyBean private Clock clock;
    private TopicRowWriter writerTarget;
    private AuditService auditTarget;
    private User user;
    private final List<User> createdUsers = new ArrayList<>();
    private Set<Long> topicBaseline;
    private Set<Long> lexemeBaseline;
    private Set<Long> itemBaseline;
    private Set<Long> stageBaseline;

    @BeforeEach
    void setup() {
        writerTarget = AopTestUtils.getUltimateTargetObject(topicWriter);
        auditTarget = AopTestUtils.getUltimateTargetObject(audit);
        doReturn(NOW).when(clock).instant();
        user = createUser("Người tạo lần nhập");
        topicBaseline = topics.findAll().stream().map(Topic::getId).collect(Collectors.toSet());
        lexemeBaseline = lexemes.findAll().stream().map(Lexeme::getId).collect(Collectors.toSet());
        itemBaseline = items.findAll().stream().map(TopicItem::getId).collect(Collectors.toSet());
        stageBaseline = stages.findAll().stream().map(PronStage::getId).collect(Collectors.toSet());
    }

    @AfterEach
    void cleanup() {
        reset(writerTarget, auditTarget, clock);
        Set<Long> ownedUsers = createdUsers.stream().map(User::getId).collect(Collectors.toSet());
        auditLogs.deleteAllInBatch(auditLogs.findAll().stream().filter(a -> ownedUsers.contains(a.getActorId())).toList());
        requests.deleteAllInBatch(requests.findAll().stream().filter(r -> ownedUsers.contains(r.getUserId())).toList());
        items.deleteAllByIdInBatch(items.findAll().stream().map(TopicItem::getId).filter(id -> !itemBaseline.contains(id)).toList());
        lexemes.deleteAllByIdInBatch(lexemes.findAll().stream().map(Lexeme::getId).filter(id -> !lexemeBaseline.contains(id)).toList());
        topics.deleteAllByIdInBatch(topics.findAll().stream().map(Topic::getId).filter(id -> !topicBaseline.contains(id)).toList());
        stages.deleteAllByIdInBatch(stages.findAll().stream().map(PronStage::getId).filter(id -> !stageBaseline.contains(id)).toList());
        users.deleteAllInBatch(createdUsers);
        createdUsers.clear();
    }

    @ParameterizedTest
    @EnumSource(ImportDataType.class)
    void FR018_FR023_allSevenTemplatesCanBeUploadedAndConfirmed(ImportDataType type) throws Exception {
        switch (type) {
            case TOPIC_ITEM -> { importExample(ImportDataType.TOPIC); importExample(ImportDataType.LEXEME); }
            case GRAMMAR_EXERCISE -> importExample(ImportDataType.GRAMMAR);
            case PRON_UNIT -> importExample(ImportDataType.PRON_STAGE);
            default -> { }
        }
        JsonNode preview = upload(type, exampleCsv(type));
        JsonNode result = json(confirm(preview.path("runId").asLong(), "{}", user, 200));
        assertThat(result.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("dataType").asText()).isEqualTo(type.name());
        assertThat(result.path("fileName").asText()).isEqualTo("synthetic.csv");
        assertThat(result.path("result").path("created").asInt()).isEqualTo(1);
        assertThat(result.path("result").path("failed").asInt()).isZero();
        assertThat(result.path("createdBy").path("displayName").asText()).isEqualTo("Người tạo lần nhập");
        assertThat(result.toString()).doesNotContain("@example.test", "validRows", "pinyinNorm");
    }

    @Test
    void FR015_AT19_expiredPreviewPersistsExpiredAndClearsRowsEvenThoughHttpReturns410() throws Exception {
        long id = upload(ImportDataType.TOPIC, topicCsv(prefix(), 1)).path("runId").asLong();
        doReturn(NOW.plusSeconds(1801)).when(clock).instant();
        assertError(confirm(id, "{}", user, 410), "IMPORT_PREVIEW_EXPIRED");
        ServiceRequest stored = requests.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        assertThat(stored.getPayload()).doesNotContainKey("validRows");
        assertThat(topics.count()).isEqualTo(topicBaseline.size());
        assertThat(audits(id)).isEmpty();
    }

    @Test
    void FR015_AT20_repeatConfirmIsConflictAndDoesNotWriteOrAuditTwice() throws Exception {
        long id = upload(ImportDataType.TOPIC, topicCsv(prefix(), 2)).path("runId").asLong();
        confirm(id, "{}", user, 200);
        assertError(confirm(id, "{}", user, 409), "IMPORT_NOT_PENDING");
        assertThat(topics.count()).isEqualTo(topicBaseline.size() + 2);
        assertThat(audits(id)).hasSize(1);
    }

    @Test
    void FR015_unknownRunAndNonImportServiceRequestReturn404() throws Exception {
        assertError(confirm(Long.MAX_VALUE, "{}", user, 404), "IMPORT_RUN_NOT_FOUND");
        ServiceRequest other = requests.saveAndFlush(new ServiceRequest(user.getId(), ServiceRequestKind.TRANSLATE));
        assertError(confirm(other.getId(), "{}", user, 404), "IMPORT_RUN_NOT_FOUND");
        assertThat(requests.findById(other.getId()).orElseThrow().getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
    }

    @Test
    void FR015_previewWithNoValidRowsStaysPending() throws Exception {
        long id = upload(ImportDataType.TOPIC, "code,name,hsk_level\nAA,Tên,0\n").path("runId").asLong();
        assertError(confirm(id, "{}", user, 422), "NO_VALID_ROWS");
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        assertThat(topics.count()).isEqualTo(topicBaseline.size());
    }

    @Test
    void FR016_FR021_AT21_AT25_rerunRequiresAcknowledgementAndProducesOnlyUnchangedRows() throws Exception {
        String csv = topicCsv(prefix(), 3);
        long first = upload(ImportDataType.TOPIC, csv).path("runId").asLong();
        JsonNode secondPreview = upload(ImportDataType.TOPIC, csv);
        assertThat(secondPreview.path("alreadyImported").asBoolean()).isFalse();
        confirm(first, "{}", user, 200);
        long second = secondPreview.path("runId").asLong();
        assertError(confirm(second, "{}", user, 409), "RERUN_CONFIRMATION_REQUIRED");
        assertThat(requests.findById(second).orElseThrow().getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        JsonNode rerun = json(confirm(second, "{\"acknowledgeRerun\":true}", user, 200));
        assertThat(rerun.path("result").path("unchanged").asInt()).isEqualTo(3);
        assertThat(rerun.path("result").path("created").asInt()).isZero();
        assertThat(topics.count()).isEqualTo(topicBaseline.size() + 3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void FR017_AT22_concurrentConfirmationIsSerializedWithoutLosingPendingRuns(boolean sameRun) throws Exception {
        long first = upload(ImportDataType.TOPIC, topicCsv(prefix(), 1)).path("runId").asLong();
        long second = sameRun ? first : upload(ImportDataType.TOPIC, topicCsv(prefix(), 1)).path("runId").asLong();
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            writing.countDown();
            if (!release.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Test writer was not released");
            return invocation.callRealMethod();
        }).when(writerTarget).write(any());
        var executor = Executors.newSingleThreadExecutor();
        var running = executor.submit(() -> confirm(first, "{}", user, 200));
        try {
            assertThat(writing.await(20, TimeUnit.SECONDS)).isTrue();
            assertError(confirm(second, "{}", user, 409), sameRun ? "IMPORT_NOT_PENDING" : "CONCURRENT_IMPORT");
            assertThat(requests.findById(second).orElseThrow().getStatus())
                    .isEqualTo(sameRun ? ServiceRequestStatus.RUNNING : ServiceRequestStatus.PENDING);
        } finally {
            release.countDown();
            try { running.get(30, TimeUnit.SECONDS); }
            finally { executor.shutdownNow(); }
        }
        if (!sameRun) {
            reset(writerTarget);
            confirm(second, "{}", user, 200);
        }
    }

    @Test
    void FR018_FR019_AT23_failedRowReallyRollsBackItsInsertAndOtherNineRowsCommit() throws Exception {
        String prefix = prefix();
        long id = upload(ImportDataType.TOPIC, topicCsv(prefix, 10)).path("runId").asLong();
        doAnswer(invocation -> {
            Object action = invocation.callRealMethod();
            if (((ValidatedRow) invocation.getArgument(0)).line() == 5) {
                throw new IllegalStateException("raw-cell-marker must never escape");
            }
            return action;
        }).when(writerTarget).write(any());
        JsonNode response = json(confirm(id, "{}", user, 200));
        assertThat(response.path("result").path("created").asInt()).isEqualTo(9);
        assertThat(response.path("result").path("failed").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("valid").asInt()).isEqualTo(10);
        assertThat(topics.findByCode(prefix + "_3")).isEmpty();
        assertThat(topics.count()).isEqualTo(topicBaseline.size() + 9);
        ServiceRequest stored = requests.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ServiceRequestStatus.COMPLETED);
        assertThat(stored.getErrorRows()).singleElement().satisfies(error -> {
            assertThat(error).containsEntry("line", 5).containsEntry("code", "ROW_WRITE_FAILED")
                    .containsEntry("column", null).containsEntry("value", null);
            assertThat(error.toString()).doesNotContain("raw-cell-marker");
        });
        assertThat(audits(id)).singleElement().satisfies(audit ->
                assertThat(mapper.<JsonNode>valueToTree(audit.getDetail().get("after"))).isEqualTo(response.path("result")));
    }

    @Test
    void FR019_allWriteFailuresStillCompleteWithFailureCountsAndNoContent() throws Exception {
        long id = upload(ImportDataType.TOPIC, topicCsv(prefix(), 2)).path("runId").asLong();
        doThrow(new IllegalStateException("synthetic storage failure")).when(writerTarget).write(any());
        JsonNode response = json(confirm(id, "{}", user, 200));
        assertThat(response.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(response.path("result").path("failed").asInt()).isEqualTo(2);
        assertThat(response.path("errorCount").asInt()).isEqualTo(2);
        assertThat(topics.count()).isEqualTo(topicBaseline.size());
    }

    @Test
    void FR019_originalValidationErrorsArePreservedAlongsideWriteFailures() throws Exception {
        String prefix = prefix();
        String csv = topicCsv(prefix, 2) + "INVALID_NAME,,0\n";
        long id = upload(ImportDataType.TOPIC, csv).path("runId").asLong();
        doThrow(new IllegalStateException("synthetic storage failure")).when(writerTarget).write(any());
        JsonNode response = json(confirm(id, "{}", user, 200));
        assertThat(response.path("result").path("invalid").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("failed").asInt()).isEqualTo(2);
        assertThat(response.path("errorCount").asInt()).isEqualTo(4);
        assertThat(requests.findById(id).orElseThrow().getErrorRows()).extracting(e -> e.get("line")).containsExactly(2, 3, 4, 4);
    }

    @Test
    void FR021_AT26_completedPayloadAuditAndCreatorMetadataMatchContract() throws Exception {
        long id = upload(ImportDataType.TOPIC, topicCsv(prefix(), 1)).path("runId").asLong();
        User confirmingEditor = createUser("Người xác nhận");
        JsonNode response = json(confirm(id, "{}", confirmingEditor, 200));
        assertThat(response.path("createdBy").path("id").asLong()).isEqualTo(user.getId());
        assertThat(response.path("createdBy").path("displayName").asText()).isEqualTo("Người tạo lần nhập");
        ServiceRequest stored = requests.findById(id).orElseThrow();
        assertThat(stored.getPayload()).containsOnlyKeys("dataType", "fileName", "totalRows", "totals");
        assertThat(stored.getCompletedAt()).isEqualTo(NOW);
        assertThat(mapper.<JsonNode>valueToTree(stored.getResult())).isEqualTo(response.path("result"));
        assertThat(audits(id)).singleElement().satisfies(audit -> {
            assertThat(audit.getActorId()).isEqualTo(confirmingEditor.getId());
            assertThat(audit.getTargetType()).isEqualTo("import");
            assertThat(audit.getDetail()).containsEntry("before", null);
            assertThat(mapper.<JsonNode>valueToTree(audit.getDetail().get("after"))).isEqualTo(response.path("result"));
        });
    }

    @Test
    void FR021_auditFailureRollsBackCompletionButKeepsRowsThatAlreadyCommitted() throws Exception {
        long id = upload(ImportDataType.TOPIC, topicCsv(prefix(), 1)).path("runId").asLong();
        doThrow(new IllegalStateException("synthetic audit failure")).when(auditTarget).record(anyLong(), any(), any(), any(), any());
        assertError(confirm(id, "{}", user, 500), "INTERNAL_ERROR");
        ServiceRequest stored = requests.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);
        assertThat(stored.getPayload()).containsKey("validRows");
        assertThat(stored.getResult()).isNull();
        assertThat(stored.getCompletedAt()).isNull();
        assertThat(topics.count()).isEqualTo(topicBaseline.size() + 1);
        assertThat(audits(id)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "ADMIN", "TEACHER", "MODERATOR", "ACCOUNTANT"})
    void FR001_confirmRejectsOtherRolesBeforeCsrfOrRunLookup(String role) throws Exception {
        mvc.perform(post(ROOT + "/999999/confirm").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .cookie(new Cookie("access_token", token(user, role))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void FR001_confirmRequiresAuthenticationAndCookieCsrfButAcceptsMatchingHeader() throws Exception {
        mvc.perform(post(ROOT + "/999999/confirm").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        long id = upload(ImportDataType.TOPIC, topicCsv(prefix(), 1)).path("runId").asLong();
        String access = token(user, "CONTENT_EDITOR");
        mvc.perform(post(ROOT + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .cookie(new Cookie("access_token", access)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        String csrf = UUID.randomUUID().toString();
        mvc.perform(post(ROOT + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .cookie(new Cookie("access_token", access), new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "[]", "{\"acknowledgeRerun\":null}", "{\"acknowledgeRerun\":\"true\"}", "{\"acknowledgeRerun\":1}"})
    void FR015_invalidConfirmBodyIs422AndLeavesRunPending(String body) throws Exception {
        long id = upload(ImportDataType.TOPIC, topicCsv(prefix(), 1)).path("runId").asLong();
        assertError(confirm(id, body, user, 422), "VALIDATION_FAILED");
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        assertThat(topics.count()).isEqualTo(topicBaseline.size());
    }

    @Test
    void FR015_malformedRunIdIs422WithoutLeakingConverterDetails() throws Exception {
        MvcResult response = mvc.perform(post(ROOT + "/not-an-id/confirm").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer " + token(user, "CONTENT_EDITOR")))
                .andExpect(status().isUnprocessableEntity()).andReturn();
        assertError(response, "VALIDATION_FAILED");
        assertThat(json(response).toString()).doesNotContain("NumberFormatException", "not-an-id");
    }

    @Test
    void FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget() throws Exception {
        long[] samples = new long[5];
        for (int trial = 0; trial < samples.length; trial++) {
            long id = upload(ImportDataType.TOPIC, topicCsv(prefix(), 5000)).path("runId").asLong();
            long start = System.nanoTime();
            JsonNode response = json(confirm(id, "{}", user, 200));
            samples[trial] = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertThat(response.path("result").path("created").asInt()).isEqualTo(5000);
            assertThat(response.path("result").path("failed").asInt()).isZero();
        }
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        long p95 = sorted[(int) Math.ceil(sorted.length * 0.95) - 1];
        System.out.println("T10_BENCHMARK rows=5000 samples_ms=" + Arrays.toString(samples) + " local_p95_ms=" + p95);
        assertThat(p95).isLessThan(30_000L);
    }

    private void importExample(ImportDataType type) throws Exception {
        confirm(upload(type, exampleCsv(type)).path("runId").asLong(), "{}", user, 200);
    }

    private JsonNode upload(ImportDataType type, String csv) throws Exception {
        return json(mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "synthetic.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("dataType", type.name()).header("Authorization", "Bearer " + token(user, "CONTENT_EDITOR")))
                .andExpect(status().isCreated()).andReturn());
    }

    private MvcResult confirm(long id, String body, User actor, int expected) throws Exception {
        return mvc.perform(post(ROOT + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("Authorization", "Bearer " + token(actor, "CONTENT_EDITOR")))
                .andExpect(status().is(expected)).andReturn();
    }

    private void assertError(MvcResult response, String code) throws Exception {
        JsonNode body = json(response);
        assertThat(body.path("error_code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("stackTrace", "org.hibernate", "org.postgresql");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<AuditLog> audits(long id) {
        return auditLogs.findAll().stream().filter(a -> "IMPORT_CONFIRM".equals(a.getAction()) && Long.valueOf(id).equals(a.getTargetId())).toList();
    }

    private User createUser(String name) {
        User created = users.saveAndFlush(new User("confirm-" + UUID.randomUUID() + "@example.test", "not-used", name));
        createdUsers.add(created);
        return created;
    }

    private String token(User actor, String role) {
        return jwt.issueAccessToken(actor.getId(), List.of(role), true, UUID.randomUUID());
    }

    private static String prefix() {
        return "T10_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
    }

    private static String topicCsv(String prefix, int count) {
        return "code,name,hsk_level\n" + IntStream.range(0, count).mapToObj(i -> prefix + "_" + i + ",Chủ đề tự soạn " + i + ",1\n")
                .collect(Collectors.joining());
    }

    private static String exampleCsv(ImportDataType type) {
        ImportTemplate template = ImportTemplate.forType(type);
        return template.columns().stream().map(c -> c.name()).collect(Collectors.joining(",")) + "\n"
                + template.columns().stream().map(c -> "\"" + c.example().replace("\"", "\"\"") + "\"").collect(Collectors.joining(",")) + "\n";
    }
}
