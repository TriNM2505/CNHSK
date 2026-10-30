package com.cnhsk.learning.content.importer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImportUploadIntegrationTest extends AbstractIntegrationTest {
    private static final String PATH = "/api/admin/imports";
    private static final String VALID = "code,name,hsk_level\nT08_UPLOAD,Chủ đề tự soạn,1\n";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private UserRepository users;
    @Autowired private LexemeRepository lexemes;
    @LocalServerPort private int port;
    private User user;

    @BeforeEach
    void user() {
        user = users.saveAndFlush(new User("upload-" + UUID.randomUUID() + "@example.test", "not-used", "Người nhập thử"));
    }

    @AfterEach
    void cleanup() {
        requests.deleteAll(requests.findAll().stream().filter(r -> r.getUserId().equals(user.getId())).toList());
        users.delete(user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "ADMIN", "TEACHER", "MODERATOR", "ACCOUNTANT"})
    void FR001_AT01_otherRolesFailBeforeCsrfAndFileValidation(String role) throws Exception {
        mvc.perform(multipart(PATH).file(file("bad.txt", new byte[CsvReader.MAX_FILE_BYTES + 1]))
                        .cookie(new Cookie("access_token", token(role))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        assertThat(ownRuns()).isEmpty();
    }

    @Test
    void FR001_guestCannotUpload() throws Exception {
        mvc.perform(multipart(PATH).file(file("ok.csv", VALID.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        assertThat(ownRuns()).isEmpty();
    }

    @Test
    void FR002_cookieRequiresMatchingCsrfHeaderBeforeDatatypeAndFile() throws Exception {
        mvc.perform(multipart(PATH).file(file("bad.txt", new byte[0]))
                        .cookie(new Cookie("access_token", token("CONTENT_EDITOR"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        String csrf = UUID.randomUUID().toString();
        mvc.perform(multipart(PATH).file(file("ok.csv", VALID.getBytes(StandardCharsets.UTF_8))).param("dataType", "TOPIC")
                        .cookie(new Cookie("access_token", token("CONTENT_EDITOR")), new Cookie("XSRF-TOKEN", csrf))
                        .header("X-XSRF-TOKEN", csrf))
                .andExpect(status().isCreated());
    }

    @Test
    void FR002_invalidDatatypeAndMissingPartsAreRequestErrorsWithoutRuns() throws Exception {
        mvc.perform(multipart(PATH).file(file("bad.txt", new byte[CsvReader.MAX_FILE_BYTES + 1]))
                        .header("Authorization", bearer()).param("dataType", "OTHER"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(multipart(PATH).header("Authorization", bearer()).param("dataType", "TOPIC"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(multipart(PATH).file(file("ok.csv", new byte[0])).header("Authorization", bearer()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(ownRuns()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"bad.txt,UNSUPPORTED_FILE_FORMAT", "wrong.csv,TEMPLATE_MISMATCH", "empty.csv,EMPTY_FILE",
            "encoding.csv,ENCODING_ERROR", "many.csv,TOO_MANY_ROWS", "large.csv,FILE_TOO_LARGE"})
    void FR008_fileFailuresPersistWithMetadataAndRunId(String name, String code) throws Exception {
        byte[] bytes = switch (name) {
            case "encoding.csv" -> new byte[]{(byte) 0xff};
            case "empty.csv" -> "code,name,hsk_level\n".getBytes(StandardCharsets.UTF_8);
            case "wrong.csv" -> "code,name,extra\na,b,c\n".getBytes(StandardCharsets.UTF_8);
            case "many.csv" -> ("code,name,hsk_level\n" + "T08,Chủ đề,1\n".repeat(5001)).getBytes(StandardCharsets.UTF_8);
            case "large.csv" -> new byte[CsvReader.MAX_FILE_BYTES + 1];
            default -> new byte[0];
        };
        MvcResult response = mvc.perform(multipart(PATH).file(file(name, bytes))
                        .header("Authorization", bearer()).param("dataType", "TOPIC"))
                .andExpect(status().is(code.equals("FILE_TOO_LARGE") ? 413 : 422))
                .andExpect(jsonPath("$.error_code").value(code)).andExpect(jsonPath("$.runId").isNumber())
                .andExpect(jsonPath("$.request_id").isNotEmpty()).andReturn();
        JsonNode body = json(response);
        ServiceRequest run = requests.findById(body.path("runId").asLong()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo(code);
        assertThat(run.getPayload()).containsEntry("dataType", "TOPIC").containsEntry("fileName", name);
        assertThat(run.getPayload()).doesNotContainKey("validRows");
        if (code.equals("TEMPLATE_MISMATCH")) {
            assertThat(body.path("details").findValuesAsText("field")).containsExactly("hsk_level", "extra");
        }
    }

    @Test
    void FR006_duplicateHeadersKeepEachDetailInHttpResponse() throws Exception {
        mvc.perform(multipart(PATH).file(file("dup.csv", "code,name,hsk_level, name ,code\na,b,1,c,d\n".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer()).param("dataType", "TOPIC"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details.length()").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"data.csv", "data.CSV", "data.Csv"})
    void FR002_uploadAcceptsEveryCsvExtensionCase(String name) throws Exception {
        mvc.perform(multipart(PATH).file(file(name, VALID.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer()).param("dataType", "TOPIC"))
                .andExpect(status().isCreated());
    }

    @Test
    void FR006_controllerPreservesMissingUnknownAndDuplicateColumnsInTheContractDetails() throws Exception {
        var response = mvc.perform(multipart(PATH)
                        .file(file("headers.csv", "code,name,unexpected, code \nAA,Tên,x,AA\n".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer()).param("dataType", "TOPIC"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("TEMPLATE_MISMATCH"))
                .andExpect(jsonPath("$.details.length()").value(3)).andReturn();
        JsonNode details = json(response).path("details");
        assertThat(details.findValuesAsText("field")).containsExactlyInAnyOrder("hsk_level", "unexpected", "code");
        assertThat(details.findValuesAsText("message")).containsExactlyInAnyOrder(
                "Thiếu cột bắt buộc: hsk_level", "Cột không có trong mẫu: unexpected",
                "Tên cột bị trùng sau khi bỏ khoảng trắng: code");
        details.forEach(detail -> assertThat(detail.size()).isEqualTo(2));
    }

    @Test
    void FR008_AT02_nativeMultipartLimitCreatesFailedRunWithNullMetadata() throws Exception {
        HttpResponse<String> response = nativeUpload(new byte[CsvReader.MAX_FILE_BYTES + 1], "CONTENT_EDITOR", false);
        assertThat(response.statusCode()).isEqualTo(413);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("error_code").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(body.path("request_id").asText()).isNotBlank();
        ServiceRequest run = requests.findById(body.path("runId").asLong()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("FILE_TOO_LARGE");
        assertThat(run.getPayload()).containsEntry("dataType", null).containsEntry("fileName", null);
        assertThat(run.getFileHash()).isNull();
    }

    @Test
    void FR002_nativeMultipartStillChecksRoleAndCsrfBeforeReadingOversizeBody() throws Exception {
        byte[] large = new byte[CsvReader.MAX_FILE_BYTES + 1];
        HttpResponse<String> forbidden = nativeUpload(large, "ADMIN", true);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(forbidden.body()).path("error_code").asText()).isEqualTo("FORBIDDEN");
        HttpResponse<String> csrf = nativeUpload(large, "CONTENT_EDITOR", true);
        assertThat(csrf.statusCode()).isEqualTo(403);
        assertThat(mapper.readTree(csrf.body()).path("error_code").asText()).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(ownRuns()).isEmpty();
    }

    @Test
    void FR012_AT15_nativeValidFileCreatesPendingWithFullMetadataAndNoContentWrites() throws Exception {
        long before = lexemes.count();
        HttpResponse<String> response = nativeUpload(VALID.getBytes(StandardCharsets.UTF_8), "CONTENT_EDITOR", false);
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.path("dataType").asText()).isEqualTo("TOPIC");
        assertThat(body.path("fileName").asText()).isEqualTo("upload.csv");
        ServiceRequest run = requests.findById(body.path("runId").asLong()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        assertThat(run.getKind()).isEqualTo(ServiceRequestKind.IMPORT);
        assertThat(run.getFileHash()).matches("[a-f0-9]{64}");
        assertThat(run.getPayload()).containsEntry("dataType", "TOPIC").containsEntry("fileName", "upload.csv")
                .containsEntry("totalRows", 1).containsKeys("validRows", "expiresAt");
        assertThat(Duration.between(Instant.now(), Instant.parse(body.path("expiresAt").asText())).toSeconds()).isBetween(1790L, 1800L);
        assertThat(lexemes.count()).isEqualTo(before);
        // A preview alone must not make the duplicate-file warning true.
        assertThat(json(upload("TOPIC", VALID)).path("alreadyImported").asBoolean()).isFalse();
        run.setStatus(ServiceRequestStatus.COMPLETED);
        requests.saveAndFlush(run);
        assertThat(json(upload("TOPIC", VALID)).path("alreadyImported").asBoolean()).isTrue();
    }

    @Test
    void FR014_AT17_previewLimitsDoNotTruncateStoredRowsOrTotalErrors() throws Exception {
        String csv = "code,name,hsk_level\n" + IntStream.range(0, 30).mapToObj(i -> "T08_" + i + ",Tên,1\n")
                .collect(java.util.stream.Collectors.joining())
                + IntStream.range(0, 60).mapToObj(i -> "BAD_" + i + ",Tên,0\n").collect(java.util.stream.Collectors.joining());
        JsonNode body = json(upload("TOPIC", csv));
        assertThat(body.path("preview")).hasSize(20);
        assertThat(body.path("errors")).hasSize(50);
        assertThat(body.path("errorCount").asInt()).isEqualTo(60);
        assertThat(body.path("totals").path("total").asInt()).isEqualTo(90);
        assertThat(body.path("totals").path("invalid").asInt()).isEqualTo(60);
        ServiceRequest run = requests.findById(body.path("runId").asLong()).orElseThrow();
        assertThat((List<?>) run.getPayload().get("validRows")).hasSize(30);
        assertThat(run.getErrorRows()).hasSize(60);
    }

    @Test
    void FR013_AT16_oneFileClassifiesUnchangedUpdateAndCreateWithoutWritingLexemes() throws Exception {
        Lexeme first = new Lexeme(LexemeKind.CHAR, "我", "tôi");
        first.setPinyin("wǒ"); first.setHskLevel((short) 1);
        Lexeme second = new Lexeme(LexemeKind.CHAR, "你", "nghĩa cũ");
        second.setPinyin("nǐ"); second.setHskLevel((short) 1);
        lexemes.saveAllAndFlush(List.of(first, second));
        try {
            long before = lexemes.count();
            JsonNode body = json(upload("LEXEME", "kind,text,pinyin,meaning_vi,hsk_level\n"
                    + "CHAR,我,wǒ,tôi,1\nCHAR,你,nǐ,bạn,1\nCHAR,他,tā,anh ấy,1\n"));
            assertThat(body.path("preview").findValuesAsText("action")).containsExactly("UNCHANGED", "UPDATE", "CREATE");
            assertThat(body.path("totals").path("create").asInt()).isEqualTo(1);
            assertThat(body.path("totals").path("update").asInt()).isEqualTo(1);
            assertThat(body.path("totals").path("unchanged").asInt()).isEqualTo(1);
            assertThat(lexemes.count()).isEqualTo(before);
            assertThat(lexemes.findById(second.getId()).orElseThrow().getMeaningVi()).isEqualTo("nghĩa cũ");
            ServiceRequest run = requests.findById(body.path("runId").asLong()).orElseThrow();
            JsonNode stored = mapper.valueToTree(run.getPayload().get("validRows"));
            assertThat(stored.get(0).path("lineNo").asInt()).isEqualTo(2);
            assertThat(stored.get(0).path("pinyinNorm").asText()).isEqualTo("wo");
            assertThat(stored.get(0).path("meaningNorm").asText()).isEqualTo("toi");
        } finally {
            lexemes.deleteAll(List.of(first, second));
        }
    }

    @Test
    void FR014_zeroValidRowsAndSeveralErrorsOnOneRowStillReturnPending() throws Exception {
        JsonNode body = json(upload("TOPIC", "code,name,hsk_level\n,,0\nT08,Name,1,extra\n"));
        assertThat(body.path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("totals").path("valid").asInt()).isZero();
        assertThat(body.path("totals").path("invalid").asInt()).isEqualTo(2);
        assertThat(body.path("errorCount").asInt()).isEqualTo(4);
        ServiceRequest run = requests.findById(body.path("runId").asLong()).orElseThrow();
        assertThat(run.getErrorRows().get(3)).containsEntry("column", null).containsEntry("value", null);
    }

    private MvcResult upload(String type, String csv) throws Exception {
        return mvc.perform(multipart(PATH).file(file("upload.csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer()).param("dataType", type))
                .andExpect(status().isCreated()).andReturn();
    }

    private HttpResponse<String> nativeUpload(byte[] bytes, String role, boolean cookie) throws Exception {
        String boundary = "test-" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"dataType\"\r\n\r\nTOPIC\r\n--"
                + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"upload.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (cookie) request.header("Cookie", "access_token=" + token(role));
        else request.header("Authorization", "Bearer " + token(role));
        return HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, bytes, tail)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
    private List<ServiceRequest> ownRuns() { return requests.findAll().stream().filter(r -> r.getUserId().equals(user.getId())).toList(); }
    private String bearer() { return "Bearer " + token("CONTENT_EDITOR"); }
    private String token(String role) { return jwt.issueAccessToken(user.getId(), List.of(role), true, UUID.randomUUID()); }
    private static MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("file", name, "text/csv", bytes); }
}
