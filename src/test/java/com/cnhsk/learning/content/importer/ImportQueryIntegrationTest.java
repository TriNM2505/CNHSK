package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.shared.audit.AuditLogRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ImportQueryIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String ROOT = "/api/admin/imports";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final List<String> GET_PATHS = List.of("", "/999999", "/999999/errors", "/999999/errors.csv",
            "/templates", "/templates/LEXEME.csv");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private TopicRepository topics;
    @Autowired private LexemeRepository lexemes;
    @Autowired private PronStageRepository stages;
    @Autowired private TopicItemRepository items;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private ImportMaintenance maintenance;
    @MockitoSpyBean private Clock clock;
    private final List<User> createdUsers = new ArrayList<>();
    private User user;
    private Set<Long> topicBaseline;
    private Set<Long> lexemeBaseline;
    private Set<Long> stageBaseline;
    private Set<Long> itemBaseline;

    @BeforeEach
    void setup() {
        doReturn(NOW).when(clock).instant();
        user = createUser("Người nhập T12");
        topicBaseline = topics.findAll().stream().map(Topic::getId).collect(Collectors.toSet());
        lexemeBaseline = lexemes.findAll().stream().map(value -> value.getId()).collect(Collectors.toSet());
        stageBaseline = stages.findAll().stream().map(value -> value.getId()).collect(Collectors.toSet());
        itemBaseline = items.findAll().stream().map(value -> value.getId()).collect(Collectors.toSet());
    }

    @AfterEach
    void cleanup() {
        reset(clock);
        Set<Long> userIds = createdUsers.stream().map(User::getId).collect(Collectors.toSet());
        auditLogs.deleteAllInBatch(auditLogs.findAll().stream().filter(value -> userIds.contains(value.getActorId())).toList());
        requests.deleteAllInBatch(requests.findAll().stream().filter(value -> userIds.contains(value.getUserId())).toList());
        items.deleteAllByIdInBatch(items.findAll().stream().map(value -> value.getId()).filter(id -> !itemBaseline.contains(id)).toList());
        lexemes.deleteAllByIdInBatch(lexemes.findAll().stream().map(value -> value.getId()).filter(id -> !lexemeBaseline.contains(id)).toList());
        topics.deleteAllByIdInBatch(topics.findAll().stream().map(Topic::getId).filter(id -> !topicBaseline.contains(id)).toList());
        stages.deleteAllByIdInBatch(stages.findAll().stream().map(value -> value.getId()).filter(id -> !stageBaseline.contains(id)).toList());
        users.deleteAllInBatch(createdUsers);
        createdUsers.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "ADMIN", "TEACHER", "MODERATOR", "ACCOUNTANT"})
    void FR001_allSixGetEndpointsRejectOtherRolesBeforeDataLookup(String role) throws Exception {
        for (String path : GET_PATHS) {
            var response = mvc.perform(get(ROOT + path).header("Authorization", "Bearer " + token(role)))
                    .andExpect(status().isForbidden()).andReturn();
            assertError(response, "FORBIDDEN");
        }
    }

    @Test
    void FR001_allSixGetEndpointsRequireAuthentication() throws Exception {
        for (String path : GET_PATHS) assertError(mvc.perform(get(ROOT + path))
                .andExpect(status().isUnauthorized()).andReturn(), "UNAUTHENTICATED");
    }

    @Test
    void FR001_getWithCookieAuthenticationDoesNotRequireACsrfHeader() throws Exception {
        mvc.perform(get(ROOT + "/templates").cookie(new Cookie("access_token", token("CONTENT_EDITOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void FR026_AT30_completedReportKeepsPreviewTotalsAndTheStoredResult() throws Exception {
        JsonNode preview = uploadTopic();
        long id = preview.path("runId").asLong();
        JsonNode completed = confirm(id);
        JsonNode report = json(read("/" + id, 200));
        assertThat(report.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(report.path("totals")).isEqualTo(preview.path("totals"));
        assertThat(completed.path("totals")).isEqualTo(preview.path("totals"));
        assertThat(report.path("result")).isEqualTo(completed.path("result"));
        assertThat(report.path("createdBy").path("displayName").asText()).isEqualTo("Người nhập T12");
        assertThat(report.path("createdBy").path("id").asLong()).isEqualTo(user.getId());
        assertThat(report.toString()).doesNotContain(user.getEmail(), "validRows", "fileHash", "runningSince", "pinyinNorm");
        ServiceRequest stored = requests.findById(id).orElseThrow();
        assertThat(stored.getPayload()).containsOnlyKeys("dataType", "fileName", "totalRows", "totals");
        assertThat(mapper.<JsonNode>valueToTree(stored.getPayload().get("totals"))).isEqualTo(preview.path("totals"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail", "errors", "csv", "history"})
    void FR024_FR026_everyReportAccessExpiresOverduePreviewsAndKeepsTotals(String operation) throws Exception {
        JsonNode preview = uploadTopic();
        long id = preview.path("runId").asLong();
        doReturn(NOW.plusSeconds(1801)).when(clock).instant();
        String path = switch (operation) {
            case "detail" -> "/" + id;
            case "errors" -> "/" + id + "/errors";
            case "csv" -> "/" + id + "/errors.csv";
            default -> "";
        };
        read(path, 200);
        ServiceRequest expired = requests.findById(id).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        assertThat(expired.getPayload()).doesNotContainKey("validRows");
        JsonNode report = json(read("/" + id, 200));
        assertThat(report.path("status").asText()).isEqualTo("EXPIRED");
        assertThat(report.path("totals")).isEqualTo(preview.path("totals"));
        assertThat(report.path("result").isNull()).isTrue();
        assertThat(topics.count()).isEqualTo(topicBaseline.size());
    }

    @Test
    void FR026_previewSnapshotSurvivesContentChangesAndDiffersFromTheFinalResult() throws Exception {
        JsonNode preview = uploadTopic();
        long id = preview.path("runId").asLong();
        String code = preview.path("preview").get(0).path("values").path("code").asText();
        JsonNode intervening = upload(ImportDataType.TOPIC,
                ("code,name,hsk_level\n" + code + ",Chủ đề tự soạn,1\n").getBytes(StandardCharsets.UTF_8));
        confirm(intervening.path("runId").asLong());
        assertThat(json(read("/" + id, 200)).path("totals")).isEqualTo(preview.path("totals"));
        JsonNode completed = confirm(id);
        assertThat(completed.path("result").path("unchanged").asInt()).isEqualTo(1);
        assertThat(completed.path("result").path("created").asInt()).isZero();
        JsonNode report = json(read("/" + id, 200));
        assertThat(report.path("totals").path("create").asInt()).isEqualTo(1);
        assertThat(report.path("totals")).isEqualTo(preview.path("totals"));
        assertThat(report.path("result")).isEqualTo(completed.path("result"));
    }

    @Test
    void FR024_FR026_confirmLazyExpiryAlsoKeepsThePreviewSnapshot() throws Exception {
        JsonNode preview = uploadTopic();
        long id = preview.path("runId").asLong();
        doReturn(NOW.plusSeconds(1801)).when(clock).instant();
        mvc.perform(post(ROOT + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Authorization", "Bearer " + token("CONTENT_EDITOR"))).andExpect(status().isGone());
        assertThat(json(read("/" + id, 200)).path("totals")).isEqualTo(preview.path("totals"));
    }

    @Test
    void FR024_FR026_scheduledExpiryKeepsThePreviewSnapshot() throws Exception {
        JsonNode preview = uploadTopic();
        doReturn(NOW.plusSeconds(1801)).when(clock).instant();
        maintenance.expirePreviews();
        JsonNode report = json(read("/" + preview.path("runId").asLong(), 200));
        assertThat(report.path("status").asText()).isEqualTo("EXPIRED");
        assertThat(report.path("totals")).isEqualTo(preview.path("totals"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FILE_TOO_LARGE", "ENCODING_ERROR"})
    void FR026_FR029_fileFailuresHaveNullTotalsAndExposeFailureReasonInHistory(String reason) throws Exception {
        byte[] bytes = reason.equals("FILE_TOO_LARGE") ? new byte[2 * 1024 * 1024 + 1]
                : new byte[] {(byte) 0xC3, 0x28};
        int httpStatus = reason.equals("FILE_TOO_LARGE") ? 413 : 422;
        var failed = mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "bad.csv", "text/csv", bytes))
                        .param("dataType", "TOPIC").header("Authorization", "Bearer " + token("CONTENT_EDITOR")))
                .andExpect(status().is(httpStatus)).andReturn();
        long id = json(failed).path("runId").asLong();
        JsonNode report = json(read("/" + id, 200));
        assertThat(report.path("totals").isNull()).isTrue();
        assertThat(report.path("failureReason").asText()).isEqualTo(reason);
        JsonNode historyItem = json(read("", 200)).path("items").get(0);
        assertThat(historyItem).isEqualTo(report);
        assertThat(historyItem.path("failureReason").asText()).isEqualTo(reason);
    }

    @Test
    void FR026_FR029_earlyMultipartFailureKeepsNullableMetadataValidInHistory() throws Exception {
        ServiceRequest run = record(ServiceRequestKind.IMPORT, ServiceRequestStatus.FAILED, user);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("dataType", null); metadata.put("fileName", null); metadata.put("totalRows", 0);
        run.setPayload(metadata);
        run.setErrorMessage("FILE_TOO_LARGE");
        requests.saveAndFlush(run);
        JsonNode detail = json(read("/" + run.getId(), 200));
        JsonNode item = json(read("", 200)).path("items").get(0);
        assertThat(item).isEqualTo(detail);
        assertThat(item.path("dataType").isNull()).isTrue();
        assertThat(item.path("fileName").isNull()).isTrue();
        assertThat(item.path("totals").isNull()).isTrue();
        assertThat(item.path("status").asText()).isEqualTo("FAILED");
        assertThat(item.path("failureReason").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(json(read("/" + run.getId() + "/errors", 200)).path("items")).isEmpty();
    }

    @Test
    void FR026_legacyCompletedRecordWithoutTotalsKeepsItsSavedResult() throws Exception {
        ServiceRequest run = record(ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, user);
        run.setResult(Map.of("total", 1, "valid", 1, "invalid", 0, "created", 1, "updated", 0, "unchanged", 0, "failed", 0));
        requests.saveAndFlush(run);
        JsonNode report = json(read("/" + run.getId(), 200));
        assertThat(report.path("totals").isNull()).isTrue();
        assertThat(report.path("result")).isEqualTo(mapper.valueToTree(run.getResult()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/errors", "/errors.csv"})
    void FR026_unknownIdsAndOtherRequestKindsReturn404(String suffix) throws Exception {
        ServiceRequest other = record(ServiceRequestKind.TRANSLATE, ServiceRequestStatus.PENDING, user);
        assertError(read("/999999999" + suffix, 404), "IMPORT_RUN_NOT_FOUND");
        assertError(read("/" + other.getId() + suffix, 404), "IMPORT_RUN_NOT_FOUND");
    }

    @Test
    void FR027_FR028_AT31_AT32_all45ErrorsAreOrderedPagedAndExportedWithBom() throws Exception {
        ServiceRequest run = record(ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, user);
        run.setErrorRows(errorRows(45));
        requests.saveAndFlush(run);
        JsonNode page = json(read("/" + run.getId() + "/errors?page=2&size=20", 200));
        assertThat(page.path("totalElements").asInt()).isEqualTo(45);
        assertThat(page.path("items")).hasSize(5);
        assertThat(page.path("items").get(0).path("line").asInt()).isEqualTo(42);
        assertThat(page.path("items").get(4).path("line").asInt()).isEqualTo(46);
        assertThat(page.path("items").get(0).path("value").asText()).hasSize(100);
        assertThat(json(read("/" + run.getId() + "/errors", 200)).path("items")).hasSize(20);
        var csv = read("/" + run.getId() + "/errors.csv", 200).getResponse();
        assertThat(csv.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(csv.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"import-" + run.getId() + "-errors.csv\"");
        assertThat(csv.getContentAsByteArray()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        try (var parser = CSVFormat.RFC4180.parse(new java.io.StringReader(csv.getContentAsString(StandardCharsets.UTF_8).substring(1)))) {
            var rows = parser.getRecords();
            assertThat(rows).hasSize(46);
            assertThat(rows.get(0).toList()).containsExactly("line", "column", "value", "code", "message");
            assertThat(rows.get(1).get(0)).isEqualTo("2");
            assertThat(rows.get(45).get(0)).isEqualTo("46");
            assertThat(rows.get(41).get(2)).isEqualTo(page.path("items").get(0).path("value").asText());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "size=2147483648"})
    void FR027_FR029_badPagingReturnsTheShared422ErrorShape(String query) throws Exception {
        ServiceRequest run = record(ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, user);
        assertError(read("?" + query, 422), "VALIDATION_FAILED");
        assertError(read("/" + run.getId() + "/errors?" + query, 422), "VALIDATION_FAILED");
    }

    @Test
    void FR029_AT33_historyIncludesAllEditorsAndOnlyImportsInNewestFirstOrder() throws Exception {
        User other = createUser("Biên tập khác");
        List<ServiceRequest> runs = new ArrayList<>();
        for (int index = 0; index < 12; index++) runs.add(record(ServiceRequestKind.IMPORT,
                ServiceRequestStatus.COMPLETED, index % 2 == 0 ? user : other));
        record(ServiceRequestKind.TRANSLATE, ServiceRequestStatus.COMPLETED, user);
        JsonNode page = json(read("?page=1&size=10", 200));
        assertThat(page.path("totalElements").asInt()).isEqualTo(12);
        assertThat(page.path("items")).hasSize(2);
        runs.sort(Comparator.comparing(ServiceRequest::getCreatedAt).reversed()
                .thenComparing(ServiceRequest::getId, Comparator.reverseOrder()));
        assertThat(page.path("items").get(0).path("runId").asLong()).isEqualTo(runs.get(10).getId());
        assertThat(page.path("items").get(1).path("runId").asLong()).isEqualTo(runs.get(11).getId());
        assertThat(page.path("items").get(0).path("createdBy").path("displayName").asText()).isNotBlank();
        assertThat(page.toString()).contains("Người nhập T12", "Biên tập khác").doesNotContain(user.getEmail(), other.getEmail());
        assertThat(json(read("", 200)).path("size").asInt()).isEqualTo(10);
    }

    @Test
    void FR034_FR035_AT41_sevenCsvTemplatesMatchMetadataAndTheirExamplesCanBeImported() throws Exception {
        JsonNode templates = json(read("/templates", 200));
        assertThat(templates).hasSize(7);
        for (JsonNode template : templates) {
            String type = template.path("dataType").asText();
            var csv = read("/templates/" + type + ".csv", 200).getResponse();
            assertThat(csv.getContentAsByteArray()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
            try (var parser = CSVFormat.RFC4180.parse(new java.io.StringReader(csv.getContentAsString(StandardCharsets.UTF_8).substring(1)))) {
                var rows = parser.getRecords();
                assertThat(rows).hasSize(2);
                for (int column = 0; column < template.path("columns").size(); column++) {
                    assertThat(rows.get(0).get(column)).isEqualTo(template.path("columns").get(column).path("name").asText());
                    assertThat(rows.get(1).get(column)).isEqualTo(template.path("columns").get(column).path("example").asText());
                }
            }
            JsonNode preview = upload(ImportDataType.valueOf(type), csv.getContentAsByteArray());
            assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(1);
            confirm(preview.path("runId").asLong());
        }
        assertError(read("/templates/UNKNOWN.csv", 422), "VALIDATION_FAILED");
    }

    @Test
    void FR027_errorPageMeetsTheLocal300MillisecondBudget() throws Exception {
        ServiceRequest run = record(ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, user);
        run.setErrorRows(errorRows(45));
        requests.saveAndFlush(run);
        String path = "/" + run.getId() + "/errors?size=20";
        read(path, 200);
        long[] timings = new long[5];
        for (int index = 0; index < timings.length; index++) {
            long start = System.nanoTime();
            read(path, 200);
            timings[index] = (System.nanoTime() - start) / 1_000_000;
        }
        long p95 = Arrays.stream(timings).max().orElseThrow();
        System.out.println("T12_ERRORS_BENCHMARK samples_ms=" + Arrays.toString(timings) + " local_p95_ms=" + p95);
        assertThat(p95).isLessThan(300);
    }

    private User createUser(String name) {
        User created = users.saveAndFlush(new User("query-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, name));
        createdUsers.add(created);
        return created;
    }

    private ServiceRequest record(ServiceRequestKind kind, ServiceRequestStatus status, User creator) {
        ServiceRequest run = new ServiceRequest(creator.getId(), kind);
        run.setStatus(status);
        run.setPayload(Map.of("dataType", "TOPIC", "fileName", "legacy.csv", "totalRows", 0,
                "expiresAt", NOW.plusSeconds(1800).toString(), "validRows", List.of()));
        run.setErrorRows(List.of());
        return requests.saveAndFlush(run);
    }

    private static List<Map<String, Object>> errorRows(int count) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int line = count + 1; line >= 2; line--) errors.add(Map.of("line", line, "column", "name",
                "value", "文".repeat(101), "code", "INVALID_VALUE", "message", "Giá trị sai"));
        return errors;
    }

    private JsonNode uploadTopic() throws Exception {
        String code = "T12_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        return upload(ImportDataType.TOPIC, ("code,name,hsk_level\n" + code + ",Chủ đề tự soạn,1\nBAD,,10\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode upload(ImportDataType type, byte[] csv) throws Exception {
        return json(mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "synthetic.csv", "text/csv", csv))
                        .param("dataType", type.name()).header("Authorization", "Bearer " + token("CONTENT_EDITOR")))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode confirm(long id) throws Exception {
        return json(mvc.perform(post(ROOT + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer " + token("CONTENT_EDITOR")))
                .andExpect(status().isOk()).andReturn());
    }

    private MvcResult read(String path, int expected) throws Exception {
        return mvc.perform(get(ROOT + path).header("Authorization", "Bearer " + token("CONTENT_EDITOR")))
                .andExpect(status().is(expected)).andReturn();
    }

    private String token(String role) {
        return jwt.issueAccessToken(user.getId(), List.of(role), true, UUID.randomUUID());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void assertError(MvcResult result, String code) throws Exception {
        JsonNode error = json(result);
        assertThat(error.path("error_code").asText()).isEqualTo(code);
        assertThat(error.path("request_id").asText()).isNotBlank();
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.toString()).doesNotContain("stackTrace", "org.hibernate", "org.postgresql");
    }
}
