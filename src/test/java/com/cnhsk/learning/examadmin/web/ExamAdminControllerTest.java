package com.cnhsk.learning.examadmin.web;

import static com.cnhsk.learning.examadmin.ExamAdminModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.cnhsk.learning.examadmin.ExamAdminService;
import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class ExamAdminControllerTest {
    private static final String BODY = """
            {"code":"EXAM","title":"Đề tự soạn","hskLevel":3,"totalScore":1,
             "sections":[{"name":"Đọc","type":"READING","durationMin":0}],
             "questions":[{"questionId":11,"sectionIndex":0,"points":1}]}
            """;
    private final ExamAdminService service = mock(ExamAdminService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;
    private EtagBody publishedBody;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ExamAdminController(service, mapper))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        var principal = new AuthenticatedUser(42, Set.of("CONTENT_EDITOR"), true, "fixture", null, Instant.EPOCH);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(service.create(anyLong(), any())).thenReturn(detail());
        when(service.detail(anyLong())).thenReturn(detail());
        when(service.clone(anyLong(), anyLong(), any())).thenReturn(detail());
        when(service.archive(anyLong(), anyLong())).thenReturn(detail());
        when(service.publish(anyLong(), anyLong(), any())).thenAnswer(call -> {
            Supplier<EtagBody> body = call.getArgument(2);
            publishedBody = body.get();
            return detail();
        });
        when(service.update(anyLong(), any())).thenAnswer(call -> {
            Supplier<ExamUpdate> body = call.getArgument(1);
            body.get();
            return detail();
        });
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void createUsesThePrincipalRatherThanClientSuppliedIdentity() throws Exception {
        var body = mapper.readTree(BODY).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("created_by", 999).put("createdBy", 998);
        mvc.perform(post("/api/admin/exams").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.questions[0].kid").value("NP3-901"))
                .andExpect(jsonPath("$.publishedAt").isEmpty()).andExpect(jsonPath("$.etag").value("a".repeat(64)));
        var input = ArgumentCaptor.forClass(ExamWrite.class);
        verify(service).create(eq(42L), input.capture());
        assertThat(input.getValue().sections()).containsExactly(new Section("Đọc", SectionType.READING, 0));
        assertThat(input.getValue().questions()).containsExactly(new QuestionWrite(11, 0, 1));
    }

    @Test
    void listParsesFiltersAndUsesContractPaginationDefaults() throws Exception {
        when(service.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(new ExamPage(List.of(), 0, 20, 0));
        mvc.perform(get("/api/admin/exams")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20));
        verify(service).list(null, null, null, 0, 20);
        mvc.perform(get("/api/admin/exams").param("status", "ARCHIVED").param("hsk", "6").param("q", "%_")
                .param("page", "2").param("size", "50")).andExpect(status().isOk());
        verify(service).list(ExamStatus.ARCHIVED, 6, "%_", 2, 50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "hsk", "page", "size"})
    void malformedQueryValuesReturn422(String field) throws Exception {
        mvc.perform(get("/api/admin/exams").param(field, "invalid")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        verify(service, never()).list(any(), any(), any(), anyInt(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{", "{} {}", "{\"sections\":null,\"questions\":[]}",
            "{\"sections\":[],\"questions\":null}"})
    void malformedJsonAndWrongRootShapesReturn422(String body) throws Exception {
        mvc.perform(post("/api/admin/exams").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
        verify(service, never()).create(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"hskLevel", "totalScore", "sections.0.durationMin", "questions.0.questionId",
            "questions.0.sectionIndex", "questions.0.points"})
    void numericStringsFractionsNullAndOverflowAreNotCoerced(String path) throws Exception {
        for (String raw : List.of("\"1\"", "1.5", "null", "99999999999999999999999999999")) {
            var body = mapper.readTree(BODY);
            String[] segments = path.split("\\.");
            var parent = segments.length == 1 ? body : body.path(segments[0]).get(0);
            ((com.fasterxml.jackson.databind.node.ObjectNode) parent).set(segments[segments.length - 1], mapper.readTree(raw));
            mvc.perform(post("/api/admin/exams").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
        verify(service, never()).create(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "title", "hskLevel", "totalScore", "sections", "questions"})
    void everyRequiredWriteFieldIsRequired(String field) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
        body.remove(field);
        mvc.perform(post("/api/admin/exams").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateReadsItsBodyInsideTheLockedServiceOperation() throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
        body.put("etag", "expected");
        mvc.perform(put("/api/admin/exams/7").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk());
        verify(service).update(eq(7L), any());
        doThrow(new ApiException(ErrorCode.EXAM_NOT_EDITABLE, org.springframework.http.HttpStatus.CONFLICT,
                "Chỉ được sửa đề nháp.", Map.of())).when(service).update(eq(7L), any());
        mvc.perform(put("/api/admin/exams/7").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("EXAM_NOT_EDITABLE"));
    }

    @Test
    void missingEtagAndUnknownSectionTypeReturn422() throws Exception {
        mvc.perform(put("/api/admin/exams/7").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/admin/exams").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("READING", "UNKNOWN")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cloneUsesThePrincipalAndSupportsMissingNullOrExplicitTitle() throws Exception {
        for (String body : List.of("{\"code\":\"COPY\"}", "{\"code\":\"COPY\",\"title\":null}")) {
            mvc.perform(post("/api/admin/exams/7/clone").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        verify(service, times(2)).clone(7, 42, new CloneWrite("COPY", null));
        mvc.perform(post("/api/admin/exams/7/clone").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"COPY\",\"title\":\"Bản sao\",\"createdBy\":999}"))
                .andExpect(status().isCreated());
        verify(service).clone(7, 42, new CloneWrite("COPY", "Bản sao"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "999999999999999999999"})
    void invalidResourceIdsReturnTheExamNotFoundResponse(String id) throws Exception {
        mvc.perform(get("/api/admin/exams/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("EXAM_NOT_FOUND"));
    }

    @Test
    void readSectionSchemaAllowsUnknownFieldsWithoutRelaxingExamWrite() {
        var contract = new OpenAPIV3Parser().readLocation(Path.of("specs/023-content-admin-settings/contracts/openapi-content-admin.yaml")
                .toAbsolutePath().toUri().toString(), null, null).getOpenAPI();
        var schemas = contract.getComponents().getSchemas();
        assertThat(property(schemas.get("ExamWrite"), "sections").getItems().get$ref()).endsWith("/ExamSection");
        assertThat(property(schemas.get("ExamDetail"), "sections").getItems().get$ref()).endsWith("/ExamSectionView");
        assertThat(schemas.get("ExamSection").getRequired()).contains("type", "durationMin");
        assertThat(property(schemas.get("ExamSection"), "type").getEnum()).doesNotContainNull();
        assertThat(property(schemas.get("ExamSectionView"), "type").getTypes()).contains("string", "null");
        assertThat(property(schemas.get("ExamSectionView"), "durationMin").getTypes()).contains("integer", "null");
        assertThat(property(schemas.get("PublicationError"), "code").getEnum()).extracting(Object::toString).contains("SECTION_TYPE_MISSING");
    }

    private io.swagger.v3.oas.models.media.Schema<?> property(io.swagger.v3.oas.models.media.Schema<?> schema, String field) {
        return schema.getProperties().get(field);
    }

    @Test
    void exactlyTheSevenImplementedOperationsMatchContractMethodsIdsAndStatuses() {
        var contract = new OpenAPIV3Parser().readLocation(Path.of("specs/023-content-admin-settings/contracts/openapi-content-admin.yaml")
                .toAbsolutePath().toUri().toString(), null, null).getOpenAPI();
        Set<String> actual = new TreeSet<>();
        for (var method : ExamAdminController.class.getDeclaredMethods()) {
            String suffix;
            String verb;
            if (method.isAnnotationPresent(GetMapping.class)) {
                var values = method.getAnnotation(GetMapping.class).value(); suffix = values.length == 0 ? "" : values[0]; verb = "GET";
            } else if (method.isAnnotationPresent(PostMapping.class)) {
                var values = method.getAnnotation(PostMapping.class).value(); suffix = values.length == 0 ? "" : values[0]; verb = "POST";
            } else if (method.isAnnotationPresent(PutMapping.class)) {
                suffix = method.getAnnotation(PutMapping.class).value()[0]; verb = "PUT";
            } else continue;
            String path = "/api/admin/exams" + suffix;
            actual.add(verb + " " + path);
            var expected = contract.getPaths().get(path).readOperationsMap().get(io.swagger.v3.oas.models.PathItem.HttpMethod.valueOf(verb));
            assertThat(method.getAnnotation(Operation.class).operationId()).isEqualTo(expected.getOperationId());
            assertThat(java.util.Arrays.stream(method.getAnnotation(ApiResponses.class).value()).map(response -> response.responseCode()).toList())
                    .containsExactlyInAnyOrderElementsOf(expected.getResponses().keySet());
        }
        assertThat(actual).containsExactlyInAnyOrder("GET /api/admin/exams", "POST /api/admin/exams", "GET /api/admin/exams/{id}",
                "PUT /api/admin/exams/{id}", "POST /api/admin/exams/{id}/clone",
                "POST /api/admin/exams/{id}/publish", "POST /api/admin/exams/{id}/archive");
    }

    @Test
    void lifecycleEndpointsTakeTheActorFromThePrincipal() throws Exception {
        mvc.perform(post("/api/admin/exams/7/publish").contentType(MediaType.APPLICATION_JSON)
                .content("{\"etag\":\"expected\",\"actorId\":999}"))
                .andExpect(status().isOk());
        verify(service).publish(eq(7L), eq(42L), any());
        assertThat(publishedBody).isEqualTo(new EtagBody("expected"));
        mvc.perform(post("/api/admin/exams/7/archive")).andExpect(status().isOk());
        verify(service).archive(7, 42);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{", "{} {}", "{}", "{\"etag\":null}", "{\"etag\":1}"})
    void publicationRequiresAJsonObjectWithAStringEtag(String body) throws Exception {
        mvc.perform(post("/api/admin/exams/7/publish").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void publicationStateConflictTakesPrecedenceOverMalformedBody() throws Exception {
        doThrow(new ApiException(ErrorCode.EXAM_NOT_EDITABLE, org.springframework.http.HttpStatus.CONFLICT,
                "Chỉ được công bố đề nháp.", Map.of())).when(service).publish(eq(7L), anyLong(), any());
        mvc.perform(post("/api/admin/exams/7/publish").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code").value("EXAM_NOT_EDITABLE"));
    }

    private ExamDetail detail() {
        return new ExamDetail(7, "EXAM", "Đề tự soạn", 3, 1, List.of(new SectionView("Đọc", SectionType.READING, 0)),
                ExamStatus.DRAFT, null, "a".repeat(64),
                List.of(new QuestionView(11, 0, 0, 1, "Q", "MULTIPLE_CHOICE", "APPROVED", "NP3-901", false)), List.of());
    }
}
