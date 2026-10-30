package com.cnhsk.contract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import com.cnhsk.learning.content.importer.ImportMaintenance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
// Scope spec 001's runtime contract comparison to auth controllers as other modules add endpoints.
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.auth"
})
@WithMockUser(username = "contract-test", roles = "ADMIN")
class AuthContractTest {

    private static final Path CONTRACT_FILE = Path.of(
            "specs", "001-auth-rbac", "contracts", "openapi-auth.yaml");
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");

    // Context H2 không có bảng learning.service_requests; runner khôi phục nhập dữ liệu kiểm ở integration test Testcontainers.
    @MockitoBean
    private ImportMaintenance importMaintenance;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void AC01_sourceContractParsesAndDeclaresResponsesForEveryOperation() throws IOException {
        OpenAPI contract = readContract();

        assertThat(contract.getOpenapi()).startsWith("3.1.");
        assertThat(contract.getPaths()).isNotEmpty();
        contract.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
            assertThat(operation.getResponses())
                    .as("Responses for %s %s", method, path)
                    .isNotNull()
                    .isNotEmpty();
        }));
    }

    @Test
    void T21_forgotPasswordHasGenericMessageBodyAndResetDeclaresInternalError() throws IOException {
        OpenAPI contract = readContract();
        Operation forgot = contract.getPaths().get("/api/auth/forgot-password")
                .readOperationsMap().get(PathItem.HttpMethod.POST);
        Operation reset = contract.getPaths().get("/api/auth/reset-password")
                .readOperationsMap().get(PathItem.HttpMethod.POST);

        assertThat(forgot.getResponses().get("202").getContent())
                .containsKey(MediaType.APPLICATION_JSON_VALUE);
        assertThat(forgot.getResponses().get("202").getContent().get(MediaType.APPLICATION_JSON_VALUE)
                .getSchema().get$ref()).isEqualTo("#/components/schemas/ForgotPasswordResponse");
        assertThat(reset.getResponses()).containsKey("500");
        assertThat(reset.getResponses().get("500").get$ref()).isEqualTo("#/components/responses/Error");
    }

    @Test
    void T23_adminUserDetailDeclaresStandardInternalErrorResponse() throws IOException {
        OpenAPI contract = readContract();
        Operation detail = contract.getPaths().get("/api/admin/users/{id}")
                .readOperationsMap().get(PathItem.HttpMethod.GET);

        assertThat(detail.getResponses()).containsKey("500");
        assertThat(detail.getResponses().get("500").get$ref()).isEqualTo("#/components/responses/Error");
    }

    @Test
    void T24_roleMutationOperationsDeclareApprovedValidationAndRedisFailureResponses() throws IOException {
        OpenAPI contract = readContract();
        Operation grant = contract.getPaths().get("/api/admin/users/{id}/roles")
                .readOperationsMap().get(PathItem.HttpMethod.POST);
        Operation revoke = contract.getPaths().get("/api/admin/users/{id}/roles/{roleCode}")
                .readOperationsMap().get(PathItem.HttpMethod.DELETE);

        assertThat(grant.getResponses()).containsKeys("204", "401", "403", "404", "409", "422");
        assertThat(grant.getResponses()).doesNotContainKey("500");
        assertThat(revoke.getResponses()).containsKeys("204", "401", "403", "404", "409", "422", "500");
        assertThat(revoke.getResponses().get("422").get$ref()).isEqualTo("#/components/responses/Error");
        assertThat(revoke.getResponses().get("500").get$ref()).isEqualTo("#/components/responses/Error");
    }

    @Test
    void T25_banDeclaresPostInternalErrorButNotDeleteInternalError() throws IOException {
        OpenAPI contract = readContract();
        PathItem banPath = contract.getPaths().get("/api/admin/users/{id}/ban");
        Operation ban = banPath.readOperationsMap().get(PathItem.HttpMethod.POST);
        Operation unban = banPath.readOperationsMap().get(PathItem.HttpMethod.DELETE);

        assertThat(ban.getResponses()).containsKeys("204", "401", "403", "404", "409", "422", "500");
        assertThat(ban.getResponses().get("500").get$ref()).isEqualTo("#/components/responses/Error");
        assertThat(unban.getResponses()).containsKeys("204", "401", "403", "404");
        assertThat(unban.getResponses()).doesNotContainKey("500");
        io.swagger.v3.oas.models.media.Schema<?> reasonSchema = (io.swagger.v3.oas.models.media.Schema<?>)
                contract.getComponents().getSchemas().get("BanRequest").getProperties().get("reason");
        assertThat(reasonSchema.getDescription())
                .contains("trims leading and trailing whitespace")
                .contains("5–500 characters");
    }

    @Test
    void AC01_runtimeDocumentedPathsAndStatusesAreDeclaredBySourceContract() throws Exception {
        OpenAPI contract = readContract();
        SwaggerParseResult imports = new OpenAPIV3Parser().readContents(Files.readString(Path.of(
                "specs", "002-learning-data-import", "contracts", "openapi-import.yaml")));
        assertThat(imports.getMessages()).isEmpty();
        assertThat(imports.getOpenAPI()).isNotNull();
        imports.getOpenAPI().getPaths().forEach((path, item) -> {
            assertThat(path).startsWith("/api/admin/imports");
            assertThat(contract.getPaths()).doesNotContainKey(path);
            contract.getPaths().addPathItem(path, item);
        });
        MvcResult result = mockMvc.perform(get("/v3/api-docs").accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode runtimeDocument = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode runtimePaths = runtimeDocument.path("paths");
        assertThat(runtimePaths.isObject()).isTrue();

        runtimePaths.properties().forEach(pathEntry -> {
            String path = pathEntry.getKey();
            PathItem sourcePath = contract.getPaths().get(path);
            assertThat(sourcePath)
                    .as("Runtime path %s must be present in openapi-auth.yaml", path)
                    .isNotNull();

            pathEntry.getValue().properties().forEach(operationEntry -> {
                String methodName = operationEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(methodName)) {
                    return;
                }

                PathItem.HttpMethod method = PathItem.HttpMethod.valueOf(methodName.toUpperCase(Locale.ROOT));
                Operation sourceOperation = sourcePath.readOperationsMap().get(method);
                assertThat(sourceOperation)
                        .as("Runtime operation %s %s must be present in openapi-auth.yaml", methodName, path)
                        .isNotNull();

                JsonNode runtimeResponses = operationEntry.getValue().path("responses");
                assertThat(runtimeResponses.isObject()).isTrue();
                runtimeResponses.fieldNames().forEachRemaining(status -> assertThat(sourceOperation.getResponses())
                        .as("Status %s for %s %s must be declared in openapi-auth.yaml", status, methodName, path)
                        .containsKey(status));
            });
        });
    }

    private OpenAPI readContract() throws IOException {
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(Files.readString(CONTRACT_FILE));

        assertThat(result.getOpenAPI())
                .as("OpenAPI parser messages: %s", result.getMessages())
                .isNotNull();
        assertThat(result.getMessages())
                .as("OpenAPI parser messages")
                .isEmpty();
        return result.getOpenAPI();
    }

    @Test
    void FR008_FR026_importRunMetadataCanBeNullOnlyForEarlyOversizeFailure() throws IOException {
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(Files.readString(Path.of(
                "specs", "002-learning-data-import", "contracts", "openapi-import.yaml")));
        assertThat(parsed.getMessages()).isEmpty();
        var run = parsed.getOpenAPI().getComponents().getSchemas().get("ImportRun");
        for (String field : Set.of("dataType", "fileName")) {
            io.swagger.v3.oas.models.media.Schema<?> schema =
                    (io.swagger.v3.oas.models.media.Schema<?>) run.getProperties().get(field);
            assertThat(schema.getTypes()).containsExactlyInAnyOrder("string", "null");
            assertThat(schema.getDescription()).contains("null chỉ khi FAILED do FILE_TOO_LARGE");
        }
        var alternatives = (io.swagger.v3.oas.models.media.Schema<?>) run.getAllOf().get(0);
        var earlyFailure = (io.swagger.v3.oas.models.media.Schema<?>) alternatives.getAnyOf().get(0);
        assertThat(earlyFailure.getRequired()).contains("failureReason");
        assertThat(((io.swagger.v3.oas.models.media.Schema<?>) earlyFailure.getProperties().get("status")).getConst()).isEqualTo("FAILED");
        assertThat(((io.swagger.v3.oas.models.media.Schema<?>) earlyFailure.getProperties().get("failureReason")).getConst()).isEqualTo("FILE_TOO_LARGE");
        var knownMetadata = (io.swagger.v3.oas.models.media.Schema<?>) alternatives.getAnyOf().get(1);
        assertThat(((io.swagger.v3.oas.models.media.Schema<?>) knownMetadata.getProperties().get("fileName")).getTypes()).containsExactly("string");
        assertThat(((io.swagger.v3.oas.models.media.Schema<?>) knownMetadata.getProperties().get("dataType")).get$ref())
                .isEqualTo("#/components/schemas/DataType");
    }
}
