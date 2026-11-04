package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.validate.RowValidator;
import com.cnhsk.learning.content.importer.write.KnowledgeNodeRowWriter;
import com.cnhsk.learning.content.importer.write.RowWriter;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.shared.audit.AuditLogRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class KnowledgeNodeImportIntegrationTest extends AbstractIntegrationTest {
    private static final String ROOT = "/api/admin/imports";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private KnowledgePointRepository points;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private EntityManager entityManager;
    @MockitoSpyBean private KnowledgeNodeRowWriter writer;
    private KnowledgeNodeRowWriter writerTarget;
    private User user;
    private Set<Long> pointBaseline;

    @BeforeEach
    void setup() {
        writerTarget = AopTestUtils.getUltimateTargetObject(writer);
        user = users.saveAndFlush(new User("node-import-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người nhập nút"));
        pointBaseline = points.findAll().stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
    }

    @AfterEach
    void cleanup() {
        reset(writerTarget);
        auditLogs.deleteAllInBatch(auditLogs.findAll().stream().filter(log -> user.getId().equals(log.getActorId())).toList());
        requests.deleteAllInBatch(requests.findAll().stream().filter(run -> user.getId().equals(run.getUserId())).toList());
        points.deleteAllByIdInBatch(points.findAll().stream().map(KnowledgePoint::getId)
                .filter(id -> !pointBaseline.contains(id)).toList());
        users.deleteById(user.getId());
    }

    @Test
    void FR002_FR004_AT02_AT03_previewDoesNotCreateNodesAndConfirmDerivesTheirDomainAndLevel() throws Exception {
        long before = points.count();
        byte[] csv = fixture("nodes-valid.csv");
        JsonNode preview = upload(csv);
        assertThat(preview.path("totals").path("create").asInt()).isEqualTo(2);
        assertThat(preview.path("preview").get(0).path("values").path("kid").asText()).isEqualTo("TV2-091");
        assertThat(points.count()).isEqualTo(before);
        assertThat(points.findByCode("TV2-091")).isEmpty();
        assertThat(points.findByCode("NP3-092")).isEmpty();

        JsonNode result = confirm(preview.path("runId").asLong(), false);
        assertThat(result.path("result").path("created").asInt()).isEqualTo(2);
        assertThat(result.path("result").path("failed").asInt()).isZero();
        assertThat(points.count()).isEqualTo(before + 2);
        KnowledgePoint word = points.findByCode("TV2-091").orElseThrow();
        assertThat(word.getDomain()).isEqualTo(KnowledgeDomain.TV);
        assertThat(word.getHskLevel()).isEqualTo((short) 2);
        assertThat(word.getSkillType()).isEqualTo(KnowledgeSkillType.VOCAB);
        assertThat(word.getLexemeId()).isNull();
        assertThat(word.getPrereqIds()).isEmpty();
        assertThat(word.getSupportIds()).isEmpty();
        assertThat(word.getCreatedAt()).isNotNull();
        assertThat(word.getUpdatedAt()).isNotNull();
        KnowledgePoint grammar = points.findByCode("NP3-092").orElseThrow();
        assertThat(grammar.getDomain()).isEqualTo(KnowledgeDomain.NP);
        assertThat(grammar.getHskLevel()).isEqualTo((short) 3);

        JsonNode again = upload(csv);
        assertThat(again.path("totals").path("unchanged").asInt()).isEqualTo(2);
        assertThat(confirm(again.path("runId").asLong(), true).path("result").path("unchanged").asInt()).isEqualTo(2);
        assertThat(points.findByCode("TV2-091").orElseThrow().getId()).isEqualTo(word.getId());
        assertThat(points.findByCode("TV2-091").orElseThrow().getUpdatedAt()).isEqualTo(word.getUpdatedAt());
        assertThat(ownAuditCount()).isEqualTo(2);
    }

    @Test
    void FR004_AT04_previewDoesNotUpdateAndConfirmPreservesTheLockedNodesEdgesAndBlankOptionalValues() throws Exception {
        KnowledgePoint first = point("NP1-081", "Tiên quyết một");
        KnowledgePoint second = point("NP1-082", "Tiên quyết hai");
        KnowledgePoint support = point("NP1-083", "Hỗ trợ");
        KnowledgePoint target = point("NP3-084", "Tên cũ");
        target.setPrereqIds(new Long[]{first.getId(), second.getId()});
        target.setSupportIds(new Long[]{support.getId()});
        target.setCanDo("Năng lực đã có");
        target.setSkillType(KnowledgeSkillType.GRAMMAR);
        target = points.saveAndFlush(target);
        Instant updatedAt = target.getUpdatedAt();
        long before = points.count();
        JsonNode preview = upload("kid,name_vi,name_zh,can_do,skill_type\nnp3-084,Tên mới,新名称,,\n".getBytes(StandardCharsets.UTF_8));
        assertThat(preview.path("totals").path("update").asInt()).isEqualTo(1);
        KnowledgePoint unchanged = points.findByCode("NP3-084").orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Tên cũ");
        assertThat(unchanged.getNameZh()).isEqualTo("测试节点");
        assertThat(unchanged.getCanDo()).isEqualTo("Năng lực đã có");
        assertThat(unchanged.getSkillType()).isEqualTo(KnowledgeSkillType.GRAMMAR);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(unchanged.getPrereqIds()).containsExactly(first.getId(), second.getId());
        assertThat(unchanged.getSupportIds()).containsExactly(support.getId());
        assertThat(points.count()).isEqualTo(before);

        assertThat(confirm(preview.path("runId").asLong(), false).path("result").path("updated").asInt()).isEqualTo(1);
        KnowledgePoint saved = points.findByCode("NP3-084").orElseThrow();
        assertThat(saved.getId()).isEqualTo(target.getId());
        assertThat(saved.getTitle()).isEqualTo("Tên mới");
        assertThat(saved.getNameZh()).isEqualTo("新名称");
        assertThat(saved.getCanDo()).isEqualTo("Năng lực đã có");
        assertThat(saved.getSkillType()).isEqualTo(KnowledgeSkillType.GRAMMAR);
        assertThat(saved.getPrereqIds()).containsExactly(first.getId(), second.getId());
        assertThat(saved.getSupportIds()).containsExactly(support.getId());
        assertThat(points.count()).isEqualTo(before);
    }

    @Test
    void FR003_AT03_invalidFixtureKeepsPhysicalErrorsAndOnlyConfirmsItsValidRow() throws Exception {
        JsonNode preview = upload(fixture("nodes-invalid.csv"));
        assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(1);
        assertThat(preview.path("totals").path("invalid").asInt()).isEqualTo(5);
        List<String> codes = new ArrayList<>();
        List<Integer> lines = new ArrayList<>();
        preview.path("errors").forEach(error -> { codes.add(error.path("code").asText()); lines.add(error.path("line").asInt()); });
        assertThat(codes).containsExactly("INVALID_KID", "INVALID_KID", "INVALID_KID", "NOT_CJK", "REQUIRED_MISSING");
        assertThat(lines).containsExactly(2, 3, 4, 5, 6);
        assertThat(points.findByCode("CH1-096")).isEmpty();
        JsonNode result = confirm(preview.path("runId").asLong(), false);
        assertThat(result.path("result").path("created").asInt()).isEqualTo(1);
        assertThat(points.findByCode("CH1-096")).isPresent();
        assertThat(points.findByCode("NP3-094")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUESTION", "NODE_LINK"})
    void FR002_unimplementedTypesRejectUploadWithoutReadingFilesOrRecordingRunsOrAudit(String type) throws Exception {
        long runCount = requests.count();
        long auditCount = auditLogs.count();
        mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "not-csv.txt", "text/plain", new byte[]{(byte) 0xff}))
                        .param("dataType", type).header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Loại dữ liệu này chưa hỗ trợ nhập"))
                .andExpect(jsonPath("$.details[0].field").value("dataType"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        assertThat(requests.count()).isEqualTo(runCount);
        assertThat(auditLogs.count()).isEqualTo(auditCount);
        mvc.perform(get(ROOT + "/templates/" + type + ".csv").header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isOk());
        assertThat(requests.count()).isEqualTo(runCount);
        assertThat(auditLogs.count()).isEqualTo(auditCount);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true"})
    void FR002_missingEitherProcessorStopsBeforeAnyFileAccess(boolean validator, boolean writer) {
        Availability fixture = availability(validator, writer);
        MultipartFile unopened = mock(MultipartFile.class);
        assertThatThrownBy(() -> fixture.service().upload("QUESTION", unopened))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(error.getStatus().value()).isEqualTo(422);
                    assertThat(error.getMessage()).isEqualTo("Loại dữ liệu này chưa hỗ trợ nhập");
                    assertThat(error.getExtra()).containsEntry("details", List.of(Map.of("field", "dataType", "message", "Loại dữ liệu này chưa hỗ trợ nhập")));
                });
        verifyNoInteractions(unopened, fixture.reader(), fixture.requests(), fixture.audit(), fixture.manager());
    }

    @Test
    void FR002_registeringBothProcessorsAutomaticallyOpensAFutureType() throws Exception {
        Availability fixture = availability(true, true);
        MockMultipartFile file = new MockMultipartFile("file", "future.csv", "text/csv", "synthetic".getBytes(StandardCharsets.UTF_8));
        assertThat(fixture.service().upload("QUESTION", file).totals().valid()).isEqualTo(1);
        verify(fixture.reader()).read(ImportDataType.QUESTION, "future.csv", file.getBytes());
        verify(fixture.requests()).saveAndFlush(any());
        verifyNoInteractions(fixture.audit());
    }

    @Test
    void FR004_eachNodeCommitsSeparatelyAndAFailingSavedRowIsRolledBackWithoutLeakingItsException() throws Exception {
        doAnswer(invocation -> {
            ValidatedRow row = invocation.getArgument(0);
            Object result = invocation.callRealMethod();
            if (row.values().get("kid").equals("NP3-092")) throw new IllegalStateException("synthetic private database detail");
            return result;
        }).when(writerTarget).write(any());
        JsonNode preview = upload(fixture("nodes-valid.csv"));
        JsonNode result = confirm(preview.path("runId").asLong(), false);
        assertThat(result.path("result").path("created").asInt()).isEqualTo(1);
        assertThat(result.path("result").path("failed").asInt()).isEqualTo(1);
        assertThat(points.findByCode("TV2-091")).isPresent();
        assertThat(points.findByCode("NP3-092")).isEmpty();
        var run = requests.findById(preview.path("runId").asLong()).orElseThrow();
        assertThat(run.getErrorRows()).singleElement().satisfies(error -> {
            assertThat(error).containsEntry("line", 3).containsEntry("code", "ROW_WRITE_FAILED");
            assertThat(error.toString()).doesNotContain("synthetic private", "hibernate", "postgresql");
        });
        assertThat(run.getPayload()).doesNotContainKey("validRows");
        assertThat(ownAuditCount()).isEqualTo(1);
    }

    @Test
    void FR004_writerHoldsTheExistingNodesRowLockUntilItsCallerCommits() {
        KnowledgePoint point = point("NP3-085", "Tên trước khi khoá");
        var executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                writer.write(new ValidatedRow(2, Map.of("kid", point.getCode(), "name_vi", "Tên cập nhật", "name_zh", "新名称"), null, null));
                var competing = executor.submit(() -> new TransactionTemplate(manager).execute(transaction ->
                        entityManager.createNativeQuery("SELECT id FROM learning.knowledge_points WHERE code = :kid FOR UPDATE NOWAIT")
                                .setParameter("kid", point.getCode()).getSingleResult()));
                assertThatThrownBy(() -> competing.get(5, TimeUnit.SECONDS))
                        .hasRootCauseMessage("ERROR: could not obtain lock on row in relation \"knowledge_points\"");
            });
            assertThat(points.findByCode(point.getCode()).orElseThrow().getTitle()).isEqualTo("Tên cập nhật");
        } finally {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "TEACHER", "MODERATOR", "ACCOUNTANT", "ADMIN"})
    void FR001_nodeUploadsStillRejectEveryOtherRole(String role) throws Exception {
        mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "nodes.csv", "text/csv", fixture("nodes-valid.csv")))
                        .param("dataType", "KNOWLEDGE_NODE").header("Authorization", bearer(role)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void FR001_nodeUploadsRequireAuthenticationAndCookieWritesRequireCsrf() throws Exception {
        mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "nodes.csv", "text/csv", fixture("nodes-valid.csv")))
                        .param("dataType", "KNOWLEDGE_NODE"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "nodes.csv", "text/csv", fixture("nodes-valid.csv")))
                        .param("dataType", "KNOWLEDGE_NODE").cookie(new Cookie("access_token", bearer("CONTENT_EDITOR").substring(7))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
    }

    private KnowledgePoint point(String kid, String name) {
        return points.saveAndFlush(new KnowledgePoint(kid, name, KnowledgeDomain.valueOf(kid.substring(0, 2)),
                "测试节点", Short.parseShort(kid.substring(2, 3))));
    }

    private JsonNode upload(byte[] csv) throws Exception {
        var response = mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "nodes.csv", "text/csv", csv))
                        .param("dataType", "KNOWLEDGE_NODE").header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray());
    }

    private JsonNode confirm(long id, boolean acknowledge) throws Exception {
        var response = mvc.perform(post(ROOT + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgeRerun\":" + acknowledge + "}").header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray());
    }

    private String bearer(String role) {
        return "Bearer " + jwt.issueAccessToken(user.getId(), List.of(role), true, UUID.randomUUID());
    }

    private long ownAuditCount() {
        return auditLogs.findAll().stream().filter(log -> user.getId().equals(log.getActorId())).count();
    }

    private static byte[] fixture(String name) throws Exception {
        try (var stream = new ClassPathResource("import/knowledge/" + name).getInputStream()) {
            return stream.readAllBytes();
        }
    }

    private static Availability availability(boolean withValidator, boolean withWriter) {
        AuthApi auth = mock(AuthApi.class);
        when(auth.currentUser()).thenReturn(new CurrentUser(99L, Set.of(RoleCode.CONTENT_EDITOR), true));
        CsvReader reader = mock(CsvReader.class);
        RowValidator validator = mock(RowValidator.class);
        when(validator.dataType()).thenReturn(ImportDataType.QUESTION);
        RowWriter writer = mock(RowWriter.class);
        when(writer.dataType()).thenReturn(ImportDataType.QUESTION);
        Map<String, String> values = Map.of("code", "FUTURE_QUESTION");
        when(validator.validate(any())).thenReturn(new RowValidationResult(1, List.of(new ValidatedRow(2, values, null, null)), List.of()));
        ImportChangePlanner planner = mock(ImportChangePlanner.class);
        when(planner.plan(any(), any())).thenReturn(List.of(new ImportPreview.PreviewRow(2, ImportPreview.RowAction.CREATE, values)));
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> {
            var run = invocation.getArgument(0);
            ReflectionTestUtils.setField(run, "id", 777L);
            return run;
        });
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AuditService audit = mock(AuditService.class);
        ImportService service = new ImportService(auth, reader, withValidator ? List.of(validator) : List.of(), planner,
                requests, new ImportProperties(), Clock.fixed(Instant.parse("2026-11-04T00:00:00Z"), ZoneOffset.UTC),
                manager, mock(EntityManager.class), audit, withWriter ? List.of(writer) : List.of());
        return new Availability(service, reader, requests, audit, manager);
    }

    private record Availability(ImportService service, CsvReader reader, ServiceRequestRepository requests,
            AuditService audit, PlatformTransactionManager manager) { }
}
