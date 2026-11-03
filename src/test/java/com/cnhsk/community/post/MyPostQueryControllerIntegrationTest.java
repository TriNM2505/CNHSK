package com.cnhsk.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "dev"})
@Import(MyPostQueryControllerIntegrationTest.ReadClockConfiguration.class)
class MyPostQueryControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/community/posts";
    private static final String PASSWORD = "OwnPostTestPass123";
    private static final Instant TIME = Instant.parse("2026-10-30T12:00:00Z");
    private static final List<String> STATES = List.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "HIDDEN");
    private static final Set<String> PRIVATE_FIELDS = Set.of("email", "password", "passwordHash", "roles",
            "reviewedBy", "reviewedAt", "moderatedBy", "moderationReason", "commentsLocked", "authorId",
            "phone", "username", "moderationCases", "reports");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Value("${test.mailpit.base-url}") private String mailpitUrl;

    @BeforeEach
    @AfterEach
    void cleanCommunityFixtures() {
        jdbc.update("DELETE FROM community.reactions");
        jdbc.update("DELETE FROM community.comments");
        jdbc.update("DELETE FROM community.posts");
    }

    @Test
    void FR001_privateReadsRequireLearnerButAllowUnverifiedLearnersWithoutAdminInheritance() throws Exception {
        Account owner = register();
        long id = seed(owner.id(), "APPROVED", "Stored reason", TIME);
        List<String> paths = List.of(BASE + "/mine", BASE + "/mine/" + id, BASE + "/" + id + "/viewer-state");
        for (String path : paths) {
            error(get(path), 401, "UNAUTHENTICATED");
            for (String role : List.of("ADMIN", "MODERATOR", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT")) {
                error(get(path).header("Authorization", bearer(owner.id(), Set.of(role), true)), 403, "FORBIDDEN");
            }
            ok(get(path).header("Authorization", bearer(owner.id(), Set.of("LEARNER"), false)));
            ok(get(path).header("Authorization", bearer(owner.id(), Set.of("ADMIN", "LEARNER"), false)));
            String cookieToken = jwt.issueAccessToken(owner.id(), Set.of("LEARNER"), false, UUID.randomUUID());
            ok(get(path).cookie(new Cookie("access_token", cookieToken)));
        }
    }

    @Test
    void FR016_ownerListFiltersAllStatusesAndOrdersUpdateTimeThenIdWithoutAcceptingForgedFilters() throws Exception {
        Account a = register();
        Account b = register();
        List<Long> expected = new ArrayList<>();
        for (String state : STATES) {
            expected.add(0, seed(a.id(), state, "Stored reason " + state, TIME));
            seed(b.id(), state, "Other reason", TIME.plusSeconds(20));
        }
        long newest = seed(a.id(), "DRAFT", null, TIME.plusSeconds(1));
        expected.add(0, newest);
        String token = bearer(a.id(), Set.of("LEARNER"), false);
        JsonNode all = ok(get(BASE + "/mine").header("Authorization", token));
        assertThat(ids(all)).containsExactlyElementsOf(expected);
        assertThat(all.path("page").asInt()).isZero();
        assertThat(all.path("size").asInt()).isEqualTo(20);
        assertThat(all.path("totalElements").asLong()).isEqualTo(6);
        assertThat(ok(get(BASE + "/mine").header("Authorization", token)
                .param("authorId", Long.toString(b.id())).param("category", "CULTURE"))).isEqualTo(all);
        assertThat(ok(get(BASE + "/mine").header("Authorization", token)
                .param("status", "").param("page", "").param("size", ""))).isEqualTo(all);
        for (String state : STATES) {
            JsonNode page = ok(get(BASE + "/mine").header("Authorization", token).param("status", state));
            assertThat(page.path("totalElements").asLong()).isEqualTo(state.equals("DRAFT") ? 2 : 1);
            page.path("items").forEach(item -> assertThat(item.path("status").asText()).isEqualTo(state));
        }
        assertThat(ids(ok(get(BASE + "/mine").header("Authorization", token).param("page", "1").param("size", "2"))))
                .containsExactlyElementsOf(expected.subList(2, 4));
        assertPrivateFieldsAbsent(all);
    }

    @Test
    void FR016_ownerReadsPreserveDatabaseMetadataAndMaskReasonsForEveryStatus() throws Exception {
        Account a = register();
        String token = bearer(a.id(), Set.of("LEARNER"), false);
        List<Long> ids = new ArrayList<>();
        for (String state : STATES) ids.add(seed(a.id(), state, "Old reason " + state, TIME));
        long hiddenWithoutReason = seed(a.id(), "HIDDEN", null, TIME);
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM community.posts ORDER BY id");
        JsonNode page = ok(get(BASE + "/mine").header("Authorization", token));
        for (long id : ids) {
            JsonNode detail = ok(get(BASE + "/mine/{id}", id).header("Authorization", token));
            String state = detail.path("status").asText();
            assertThat(detail.has("reviewReason")).isTrue();
            if (state.equals("REJECTED") || state.equals("HIDDEN")) {
                assertThat(detail.path("reviewReason").asText()).isEqualTo("Old reason " + state);
            } else assertThat(detail.path("reviewReason").isNull()).isTrue();
            assertThat(detail.path("publishedAt").asText()).isEqualTo(TIME.toString());
            assertThat(detail.path("content").asText()).isEqualTo("Literal <b>body</b> **text**");
            JsonNode listItem = null;
            for (JsonNode item : page.path("items")) if (item.path("id").asLong() == id) listItem = item;
            assertThat(detail).isEqualTo(listItem);
            assertThat(fields(detail)).containsExactlyInAnyOrder("id", "title", "content", "category", "status",
                    "reviewReason", "publishedAt", "createdAt", "updatedAt");
            assertPrivateFieldsAbsent(detail);
        }
        JsonNode nullReason = ok(get(BASE + "/mine/{id}", hiddenWithoutReason).header("Authorization", token));
        assertThat(nullReason.has("reviewReason")).isTrue();
        assertThat(nullReason.path("reviewReason").isNull()).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM community.posts ORDER BY id")).isEqualTo(before);
    }

    @Test
    void FR016_nonOwnerMissingAndMalformedOwnerIdsHaveEquivalentSafeForbiddenErrors() throws Exception {
        Account a = register();
        Account b = register();
        long other = seed(b.id(), "PENDING", "Private reason", TIME);
        String token = bearer(a.id(), Set.of("LEARNER"), false);
        JsonNode expected = error(get(BASE + "/mine/{id}", other).header("Authorization", token), 403, "FORBIDDEN");
        for (String id : List.of(Long.toString(Long.MAX_VALUE), "0", "-1", "abc", "9223372036854775808", "-9223372036854775809")) {
            assertThat(error(get(BASE + "/mine/{id}", id).header("Authorization", token), 403, "FORBIDDEN"))
                    .isEqualTo(expected);
        }
    }

    @Test
    void FR016_listBindingRejectsInvalidAndOverflowParametersAndHandlesHugePages() throws Exception {
        Account a = register();
        String token = bearer(a.id(), Set.of("LEARNER"), false);
        JsonNode empty = ok(get(BASE + "/mine").header("Authorization", token));
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("totalElements").asLong()).isZero();
        seed(a.id(), "DRAFT", null, TIME);
        for (String[] invalid : List.of(new String[] {"status", "NEWS"}, new String[] {"status", "pending"},
                new String[] {"page", "-1"}, new String[] {"page", "abc"}, new String[] {"page", "2147483648"},
                new String[] {"page", "-2147483649"}, new String[] {"size", "abc"}, new String[] {"size", "0"},
                new String[] {"size", "-1"}, new String[] {"size", "51"}, new String[] {"size", "2147483648"},
                new String[] {"size", "-2147483649"})) {
            error(get(BASE + "/mine").header("Authorization", token).param(invalid[0], invalid[1]), 422, "VALIDATION_FAILED");
        }
        for (String number : List.of("99", Integer.toString(Integer.MAX_VALUE))) {
            JsonNode page = ok(get(BASE + "/mine").header("Authorization", token).param("page", number).param("size", "50"));
            assertThat(page.path("items")).isEmpty();
            assertThat(page.path("totalElements").asLong()).isEqualTo(1);
            assertThat(page.path("page").asInt()).isEqualTo(Integer.parseInt(number));
        }
    }

    @Test
    void FR056_viewerStateUsesOnlyOwnPostReactionsAndHidesAllNonpublicStates() throws Exception {
        Account a = register();
        Account b = register();
        String token = bearer(a.id(), Set.of("LEARNER"), false);
        for (int flags = 0; flags < 4; flags++) {
            long id = seed(b.id(), "APPROVED", null, TIME);
            reaction(b.id(), "POST", id, "LIKE");
            reaction(b.id(), "POST", id, "REPORT");
            reaction(a.id(), "COMMENT", id, "LIKE");
            reaction(a.id(), "COMMENT", id, "REPORT");
            reaction(a.id(), "POST", id, "FOLLOW");
            if ((flags & 1) != 0) reaction(a.id(), "POST", id, "LIKE");
            if ((flags & 2) != 0) reaction(a.id(), "POST", id, "REPORT");
            JsonNode state = ok(get(BASE + "/{id}/viewer-state", id).header("Authorization", token));
            assertThat(fields(state)).containsExactlyInAnyOrder("liked", "reported");
            assertThat(state.path("liked").asBoolean()).isEqualTo((flags & 1) != 0);
            assertThat(state.path("reported").asBoolean()).isEqualTo((flags & 2) != 0);
            assertPrivateFieldsAbsent(state);
        }
        JsonNode missing = error(get(BASE + "/{id}/viewer-state", Long.MAX_VALUE).header("Authorization", token), 404, "POST_NOT_FOUND");
        for (String state : STATES) {
            if (state.equals("APPROVED")) continue;
            for (long author : List.of(a.id(), b.id())) {
                long id = seed(author, state, "Synthetic reason", TIME);
                assertThat(error(get(BASE + "/{id}/viewer-state", id).header("Authorization", token), 404, "POST_NOT_FOUND"))
                        .isEqualTo(missing);
            }
        }
        for (String id : List.of("0", "-1", "abc", "9223372036854775808", "-9223372036854775809")) {
            assertThat(error(get(BASE + "/{id}/viewer-state", id).header("Authorization", token), 404, "POST_NOT_FOUND"))
                    .isEqualTo(missing);
        }
    }

    @Test
    void FR056_literalMineViewerStateSelectsViewerHandlerAndReturnsSafeNotFound() throws Exception {
        Account a = register();
        String token = bearer(a.id(), Set.of("LEARNER"), false);
        assertHandler(get(BASE + "/mine").header("Authorization", token), 200, "listMyPosts");
        long id = seed(a.id(), "APPROVED", null, TIME);
        assertHandler(get(BASE + "/mine/{id}", id).header("Authorization", token), 200, "getMyPost");
        assertHandler(get(BASE + "/{id}/viewer-state", id).header("Authorization", token), 200, "getViewerState");
        MvcResult result = assertHandler(get(BASE + "/mine/viewer-state").header("Authorization", token), 404, "getViewerState");
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).path("error_code").asText()).isEqualTo("POST_NOT_FOUND");
    }

    @Test
    void FR016_verifiedCreateAndUpdateResponsesEqualTheirOwnerReadProjections() throws Exception {
        Account a = register();
        verifyEmail(a);
        JsonNode login = ok(post("/api/auth/login").header("X-Client-Type", "MOBILE").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD))));
        String token = "Bearer " + login.path("accessToken").asText();
        String body = mapper.writeValueAsString(Map.of("title", " Draft ", "content", " Body ", "category", "QUESTION", "submit", false));
        JsonNode created = mapper.readTree(mvc.perform(post(BASE).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = created.path("id").asLong();
        assertThat(ok(get(BASE + "/mine/{id}", id).header("Authorization", token))).isEqualTo(created);
        body = mapper.writeValueAsString(Map.of("title", "Revised", "content", "Updated body", "category", "CULTURE", "submit", true));
        JsonNode updated = ok(put(BASE + "/{id}", id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
        assertThat(ok(get(BASE + "/mine/{id}", id).header("Authorization", token))).isEqualTo(updated);
        assertThat(ok(get(BASE + "/mine").header("Authorization", token)).path("items").get(0)).isEqualTo(updated);
        error(put(BASE + "/abc").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body), 422, "VALIDATION_FAILED");
    }

    @Test
    void FR016_runtimeSwaggerMatchesPrivateContractsSecurityAndResponseReferences() throws Exception {
        JsonNode document = ok(get("/v3/api-docs"));
        for (String[] contract : List.of(new String[] {"/mine", "listMyPosts", "OwnPostPage", "200,401,403,422"},
                new String[] {"/mine/{id}", "getMyPost", "OwnPost", "200,401,403"},
                new String[] {"/{id}/viewer-state", "getViewerState", "ViewerState", "200,401,403,404"})) {
            JsonNode operation = document.path("paths").path(BASE + contract[0]).path("get");
            assertThat(operation.path("operationId").asText()).isEqualTo(contract[1]);
            assertThat(fields(operation.path("responses"))).containsExactlyInAnyOrder(contract[3].split(","));
            JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
            assertThat(security.isArray()).isTrue();
            assertThat(security).isNotEmpty();
            assertThat(security.toString()).contains("cookieAuth", "bearerAuth");
            for (String code : contract[3].split(",")) {
                assertThat(operation.path("responses").path(code).toString()).contains("#/components/schemas/"
                        + (code.equals("200") ? contract[2] : "ErrorResponse"));
            }
        }
        JsonNode page = document.path("components").path("schemas").path("OwnPostPage");
        assertThat(fields(page.path("properties"))).containsExactlyInAnyOrder("items", "page", "size", "totalElements");
        assertThat(page.path("properties").path("items").path("items").path("$ref").asText()).isEqualTo("#/components/schemas/OwnPost");
    }

    private Account register() throws Exception {
        String email = "own-query-" + UUID.randomUUID() + "@example.test";
        JsonNode result = mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD, "confirmPassword", PASSWORD,
                        "displayName", "Synthetic Owner", "acceptTerms", true)))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(result.path("userId").asLong()).isPositive();
        return new Account(result.path("userId").asLong(), email);
    }

    private long seed(long author, String state, String reason, Instant updated) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,published_at,created_at,updated_at) "
                + "VALUES (?,'QUESTION','Synthetic fixture','Literal <b>body</b> **text**',?,?,?,?,?) RETURNING id", Long.class,
                author, state, reason, Timestamp.from(TIME), Timestamp.from(TIME.minusSeconds(10)), Timestamp.from(updated));
    }

    private String bearer(long id, Set<String> roles, boolean verified) {
        return "Bearer " + jwt.issueAccessToken(id, roles, verified, UUID.randomUUID());
    }

    private void reaction(long user, String type, long id, String kind) {
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,?,?,?)", user, type, id, kind);
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode error(MockHttpServletRequestBuilder request, int code, String errorCode) throws Exception {
        JsonNode body = mapper.readTree(mvc.perform(request).andExpect(status().is(code)).andReturn().getResponse().getContentAsString());
        assertThat(body.path("error_code").asText()).isEqualTo(errorCode);
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain("Exception", "stackTrace", "java.lang", "org.springframework", "SELECT ");
        if (code != 422) assertThat(fields(body)).containsExactlyInAnyOrder("error_code", "message", "request_id");
        assertPrivateFieldsAbsent(body);
        ((ObjectNode) body).remove("request_id");
        return body;
    }

    private MvcResult assertHandler(MockHttpServletRequestBuilder request, int code, String name) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(code)).andReturn();
        assertThat(result.getHandler()).isInstanceOf(HandlerMethod.class);
        assertThat(((HandlerMethod) result.getHandler()).getMethod().getName()).isEqualTo(name);
        return result;
    }

    private List<String> fields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private void assertPrivateFieldsAbsent(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            assertThat(PRIVATE_FIELDS).doesNotContain(field.getKey());
            assertPrivateFieldsAbsent(field.getValue());
        });
        else if (node.isArray()) node.forEach(this::assertPrivateFieldsAbsent);
    }

    private void verifyEmail(Account account) throws Exception {
        JsonNode verification = null;
        for (JsonNode message : mailpit("/api/v1/messages").path("messages")) {
            if (message.toString().contains(account.email())) {
                verification = mailpit("/api/v1/message/" + message.path("ID").asText());
                break;
            }
        }
        assertThat(verification).isNotNull();
        var matcher = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)").matcher(verification.path("Text").asText());
        assertThat(matcher.find()).isTrue();
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token", matcher.group(1))))).andExpect(status().isOk());
    }

    private JsonNode mailpit(String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private record Account(long id, String email) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReadClockConfiguration {
        @Bean
        @Primary
        Clock ownPostReadClock() { return Clock.tickMillis(ZoneOffset.UTC); }
    }
}
