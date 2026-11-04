package com.cnhsk.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class PublicCommentControllerIntegrationTest extends AbstractIntegrationTest {
    static final String BASE = "/api/public/community/posts";
    static final Instant TIME = Instant.parse("2026-11-04T01:00:00Z");
    static final Set<String> COMMENT_KEYS = Set.of("id", "postId", "parentId", "depth", "status", "content",
            "author", "createdAt", "editedAt");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    private Fixtures fixtures;

    @BeforeEach
    void fixtures() { fixtures = new Fixtures(mvc, mapper, jdbc); }

    @AfterEach
    void cleanup() { fixtures.cleanup(); }

    @Test
    void FR018_deletedParentRetainsTwoRepliesWithExactlyNineKeysAndThreeNulls() throws Exception {
        long author = fixtures.register();
        long post = fixtures.post(author, PostStatus.APPROVED);
        long parent = fixtures.comment(post, null, author, 0, CommentStatus.DELETED, "private parent", TIME);
        long first = fixtures.comment(post, parent, author, 1, CommentStatus.VISIBLE,
                "<img src=x onerror=alert(1)> **đậm**", TIME.plusSeconds(1));
        long second = fixtures.comment(post, parent, author, 1, CommentStatus.VISIBLE, "second", TIME.plusSeconds(2));
        jdbc.update("update community.comments set updated_at=? where id=?", Timestamp.from(TIME.plusSeconds(10)), parent);
        jdbc.update("update community.comments set updated_at=? where id=?", Timestamp.from(TIME.plusSeconds(20)), first);
        JsonNode body = read(post);
        assertThat(ids(body)).containsExactly(parent, first, second);
        assertPlaceholder(body.get(0), parent, post, null, 0, "DELETED");
        assertThat(body.get(1).path("content").asText()).isEqualTo("<img src=x onerror=alert(1)> **đậm**");
        assertThat(body.get(1).path("editedAt").asText()).isEqualTo(TIME.plusSeconds(20).toString());
        assertThat(body.get(2).path("editedAt").isNull()).isTrue();
        for (JsonNode node : body) {
            assertThat(keys(node)).containsExactlyInAnyOrderElementsOf(COMMENT_KEYS);
            if (node.path("status").asText().equals("VISIBLE")) {
                assertThat(keys(node.path("author"))).containsExactlyInAnyOrder("id", "displayName");
                assertThat(node.path("author").path("id").asLong()).isEqualTo(author);
                assertThat(node.path("parentId").asLong()).isEqualTo(parent);
                assertThat(node.path("depth").asInt()).isEqualTo(1);
            }
        }
        assertNoPrivateFields(body);
        assertThat(jdbc.queryForObject("select content from community.comments where id=?", String.class, parent))
                .isEqualTo("private parent");
    }

    @Test
    void FR018_hiddenAndDeletedLeavesDisappearWhileMultipleAncestorsSurvive() throws Exception {
        long author = fixtures.register();
        long post = fixtures.post(author, PostStatus.APPROVED);
        long root = fixtures.comment(post, null, author, 0, CommentStatus.DELETED, "secret root", TIME);
        long child = fixtures.comment(post, root, author, 1, CommentStatus.HIDDEN, "secret child", TIME);
        long leaf = fixtures.comment(post, child, author, 2, CommentStatus.VISIBLE, "visible", TIME);
        fixtures.comment(post, root, author, 1, CommentStatus.HIDDEN, "hidden leaf secret", TIME);
        fixtures.comment(post, child, author, 2, CommentStatus.DELETED, "deleted leaf secret", TIME);
        JsonNode body = read(post);
        assertThat(ids(body)).containsExactly(root, child, leaf);
        assertPlaceholder(body.get(0), root, post, null, 0, "DELETED");
        assertPlaceholder(body.get(1), child, post, root, 1, "HIDDEN");
        assertThat(body.toString()).doesNotContain("secret");
        assertThat(body.get(2).path("depth").asInt()).isEqualTo(2);
    }

    @Test
    void FR018_flatResponseKeepsGlobalCreatedAtThenIdOrderAcrossRootsAndReplies() throws Exception {
        long author = fixtures.register();
        long post = fixtures.post(author, PostStatus.APPROVED);
        long first = fixtures.comment(post, null, author, 0, CommentStatus.VISIBLE, "first", TIME);
        long second = fixtures.comment(post, null, author, 0, CommentStatus.VISIBLE, "second root", TIME);
        long reply = fixtures.comment(post, first, author, 1, CommentStatus.VISIBLE, "reply", TIME);
        long earlier = fixtures.comment(post, null, author, 0, CommentStatus.VISIBLE, "earlier", TIME.minusSeconds(1));
        assertThat(ids(read(post))).containsExactly(earlier, first, second, reply);
    }

    @Test
    void FR018_missingAndEveryNonpublicPostHaveTheSameSafeNotFoundEnvelope() throws Exception {
        long author = fixtures.register();
        JsonNode expected = error(Long.toString(Long.MAX_VALUE));
        for (PostStatus state : List.of(PostStatus.DRAFT, PostStatus.PENDING, PostStatus.REJECTED, PostStatus.HIDDEN)) {
            long post = fixtures.post(author, state);
            fixtures.comment(post, null, author, 0, CommentStatus.VISIBLE, "nonpublic content", TIME);
            assertThat(error(Long.toString(post))).isEqualTo(expected);
        }
        for (String id : List.of("abc", "9223372036854775808")) assertThat(error(id)).isEqualTo(expected);
    }

    @Test
    void FR001_guestAndAuthenticatedRolesReadTheSamePublicProjectionWithoutEmailVerification() throws Exception {
        long author = fixtures.register();
        long post = fixtures.post(author, PostStatus.APPROVED);
        fixtures.comment(post, null, author, 0, CommentStatus.VISIBLE, "public", TIME);
        JsonNode guest = read(post);
        for (RoleCode role : List.of(RoleCode.LEARNER, RoleCode.MODERATOR, RoleCode.ADMIN)) {
            String token = jwt.issueAccessToken(author, Set.of(role.name()), false, UUID.randomUUID());
            JsonNode authenticated = mapper.readTree(mvc.perform(get(path(post)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            assertThat(authenticated).isEqualTo(guest);
        }
    }

    @Test
    void FR018_emptyAndFullyPrunedCommentSetsReturnEmptyArrays() throws Exception {
        long author = fixtures.register();
        long post = fixtures.post(author, PostStatus.APPROVED);
        assertThat(read(post)).isEmpty();
        long root = fixtures.comment(post, null, author, 0, CommentStatus.HIDDEN, "hidden", TIME);
        fixtures.comment(post, root, author, 1, CommentStatus.DELETED, "deleted", TIME);
        assertThat(read(post)).isEmpty();
    }

    @Test
    void FR027_postListAndDetailCountOnlyVisibleCommentsIncludingVisibleDescendants() throws Exception {
        long author = fixtures.register();
        long post = fixtures.post(author, PostStatus.APPROVED);
        long root = fixtures.comment(post, null, author, 0, CommentStatus.DELETED, "deleted", TIME);
        fixtures.comment(post, root, author, 1, CommentStatus.VISIBLE, "one", TIME);
        fixtures.comment(post, root, author, 1, CommentStatus.VISIBLE, "two", TIME);
        fixtures.comment(post, null, author, 0, CommentStatus.HIDDEN, "hidden", TIME);
        JsonNode detail = mapper.readTree(mvc.perform(get(BASE + "/" + post)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(detail.path("commentCount").asLong()).isEqualTo(2);
        JsonNode list = mapper.readTree(mvc.perform(get(BASE).param("size", "50")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        List<JsonNode> matching = new ArrayList<>();
        list.path("items").forEach(node -> { if (node.path("id").asLong() == post) matching.add(node); });
        assertThat(matching).singleElement().satisfies(node -> assertThat(node.path("commentCount").asLong()).isEqualTo(2));
    }

    @Test
    void FR018_generatedOpenApiHasExactPublicArrayContractAndRequiredNullableFields() throws Exception {
        JsonNode doc = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        JsonNode operation = doc.path("paths").path(BASE + "/{id}/comments").path("get");
        assertThat(operation.path("operationId").asText()).isEqualTo("listPostComments");
        assertThat(keys(operation.path("responses"))).containsExactlyInAnyOrder("200", "404");
        JsonNode response = operation.path("responses").path("200").path("content").path("application/json").path("schema");
        assertThat(response.path("type").asText()).isEqualTo("array");
        assertThat(response.path("items").path("$ref").asText()).isEqualTo("#/components/schemas/Comment");
        assertThat(operation.path("responses").path("404").toString()).contains("#/components/schemas/ErrorResponse");
        JsonNode security = operation.has("security") ? operation.path("security") : doc.path("security");
        assertThat(security.isMissingNode() || security.isEmpty()).isTrue();
        JsonNode schema = doc.path("components").path("schemas").path("Comment");
        assertThat(keys(schema.path("properties"))).containsExactlyInAnyOrderElementsOf(COMMENT_KEYS);
        assertThat(schema.path("required")).extracting(JsonNode::asText).containsExactlyInAnyOrderElementsOf(COMMENT_KEYS);
        for (String field : List.of("parentId", "content", "author", "editedAt")) {
            JsonNode property = schema.path("properties").path(field);
            assertThat(property.path("nullable").asBoolean() || property.path("type").toString().contains("null")
                    || property.path("oneOf").toString().contains("null")).as("nullable %s", field).isTrue();
        }
    }

    private JsonNode read(long post) throws Exception {
        return mapper.readTree(mvc.perform(get(path(post))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode error(String id) throws Exception {
        ObjectNode body = (ObjectNode) mapper.readTree(mvc.perform(get(BASE + "/" + id + "/comments"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsByteArray());
        assertThat(body.path("error_code").asText()).isEqualTo("POST_NOT_FOUND");
        assertThat(body.path("message").asText()).isEqualTo("Không tìm thấy bài viết.");
        assertThat(body.path("request_id").asText()).isNotBlank();
        assertThat(keys(body)).containsExactlyInAnyOrder("error_code", "message", "request_id");
        body.remove("request_id");
        return body;
    }

    static String path(long post) { return BASE + "/" + post + "/comments"; }
    static List<Long> ids(JsonNode body) {
        List<Long> result = new ArrayList<>();
        body.forEach(node -> result.add(node.path("id").asLong()));
        return result;
    }
    static Set<String> keys(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
    private void assertPlaceholder(JsonNode node, long id, long post, Long parent, int depth, String state) {
        assertThat(keys(node)).containsExactlyInAnyOrderElementsOf(COMMENT_KEYS);
        assertThat(node.path("id").asLong()).isEqualTo(id);
        assertThat(node.path("postId").asLong()).isEqualTo(post);
        assertThat(node.path("status").asText()).isEqualTo(state);
        assertThat(node.path("depth").asInt()).isEqualTo(depth);
        assertThat(node.path("createdAt").asText()).isEqualTo(TIME.toString());
        if (parent == null) assertThat(node.path("parentId").isNull()).isTrue();
        else assertThat(node.path("parentId").asLong()).isEqualTo(parent);
        for (String field : List.of("content", "author", "editedAt")) assertThat(node.path(field).isNull()).isTrue();
    }
    private void assertNoPrivateFields(JsonNode node) {
        if (node.isObject()) node.fields().forEachRemaining(field -> {
            assertThat(Set.of("email", "password", "passwordHash", "roles", "moderatedBy", "moderationReason",
                    "authorId", "children", "updatedAt")).doesNotContain(field.getKey());
            assertNoPrivateFields(field.getValue());
        });
        else if (node.isArray()) node.forEach(this::assertNoPrivateFields);
    }

    static final class Fixtures {
        private final MockMvc mvc;
        private final ObjectMapper mapper;
        private final JdbcTemplate jdbc;
        private final List<Long> posts = new ArrayList<>();
        Fixtures(MockMvc mvc, ObjectMapper mapper, JdbcTemplate jdbc) {
            this.mvc = mvc; this.mapper = mapper; this.jdbc = jdbc;
        }
        long register() throws Exception {
            String passphrase = UUID.randomUUID() + "Aa1!";
            JsonNode body = mapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(Map.of("email", "comment-tree-" + UUID.randomUUID() + "@example.test",
                            "password", passphrase, "confirmPassword", passphrase, "displayName", "Synthetic tree author",
                            "acceptTerms", true)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
            return body.path("userId").asLong();
        }
        long post(long author, PostStatus status) {
            long id = jdbc.queryForObject("insert into community.posts(author_id,category,title,content,status,published_at,review_reason) "
                    + "values (?,'QUESTION','Synthetic tree post','Synthetic literal content',?,?,?) returning id", Long.class,
                    author, status.name(), status == PostStatus.APPROVED ? Timestamp.from(TIME) : null,
                    status == PostStatus.REJECTED ? "Synthetic reason" : null);
            posts.add(id);
            return id;
        }
        long comment(long post, Long parent, long author, int depth, CommentStatus status, String content, Instant created) {
            return jdbc.queryForObject("insert into community.comments(post_id,parent_id,author_id,depth,status,content,created_at,updated_at,moderation_reason) "
                    + "values (?,?,?,?,?,?,?,?,?) returning id", Long.class, post, parent, author, depth, status.name(), content,
                    Timestamp.from(created), Timestamp.from(created), status == CommentStatus.VISIBLE ? null : "Synthetic moderation reason");
        }
        void cleanup() {
            for (Long post : posts) {
                jdbc.update("delete from community.comments where post_id=?", post);
                jdbc.update("delete from community.posts where id=?", post);
            }
        }
    }
}
