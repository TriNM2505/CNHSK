package com.cnhsk.community.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "dev"})
@Import(ReportControllerIntegrationTest.ReportClockConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(OutputCaptureExtension.class)
class ReportControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String PATH = "/api/community/reports";
    private static final String PASSWORD = "ReportFixturePass123";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern VERIFY_TOKEN = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");
    private static final List<String> NONPUBLIC = List.of("DRAFT", "PENDING", "REJECTED", "HIDDEN");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ReportClock clock;
    @Value("${test.mailpit.base-url}") private String mailpitUrl;

    @BeforeEach
    void resetClockAndMail() throws Exception {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusNanos(123456789));
        HttpResponse<Void> response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitUrl + "/api/v1/messages")).DELETE().build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    @AfterEach
    void restoreClock() { clock.reset(); }

    @Test
    @Order(1)
    void FR032_FR038_generatedOpenApiExactlyMatchesTheLockedReportContract() throws Exception {
        JsonNode document = read(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
        assertThat(document.path("openapi").asText()).isEqualTo("3.1.0");
        JsonNode path = document.path("paths").path(PATH);
        assertThat(fields(path)).containsExactly("post");
        JsonNode operation = path.path("post");
        assertThat(operation.path("operationId").asText()).isEqualTo("reportContent");
        JsonNode body = operation.path("requestBody");
        assertThat(body.path("required").asBoolean()).isTrue();
        assertThat(fields(body.path("content"))).containsExactly("application/json");
        assertThat(body.path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/ReportWrite");
        assertThat(fields(operation.path("responses"))).containsExactlyInAnyOrder("201", "401", "403", "404", "409", "422", "429");
        for (String code : List.of("201", "401", "403", "404", "409", "422", "429")) {
            assertThat(operation.path("responses").path(code).path("content").path("application/json")
                    .path("schema").path("$ref").asText())
                    .isEqualTo("#/components/schemas/" + (code.equals("201") ? "ReportReceived" : "ErrorResponse"));
        }
        JsonNode write = document.path("components").path("schemas").path("ReportWrite");
        assertThat(strings(write.path("required"))).containsExactlyInAnyOrder("targetType", "targetId", "reason");
        assertThat(fields(write.path("properties"))).containsExactlyInAnyOrder("targetType", "targetId", "reason", "detail");
        JsonNode properties = write.path("properties");
        assertThat(strings(resolve(document, properties.path("targetType")).path("enum"))).containsExactlyInAnyOrder("POST", "COMMENT");
        assertThat(properties.path("targetId").path("type").asText()).isEqualTo("integer");
        assertThat(properties.path("targetId").path("format").asText()).isEqualTo("int64");
        assertThat(strings(resolve(document, properties.path("reason")).path("enum")))
                .containsExactlyInAnyOrder("SPAM", "OFFENSIVE", "HARASSMENT", "COPYRIGHT", "OTHER");
        assertThat(schemaTypes(properties.path("detail"))).containsExactlyInAnyOrder("string", "null");
        assertThat(properties.path("detail").path("maxLength").asInt()).isEqualTo(1000);
        JsonNode received = document.path("components").path("schemas").path("ReportReceived");
        assertThat(strings(received.path("required"))).containsExactly("received");
        assertThat(fields(received.path("properties"))).containsExactly("received");
        JsonNode flag = received.path("properties").path("received");
        assertThat(flag.path("type").asText()).isEqualTo("boolean");
        assertThat(flag.path("const").isBoolean()).isTrue();
        assertThat(flag.path("const").booleanValue()).isTrue();
        JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
        assertThat(security.isArray()).isTrue();
        assertThat(security).hasSize(2);
        List<List<String>> alternatives = new ArrayList<>();
        security.forEach(alternative -> {
            assertThat(alternative).hasSize(1);
            alternative.forEach(scopes -> assertThat(scopes).isEmpty());
            alternatives.add(fields(alternative));
        });
        assertThat(alternatives).containsExactlyInAnyOrder(List.of("cookieAuth"), List.of("bearerAuth"));
    }

    @Test
    void FR001_FR002_FR032_authenticationRoleCsrfValidationAndEmailGatesFollowTheRequiredOrder() throws Exception {
        Account author = verifiedAccount();
        Account pending = register();
        long postId = seedPost(author.id(), "APPROVED");
        Session unverified = login(pending.email());
        String valid = body("POST", postId, "SPAM", null);

        error(request("not-json"), 401, "UNAUTHENTICATED");
        error(request("not-json").cookie(unverified.access()), 403, "CSRF_TOKEN_INVALID");
        error(request(valid).cookie(unverified.access(), unverified.csrf()).header("X-XSRF-TOKEN", "wrong"),
                403, "CSRF_TOKEN_INVALID");
        error(cookieRequest(valid, unverified), 403, "ACCOUNT_UNVERIFIED");
        error(request(valid).header("Authorization", bearer(pending, false)), 403, "ACCOUNT_UNVERIFIED");
        error(cookieRequest("{}", unverified), 422, "VALIDATION_FAILED");
        error(cookieRequest(body("POST", postId, "OTHER", "\u3000\t\n"), unverified), 422, "VALIDATION_FAILED");
        for (String role : List.of("ADMIN", "MODERATOR", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT")) {
            String token = "Bearer " + jwt.issueAccessToken(author.id(), Set.of(role), true, UUID.randomUUID());
            error(request("not-json").header("Authorization", token), 403, "FORBIDDEN");
        }
        assertThat(reportCount(pending.id())).isZero();
        assertThat(caseCount("POST", postId)).isZero();

        verifyEmail(pending);
        Session verified = login(pending.email());
        received(cookieRequest(valid, verified));
        long commentId = seedComment(postId, author.id(), "VISIBLE");
        received(request(body("COMMENT", commentId, "OFFENSIVE", "Reason"))
                .header("Authorization", bearer(pending, true)));
        String mixed = "Bearer " + jwt.issueAccessToken(pending.id(), Set.of("ADMIN", "LEARNER"), true, UUID.randomUUID());
        received(request(body("POST", seedPost(author.id(), "APPROVED"), "COPYRIGHT", null)).header("Authorization", mixed));
    }

    @Test
    void FR032_invalidBodiesAlwaysReturnSafe422WithoutAnyReportRows() throws Exception {
        Account reporter = verifiedAccount();
        Account author = verifiedAccount();
        long target = seedPost(author.id(), "APPROVED");
        String token = bearer(reporter, true);
        String valid = body("POST", target, "SPAM", null);
        List<String> invalid = new ArrayList<>(List.of("", "not-json", "null", "[]", "42", "true", "{}"));
        invalid.add(valid + " {}");
        invalid.add(valid + " true");
        for (String field : List.of("targetType", "targetId", "reason")) {
            ObjectNode missing = (ObjectNode) mapper.readTree(valid);
            missing.remove(field);
            invalid.add(missing.toString());
            ObjectNode explicitNull = (ObjectNode) mapper.readTree(valid);
            explicitNull.putNull(field);
            invalid.add(explicitNull.toString());
        }
        for (String type : List.of("USER", "COLLECTION", "post", "", " POST ")) invalid.add(body(type, target, "SPAM", null));
        for (String reason : List.of("FAKE", "UNKNOWN", "spam", "", " SPAM ")) invalid.add(body("POST", target, reason, null));
        for (String field : List.of("targetType", "reason", "detail")) {
            for (JsonNode value : List.of(mapper.readTree("1"), mapper.readTree("true"), mapper.readTree("[]"), mapper.readTree("{}"))) {
                ObjectNode wrongType = (ObjectNode) mapper.readTree(valid);
                wrongType.set(field, value);
                invalid.add(wrongType.toString());
            }
        }
        for (String value : List.of("\"17\"", "1.5", "true", "[]", "{}", "9223372036854775808", "-9223372036854775809")) {
            ObjectNode wrongId = (ObjectNode) mapper.readTree(valid);
            wrongId.set("targetId", mapper.readTree(value));
            invalid.add(wrongId.toString());
        }
        for (String detail : List.of("", " \t\n\u3000", "\u2003")) invalid.add(body("POST", target, "OTHER", detail));
        invalid.add(body("POST", target, "OTHER", null));
        invalid.add(body("POST", target, "SPAM", "x".repeat(1001)));
        invalid.add(body("POST", target, "OTHER", "x".repeat(1001)));
        for (String content : invalid) {
            JsonNode failure = error(request(content).header("Authorization", token), 422, "VALIDATION_FAILED");
            assertThat(failure.path("details").isArray()).isTrue();
            assertThat(failure.path("details").isEmpty()).isFalse();
        }
        assertThat(reportCount(reporter.id())).isZero();
        assertThat(caseCount("POST", target)).isZero();
    }

    @Test
    void FR032_FR034_everyReasonAcceptsTheThousandCharacterBoundaryAndBothTargetsRemainUnchanged() throws Exception {
        Account reporter = verifiedAccount();
        Account author = verifiedAccount();
        String token = bearer(reporter, true);
        for (String reason : List.of("SPAM", "OFFENSIVE", "HARASSMENT", "COPYRIGHT", "OTHER")) {
            long postId = seedPost(author.id(), "APPROVED");
            long commentId = seedComment(postId, author.id(), "VISIBLE");
            var postBefore = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId);
            var commentBefore = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", commentId);
            String detail = "文".repeat(1000);
            received(request(body("POST", postId, reason, "\u3000" + detail + "\u3000")).header("Authorization", token));
            received(request(body("COMMENT", commentId, reason, detail)).header("Authorization", token));
            oneEntry("POST", postId, reporter.id(), reason, detail);
            oneEntry("COMMENT", commentId, reporter.id(), reason, detail);
            assertThat(jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId)).isEqualTo(postBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", commentId)).isEqualTo(commentBefore);
        }
        assertThat(reportCount(reporter.id())).isEqualTo(10);
    }

    @Test
    void FR033_FR035_missingAndNonpublicTargetsWinOverSelfDuplicateAndFullQuota() throws Exception {
        Account reporter = verifiedAccount();
        Account author = verifiedAccount();
        String token = bearer(reporter, true);
        seedQuota(reporter.id(), author.id(), 20, clock.instant());
        JsonNode missing = error(request(body("POST", Long.MAX_VALUE, "SPAM", null)).header("Authorization", token),
                404, "TARGET_NOT_FOUND");
        for (long target : List.of(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            for (String type : List.of("POST", "COMMENT")) {
                JsonNode response = error(request(body(type, target, "SPAM", null)).header("Authorization", token),
                        404, "TARGET_NOT_FOUND");
                assertThat(withoutRequestId(response)).isEqualTo(withoutRequestId(missing));
            }
        }
        for (String state : NONPUBLIC) {
            for (long owner : List.of(reporter.id(), author.id())) {
                long postId = seedPost(owner, state);
                long commentId = seedComment(postId, owner, "VISIBLE");
                for (var target : Map.of("POST", postId, "COMMENT", commentId).entrySet()) {
                    seedReaction(reporter.id(), target.getKey(), target.getValue(), clock.instant().minusSeconds(86400));
                    JsonNode response = error(request(body(target.getKey(), target.getValue(), "SPAM", null)).header("Authorization", token),
                            404, "TARGET_NOT_FOUND");
                    assertThat(withoutRequestId(response)).isEqualTo(withoutRequestId(missing));
                    assertThat(caseCount(target.getKey(), target.getValue())).isZero();
                }
            }
        }
        long approved = seedPost(author.id(), "APPROVED");
        for (String state : List.of("HIDDEN", "DELETED")) {
            long commentId = seedComment(approved, author.id(), state);
            error(request(body("COMMENT", commentId, "SPAM", null)).header("Authorization", token), 404, "TARGET_NOT_FOUND");
        }
        long ownPost = seedPost(reporter.id(), "APPROVED");
        long ownComment = seedComment(approved, reporter.id(), "VISIBLE");
        error(request(body("POST", ownPost, "SPAM", null)).header("Authorization", token), 422, "SELF_REPORT");
        error(request(body("COMMENT", ownComment, "SPAM", null)).header("Authorization", token), 422, "SELF_REPORT");
        assertThat(caseCount("POST", ownPost)).isZero();
        assertThat(caseCount("COMMENT", ownComment)).isZero();
    }

    @Test
    void FR035_FR036_nineteenthAndTwentiethAreAcceptedTwentyFirstRejectedAndDuplicateStill409AtFullQuota() throws Exception {
        Account reporter = verifiedAccount();
        Account author = verifiedAccount();
        Instant midnight = clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        clock.set(midnight.minusSeconds(1).plusNanos(999));
        seedQuota(reporter.id(), author.id(), 18, clock.instant().minusSeconds(1));
        long nineteenth = seedPost(author.id(), "APPROVED");
        long twentieth = seedComment(nineteenth, author.id(), "VISIBLE");
        long twentyFirst = seedPost(author.id(), "APPROVED");
        String token = bearer(reporter, true);
        received(request(body("POST", nineteenth, "SPAM", null)).header("Authorization", token));
        received(request(body("COMMENT", twentieth, "HARASSMENT", null)).header("Authorization", token));
        assertThat(reportCount(reporter.id())).isEqualTo(20);
        List<Map<String, Object>> rowsBefore = reporterRows(reporter.id());
        Map<String, Object> caseBefore = pendingCase("POST", nineteenth);
        MvcResult blocked = mvc.perform(request(body("POST", twentyFirst, "SPAM", null)).header("Authorization", token))
                .andExpect(status().isTooManyRequests()).andReturn();
        JsonNode quota = assertError(blocked, "TOO_MANY_REQUESTS");
        assertThat(quota.path("retryAfterSeconds").asLong()).isEqualTo(1);
        error(request(body("POST", nineteenth, "OTHER", "Repeated reason")).header("Authorization", token),
                409, "DUPLICATE_REPORT");
        assertThat(reporterRows(reporter.id())).isEqualTo(rowsBefore);
        assertThat(pendingCase("POST", nineteenth)).isEqualTo(caseBefore);
        assertThat(caseCount("POST", twentyFirst)).isZero();

        clock.set(midnight);
        token = bearer(reporter, true);
        received(request(body("POST", twentyFirst, "SPAM", null)).header("Authorization", token));
        assertThat(reportCount(reporter.id())).isEqualTo(21);
        error(request(body("POST", nineteenth, "SPAM", null)).header("Authorization", token), 409, "DUPLICATE_REPORT");
    }

    @Test
    void FR036_FR038_jsonbRoundTripKeepsNullUnicodeAndEscapingWithOneSharedMicrosecondTimestampAndNoIdentityResponse(CapturedOutput output) throws Exception {
        Account reporter = verifiedAccount();
        Account author = verifiedAccount();
        long postId = seedPost(author.id(), "APPROVED");
        long commentId = seedComment(postId, author.id(), "VISIBLE");
        var postBefore = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId);
        var commentBefore = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", commentId);
        String token = bearer(reporter, true);
        String detail = "Synthetic private report 举报 tiếng Việt \"quoted\" \\ line\n第二行 😀";
        ObjectNode metadata = (ObjectNode) mapper.readTree(body("COMMENT", commentId, "OTHER", "\u3000 " + detail + " \u3000"));
        metadata.put("userId", author.id());
        metadata.put("reportCount", 900);
        metadata.put("targetTypeValid", true);
        metadata.put("reportedAt", "2000-01-01T00:00:00Z");
        received(request(metadata.toString()).header("Authorization", token));
        JsonNode commentEntry = oneEntry("COMMENT", commentId, reporter.id(), "OTHER", detail);
        assertThat(Instant.parse(commentEntry.path("reportedAt").asText())).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MICROS));
        received(request(body("POST", postId, "SPAM", " \t\n\u3000")).header("Authorization", token));
        oneEntry("POST", postId, reporter.id(), "SPAM", null);
        assertThat(jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId)).isEqualTo(postBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", commentId)).isEqualTo(commentBefore);
        assertThat(output.getAll()).doesNotContain(detail, "ReportEntry[userId=" + reporter.id());
    }

    @Test
    void FR037_FR038_tenDifferentReportersAggregateWithoutAutoHidingAndKeepIdentityOutOfPublicReadModels() throws Exception {
        Account author = verifiedAccount();
        long postId = seedPost(author.id(), "APPROVED");
        var before = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId);
        Set<Long> reporters = new HashSet<>();
        for (int index = 0; index < 10; index++) {
            Account reporter = verifiedAccount();
            reporters.add(reporter.id());
            received(request(body("POST", postId, "SPAM", null)).header("Authorization", bearer(reporter, true)));
        }
        Map<String, Object> pending = pendingCase("POST", postId);
        assertThat(((Number) pending.get("report_count")).intValue()).isEqualTo(10);
        assertThat(pending.get("auto_hidden")).isEqualTo(false);
        JsonNode reasons = mapper.readTree(pending.get("reasons").toString());
        assertThat(reasons).hasSize(10);
        Set<Long> storedReporters = new HashSet<>();
        reasons.forEach(entry -> storedReporters.add(entry.path("userId").asLong()));
        assertThat(storedReporters).isEqualTo(reporters);
        assertThat(jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId)).isEqualTo(before);
        JsonNode detail = read(mvc.perform(get("/api/public/community/posts/{id}", postId)).andExpect(status().isOk()).andReturn());
        assertThat(fields(detail)).doesNotContain("reasons", "reports", "reportCount", "reporter", "userId", "autoHidden");
        JsonNode state = read(mvc.perform(get("/api/community/posts/{id}/viewer-state", postId)
                .header("Authorization", bearer(author, true))).andExpect(status().isOk()).andReturn());
        assertThat(fields(state)).containsExactlyInAnyOrder("liked", "reported");
    }

    @Test
    void FR036_aClosedCaseStaysImmutableWhileANewReporterCreatesANewPendingCaseAndPreviousReporterCannotRepeat() throws Exception {
        Account first = verifiedAccount();
        Account second = verifiedAccount();
        Account author = verifiedAccount();
        for (String type : List.of("POST", "COMMENT")) {
            long postId = seedPost(author.id(), "APPROVED");
            long target = type.equals("POST") ? postId : seedComment(postId, author.id(), "VISIBLE");
            received(request(body(type, target, "COPYRIGHT", "First reason")).header("Authorization", bearer(first, true)));
            long closedId = ((Number) pendingCase(type, target).get("id")).longValue();
            jdbc.update("UPDATE community.moderation_cases SET status='REJECTED',handled_by=?,handled_at=?,decision_note=? WHERE id=?",
                    author.id(), Timestamp.from(clock.instant()), "Synthetic closed case", closedId);
            var closedBefore = jdbc.queryForMap("SELECT * FROM community.moderation_cases WHERE id=?", closedId);
            var reactionBefore = reporterRows(first.id());
            error(request(body(type, target, "OTHER", "Repeat after closure")).header("Authorization", bearer(first, true)),
                    409, "DUPLICATE_REPORT");
            assertThat(jdbc.queryForMap("SELECT * FROM community.moderation_cases WHERE id=?", closedId)).isEqualTo(closedBefore);
            assertThat(reporterRows(first.id())).isEqualTo(reactionBefore);
            received(request(body(type, target, "HARASSMENT", "Second reporter")).header("Authorization", bearer(second, true)));
            assertThat(caseCount(type, target)).isEqualTo(2);
            assertThat(((Number) pendingCase(type, target).get("id")).longValue()).isNotEqualTo(closedId);
            oneEntry(type, target, second.id(), "HARASSMENT", "Second reporter");
            assertThat(jdbc.queryForMap("SELECT * FROM community.moderation_cases WHERE id=?", closedId)).isEqualTo(closedBefore);
            error(request(body(type, target, "SPAM", null)).header("Authorization", bearer(first, true)), 409, "DUPLICATE_REPORT");
            assertThat(((Number) pendingCase(type, target).get("report_count")).intValue()).isEqualTo(1);
        }
    }

    private JsonNode oneEntry(String type, long target, long reporter, String reason, String detail) throws Exception {
        Map<String, Object> pending = pendingCase(type, target);
        assertThat(pending.get("status")).isEqualTo("PENDING");
        assertThat(pending.get("auto_hidden")).isEqualTo(false);
        assertThat(((Number) pending.get("report_count")).intValue()).isEqualTo(1);
        JsonNode reasons = mapper.readTree(pending.get("reasons").toString());
        assertThat(reasons.isArray()).isTrue();
        assertThat(reasons).hasSize(1);
        JsonNode entry = reasons.get(0);
        assertThat(fields(entry)).containsExactlyInAnyOrder("userId", "reason", "detail", "reportedAt");
        assertThat(entry.path("userId").asLong()).isEqualTo(reporter);
        assertThat(entry.path("reason").asText()).isEqualTo(reason);
        if (detail == null) assertThat(entry.path("detail").isNull()).isTrue();
        else assertThat(entry.path("detail").asText()).isEqualTo(detail);
        assertThat(entry.path("reportedAt").isTextual()).isTrue();
        Instant at = Instant.parse(entry.path("reportedAt").asText());
        assertThat(at).isEqualTo(clock.instant().truncatedTo(ChronoUnit.MICROS));
        assertThat(((Timestamp) pending.get("created_at")).toInstant()).isEqualTo(at);
        Timestamp reactionTime = jdbc.queryForObject("SELECT created_at FROM community.reactions "
                        + "WHERE user_id=? AND target_type=? AND target_id=? AND kind='REPORT'", Timestamp.class, reporter, type, target);
        assertThat(reactionTime.toInstant()).isEqualTo(at);
        return entry;
    }

    private void seedQuota(long reporter, long author, int count, Instant at) {
        for (int index = 0; index < count; index++) seedReaction(reporter, "POST", seedPost(author, "APPROVED"), at);
    }

    private void seedReaction(long reporter, String type, long target, Instant at) {
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind,created_at) VALUES (?,?,?,'REPORT',?)",
                reporter, type, target, Timestamp.from(at));
    }

    private long seedPost(long author, String state) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,published_at,created_at,updated_at) "
                        + "VALUES (?,'QUESTION','Synthetic report target','Private synthetic body',?,'Private synthetic reason',?,?,?) RETURNING id",
                Long.class, author, state, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
    }

    private long seedComment(long postId, long author, String state) {
        return jdbc.queryForObject("INSERT INTO community.comments(post_id,author_id,content,status,moderation_reason,created_at,updated_at) "
                        + "VALUES (?,?,'Private synthetic comment',?,'Private synthetic reason',?,?) RETURNING id",
                Long.class, postId, author, state, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
    }

    private long reportCount(long reporter) {
        return jdbc.queryForObject("SELECT count(*) FROM community.reactions WHERE user_id=? AND kind='REPORT'", Long.class, reporter);
    }

    private long caseCount(String type, long target) {
        return jdbc.queryForObject("SELECT count(*) FROM community.moderation_cases WHERE target_type=? AND target_id=?", Long.class, type, target);
    }

    private Map<String, Object> pendingCase(String type, long target) {
        return jdbc.queryForMap("SELECT * FROM community.moderation_cases WHERE target_type=? AND target_id=? AND status='PENDING'", type, target);
    }

    private List<Map<String, Object>> reporterRows(long reporter) {
        return jdbc.queryForList("SELECT * FROM community.reactions WHERE user_id=? ORDER BY target_type,target_id,kind", reporter);
    }

    private Account verifiedAccount() throws Exception {
        Account account = register();
        verifyEmail(account);
        return account;
    }

    private Account register() throws Exception {
        String email = "report-" + UUID.randomUUID() + "@example.test";
        JsonNode result = read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "confirmPassword", PASSWORD,
                        "displayName", "Synthetic Report Account", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn());
        assertThat(result.path("userId").asLong()).isPositive();
        return new Account(result.path("userId").asLong(), email);
    }

    private void verifyEmail(Account account) throws Exception {
        JsonNode found = null;
        for (JsonNode message : mail("/api/v1/messages").path("messages")) {
            JsonNode full = mail("/api/v1/message/" + message.path("ID").asText());
            if (full.path("To").toString().contains(account.email())) { found = full; break; }
        }
        assertThat(found != null).as("verification mail delivered to synthetic account").isTrue();
        Matcher token = VERIFY_TOKEN.matcher(found.path("Text").asText());
        assertThat(token.find()).as("verification link exists").isTrue();
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", token.group(1))))).andExpect(status().isOk());
    }

    private JsonNode mail(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder().uri(URI.create(mailpitUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private Session login(String email) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        Cookie access = response.getResponse().getCookie("access_token");
        Cookie csrf = response.getResponse().getCookie("XSRF-TOKEN");
        assertThat(access).isNotNull();
        assertThat(csrf).isNotNull();
        return new Session(access, csrf);
    }

    private String bearer(Account account, boolean verified) {
        return "Bearer " + jwt.issueAccessToken(account.id(), Set.of("LEARNER"), verified, UUID.randomUUID());
    }

    private MockHttpServletRequestBuilder request(String body) { return post(PATH).contentType(MediaType.APPLICATION_JSON).content(body); }
    private MockHttpServletRequestBuilder cookieRequest(String body, Session session) {
        return request(body).cookie(session.access(), session.csrf()).header("X-XSRF-TOKEN", session.csrf().getValue());
    }

    private void received(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode result = read(mvc.perform(request).andExpect(status().isCreated()).andReturn());
        assertThat(fields(result)).containsExactly("received");
        assertThat(result.path("received").isBoolean()).isTrue();
        assertThat(result.path("received").booleanValue()).isTrue();
    }

    private JsonNode error(MockHttpServletRequestBuilder request, int statusCode, String code) throws Exception {
        return assertError(mvc.perform(request).andExpect(status().is(statusCode)).andReturn(), code);
    }

    private JsonNode assertError(MvcResult result, String code) throws Exception {
        JsonNode body = read(result);
        assertThat(body.path("error_code").asText()).isEqualTo(code);
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("stackTrace", "Exception", "org.hibernate", "java.lang", "SELECT ", "INSERT ");
        assertThat(fields(body)).doesNotContain("reporter", "userId", "reasons", "reportCount", "caseId");
        return body;
    }

    private JsonNode read(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsByteArray()); }
    private String body(String type, long target, String reason, String detail) throws Exception {
        ObjectNode body = mapper.createObjectNode().put("targetType", type).put("targetId", target).put("reason", reason);
        if (detail == null) body.putNull("detail"); else body.put("detail", detail);
        return mapper.writeValueAsString(body);
    }
    private ObjectNode withoutRequestId(JsonNode node) {
        ObjectNode copy = node.deepCopy();
        copy.remove("request_id");
        return copy;
    }
    private static List<String> fields(JsonNode node) {
        return java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(node.fieldNames(), 0), false).toList();
    }
    private static List<String> strings(JsonNode array) {
        List<String> result = new ArrayList<>();
        array.forEach(value -> result.add(value.asText()));
        return result;
    }
    private static JsonNode resolve(JsonNode document, JsonNode schema) {
        return schema.has("$ref") ? document.at(schema.path("$ref").asText().substring(1)) : schema;
    }
    private static Set<String> schemaTypes(JsonNode schema) {
        Set<String> result = new HashSet<>();
        JsonNode type = schema.path("type");
        if (type.isTextual()) result.add(type.asText());
        else if (type.isArray()) type.forEach(value -> result.add(value.asText()));
        for (String keyword : List.of("oneOf", "anyOf")) schema.path(keyword).forEach(part -> result.addAll(schemaTypes(part)));
        return result;
    }
    private record Account(long id, String email) { }
    private record Session(Cookie access, Cookie csrf) { }

    @TestConfiguration
    static class ReportClockConfiguration {
        @Bean
        @Primary
        ReportClock reportIntegrationClock() { return new ReportClock(); }
    }

    static class ReportClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        void set(Instant now) { instant.set(now); }
        void reset() { instant.set(null); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() {
            Instant fixed = instant.get();
            return fixed == null ? Instant.now() : fixed;
        }
    }
}
