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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.cnhsk.community.common.PostCategory;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles({"test", "dev"})
class PublicPostControllerIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/public/community/posts";
    private static final String PASSWORD = "PublicPostTestPass123";
    private static final Instant PUBLISHED = Instant.parse("2026-10-30T12:00:00Z");
    private static final Set<String> PRIVATE_FIELDS = Set.of("email", "password", "passwordHash", "roles",
            "status", "reviewReason", "reviewedBy", "reviewedAt", "commentsLocked", "createdAt", "updatedAt",
            "liked", "reported", "viewerState", "authorId", "phone", "username");
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
    void FR013_approvedStatusIsRequiredForEveryPublicListAndDetailRegardlessOfViewerRole() throws Exception {
        Account author = register();
        long approved = 0;
        List<Long> privateIds = new ArrayList<>();
        for (String state : List.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "HIDDEN")) {
            long id = seed(author.id(), "QUESTION", state, "Body " + state, PUBLISHED);
            if (state.equals("APPROVED")) approved = id;
            else privateIds.add(id);
        }
        JsonNode guestList = ok(get(BASE));
        assertThat(guestList.path("totalElements").asLong()).isEqualTo(1);
        assertThat(guestList.path("items")).hasSize(1);
        assertThat(guestList.path("items").get(0).path("id").asLong()).isEqualTo(approved);
        assertThat(guestList.path("items").get(0).has("content")).isFalse();
        JsonNode guestDetail = ok(get(BASE + "/{id}", approved));
        assertThat(guestDetail.path("content").asText()).isEqualTo("Body APPROVED");
        assertPrivateFieldsAbsent(guestList);
        assertPrivateFieldsAbsent(guestDetail);
        assertThat(fieldNames(guestDetail)).containsExactlyInAnyOrder("id", "title", "category", "excerpt",
                "author", "publishedAt", "commentCount", "likeCount", "content");
        assertThat(fieldNames(guestDetail.path("author"))).containsExactlyInAnyOrder("id", "displayName");
        for (String role : List.of("LEARNER", "MODERATOR", "TEACHER", "CONTENT_EDITOR", "ACCOUNTANT", "ADMIN")) {
            String token = jwt.issueAccessToken(author.id(), Set.of(role), false, UUID.randomUUID());
            assertThat(ok(get(BASE).header("Authorization", "Bearer " + token))).isEqualTo(guestList);
            assertThat(ok(get(BASE + "/{id}", approved).header("Authorization", "Bearer " + token)))
                    .isEqualTo(guestDetail);
        }
        JsonNode missing = notFound(Long.toString(Long.MAX_VALUE));
        for (Long id : privateIds) assertThat(notFound(id.toString())).isEqualTo(missing);
        for (String invalid : List.of("not-a-number", "9223372036854775808", "-9223372036854775809")) {
            assertThat(notFound(invalid)).isEqualTo(missing);
        }
    }

    @Test
    void FR012_filtersAllFiveCategoriesAndOrdersPublishedTimeThenDescendingId() throws Exception {
        long author = register().id();
        List<Long> expected = new ArrayList<>();
        for (PostCategory category : PostCategory.values()) {
            expected.add(seed(author, category.name(), "APPROVED", "Category body", PUBLISHED));
        }
        long newest = seed(author, "QUESTION", "APPROVED", "Newest", PUBLISHED.plusSeconds(1));
        List<Long> descending = new ArrayList<>();
        descending.add(newest);
        for (int index = expected.size() - 1; index >= 0; index--) descending.add(expected.get(index));
        JsonNode all = ok(get(BASE));
        assertThat(ids(all)).containsExactlyElementsOf(descending);
        for (PostCategory category : PostCategory.values()) {
            JsonNode page = ok(get(BASE).param("category", category.name()));
            for (JsonNode item : page.path("items")) assertThat(item.path("category").asText()).isEqualTo(category.name());
            assertThat(page.path("totalElements").asLong()).isEqualTo(category == PostCategory.QUESTION ? 2 : 1);
        }
        JsonNode secondPage = ok(get(BASE).param("page", "1").param("size", "2"));
        assertThat(ids(secondPage)).containsExactlyElementsOf(descending.subList(2, 4));
        assertThat(secondPage.path("totalElements").asLong()).isEqualTo(6);
    }

    @Test
    void FR012_paginationRejectsInvalidValuesAndDefaultsEmptyParametersWithoutOverflow() throws Exception {
        long author = register().id();
        seed(author, "QUESTION", "APPROVED", "Body", PUBLISHED);
        JsonNode defaultPage = ok(get(BASE));
        assertThat(defaultPage.path("page").asInt()).isZero();
        assertThat(defaultPage.path("size").asInt()).isEqualTo(20);
        assertThat(ok(get(BASE).param("category", "").param("page", "").param("size", "")))
                .isEqualTo(defaultPage);
        for (String[] invalid : List.of(new String[] {"size", "51"}, new String[] {"size", "0"},
                new String[] {"size", "-1"}, new String[] {"page", "-1"}, new String[] {"page", "abc"},
                new String[] {"size", "abc"}, new String[] {"page", "2147483648"},
                new String[] {"size", "2147483648"}, new String[] {"page", "-2147483649"},
                new String[] {"size", "-2147483649"}, new String[] {"category", "NEWS"},
                new String[] {"category", "question"})) {
            MvcResult response = mvc.perform(get(BASE).param(invalid[0], invalid[1]))
                    .andExpect(status().isUnprocessableEntity()).andReturn();
            JsonNode error = mapper.readTree(response.getResponse().getContentAsString());
            assertThat(error.path("error_code").asText()).isEqualTo("VALIDATION_FAILED");
            assertThat(error.path("request_id").asText()).isNotBlank();
            assertThat(error.has("details")).isTrue();
            assertThat(error.toString()).doesNotContain("Exception", "stackTrace", "java.lang", "org.springframework");
        }
        for (String pageNumber : List.of("99", Integer.toString(Integer.MAX_VALUE))) {
            JsonNode page = ok(get(BASE).param("page", pageNumber).param("size", "50"));
            assertThat(page.path("items")).isEmpty();
            assertThat(page.path("totalElements").asLong()).isEqualTo(1);
            assertThat(page.path("page").asInt()).isEqualTo(Integer.parseInt(pageNumber));
        }
    }

    @Test
    void FR014_emptyDatasetReturnsStablePageAndZeroCountsRequireNoRelatedRecords() throws Exception {
        JsonNode empty = ok(get(BASE));
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("totalElements").asLong()).isZero();
        long id = seed(register().id(), "QUESTION", "APPROVED", "  a\u0301😀汉 <img src=x onerror=alert(1)> **đậm**  ", PUBLISHED);
        JsonNode detail = ok(get(BASE + "/{id}", id));
        assertThat(detail.path("content").asText()).isEqualTo("  a\u0301😀汉 <img src=x onerror=alert(1)> **đậm**  ");
        assertThat(detail.path("excerpt")).isEqualTo(detail.path("content"));
        assertThat(detail.path("commentCount").asLong()).isZero();
        assertThat(detail.path("likeCount").asLong()).isZero();
    }

    @Test
    void FR011_authorEditingApprovedPostRemovesItFromPublicReadsAndPreservesPublishedTime() throws Exception {
        Account author = register();
        verifyEmail(author);
        String token = mobileLogin(author.email());
        long id = seed(author.id(), "QUESTION", "APPROVED", "Published body", PUBLISHED);
        assertThat(ok(get(BASE)).path("items")).hasSize(1);
        mvc.perform(put("/api/community/posts/{id}", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                                "title", "Revised title", "content", "Revised body", "category", "QUESTION", "submit", true))))
                .andExpect(status().isOk());
        assertThat(ok(get(BASE)).path("items")).isEmpty();
        notFound(Long.toString(id));
        Map<String, Object> row = jdbc.queryForMap("SELECT status,published_at FROM community.posts WHERE id=?", id);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Timestamp) row.get("published_at")).toInstant()).isEqualTo(PUBLISHED);
    }

    @Test
    void FR012_runtimeSwaggerDocumentsExactPublicOperationsResponsesAndErrorReferences() throws Exception {
        JsonNode document = ok(get("/v3/api-docs"));
        JsonNode list = document.path("paths").path(BASE).path("get");
        JsonNode detail = document.path("paths").path(BASE + "/{id}").path("get");
        assertThat(list.path("operationId").asText()).isEqualTo("listPublishedPosts");
        assertThat(detail.path("operationId").asText()).isEqualTo("getPublishedPost");
        assertThat(fieldNames(list.path("responses"))).containsExactlyInAnyOrder("200", "422");
        assertThat(fieldNames(detail.path("responses"))).containsExactlyInAnyOrder("200", "404");
        for (JsonNode error : List.of(list.path("responses").path("422"), detail.path("responses").path("404"))) {
            assertThat(error.toString()).contains("#/components/schemas/ErrorResponse");
        }
        assertThat(list.path("responses").path("200").toString()).contains("#/components/schemas/PostSummaryPage");
        assertThat(detail.path("responses").path("200").toString()).contains("#/components/schemas/PostDetail");
        for (JsonNode parameter : list.path("parameters")) {
            JsonNode schema = parameter.path("schema");
            if (parameter.path("name").asText().equals("page")) {
                assertThat(schema.path("minimum").asInt()).isZero();
                assertThat(schema.path("default").asInt()).isZero();
                assertThat(schema.has("minimum")).isTrue();
                assertThat(schema.has("default")).isTrue();
            } else if (parameter.path("name").asText().equals("size")) {
                assertThat(schema.path("minimum").asInt()).isEqualTo(1);
                assertThat(schema.path("maximum").asInt()).isEqualTo(50);
                assertThat(schema.path("default").asInt()).isEqualTo(20);
            }
        }
    }

    private Account register() throws Exception {
        String email = "public-post-" + UUID.randomUUID() + "@example.test";
        JsonNode result = mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "confirmPassword", PASSWORD, "displayName", "Public Post Reader", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(result.path("userId").asLong()).isPositive();
        return new Account(result.path("userId").asLong(), email);
    }

    private long seed(long authorId, String category, String state, String content, Instant published) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,review_reason,"
                        + "published_at) VALUES (?,?,'Public fixture',?,?,?,?) RETURNING id", Long.class,
                authorId, category, content, state, state.equals("REJECTED") ? "Revise synthetic post" : null,
                Timestamp.from(published));
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());
    }

    private JsonNode notFound(String id) throws Exception {
        JsonNode error = mapper.readTree(mvc.perform(get(BASE + "/{id}", id)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString());
        assertThat(fieldNames(error)).containsExactlyInAnyOrder("error_code", "message", "request_id");
        assertThat(error.path("error_code").asText()).isEqualTo("POST_NOT_FOUND");
        assertThat(error.path("request_id").asText()).isNotBlank();
        ((ObjectNode) error).remove("request_id");
        return error;
    }

    private List<Long> ids(JsonNode page) {
        List<Long> result = new ArrayList<>();
        page.path("items").forEach(item -> result.add(item.path("id").asLong()));
        return result;
    }

    private List<String> fieldNames(JsonNode object) {
        List<String> result = new ArrayList<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }

    private void assertPrivateFieldsAbsent(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                assertThat(PRIVATE_FIELDS).doesNotContain(field.getKey());
                assertPrivateFieldsAbsent(field.getValue());
            });
        } else if (node.isArray()) node.forEach(this::assertPrivateFieldsAbsent);
    }

    private void verifyEmail(Account account) throws Exception {
        JsonNode inbox = mailpit("/api/v1/messages").path("messages");
        JsonNode verification = null;
        for (JsonNode message : inbox) {
            if (message.toString().contains(account.email())) {
                verification = mailpit("/api/v1/message/" + message.path("ID").asText());
                break;
            }
        }
        assertThat(verification).isNotNull();
        var matcher = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)")
                .matcher(verification.path("Text").asText());
        assertThat(matcher.find()).isTrue();
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("token", matcher.group(1)))))
                .andExpect(status().isOk());
    }

    private JsonNode mailpit(String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private String mobileLogin(String email) throws Exception {
        JsonNode login = ok(post("/api/auth/login").header("X-Client-Type", "MOBILE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))));
        assertThat(login.path("accessToken").asText()).isNotBlank();
        return login.path("accessToken").asText();
    }

    private record Account(long id, String email) { }
}
