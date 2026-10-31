package com.cnhsk.community;

import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.common.CaseStatus;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.ReactionKind;
import com.cnhsk.community.common.TargetType;
import com.cnhsk.community.moderation.ModerationCase;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.reaction.Reaction;
import com.cnhsk.community.reaction.ReactionId;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class CommunitySchemaConstraintsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;

    private record ColumnSpec(String table, String column, String type, boolean nullable, Integer length) { }

    static Stream<ColumnSpec> columns() {
        return Stream.of(
                col("posts", "id", "int8", false),
                col("posts", "author_id", "int8", false),
                varchar("posts", "category", 30), varchar("posts", "title", 300),
                col("posts", "content", "text", false), varchar("posts", "status", 20),
                col("posts", "reviewed_by", "int8", true), col("posts", "reviewed_at", "timestamptz", true),
                col("posts", "review_reason", "text", true), col("posts", "comments_locked", "bool", false),
                col("posts", "published_at", "timestamptz", true),
                col("posts", "created_at", "timestamptz", false), col("posts", "updated_at", "timestamptz", false),
                col("comments", "id", "int8", false), col("comments", "post_id", "int8", false),
                col("comments", "parent_id", "int8", true), col("comments", "author_id", "int8", false),
                col("comments", "content", "text", false), col("comments", "depth", "int2", false),
                varchar("comments", "status", 20), col("comments", "moderated_by", "int8", true),
                col("comments", "moderation_reason", "text", true),
                col("comments", "created_at", "timestamptz", false), col("comments", "updated_at", "timestamptz", false),
                col("reactions", "user_id", "int8", false), varchar("reactions", "target_type", 20),
                col("reactions", "target_id", "int8", false), varchar("reactions", "kind", 10),
                col("reactions", "created_at", "timestamptz", false),
                col("moderation_cases", "id", "int8", false), varchar("moderation_cases", "target_type", 20),
                col("moderation_cases", "target_id", "int8", false), col("moderation_cases", "report_count", "int4", false),
                col("moderation_cases", "reasons", "jsonb", false), varchar("moderation_cases", "status", 20),
                col("moderation_cases", "auto_hidden", "bool", false), col("moderation_cases", "handled_by", "int8", true),
                col("moderation_cases", "handled_at", "timestamptz", true), col("moderation_cases", "decision_note", "text", true),
                col("moderation_cases", "created_at", "timestamptz", false));
    }

    private static ColumnSpec col(String table, String name, String type, boolean nullable) {
        return new ColumnSpec(table, name, type, nullable, null);
    }

    private static ColumnSpec varchar(String table, String name, int length) {
        return new ColumnSpec(table, name, "varchar", false, length);
    }

    @ParameterizedTest(name = "AC05_column {0}")
    @MethodSource("columns")
    void AC05_eachColumnMatchesFlywayMetadata(ColumnSpec expected) {
        Map<String, Object> actual = jdbc.queryForMap("""
                SELECT udt_name, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'community' AND table_name = ? AND column_name = ?
                """, expected.table(), expected.column());
        assertThat(actual.get("udt_name")).isEqualTo(expected.type());
        assertThat(actual.get("is_nullable")).isEqualTo(expected.nullable() ? "YES" : "NO");
        assertThat(actual.get("character_maximum_length")).isEqualTo(expected.length());
    }

    @Test
    void AC05_fourTablesHaveExactlyFortyColumnsAndThreeIdentitySequences() {
        assertThat(jdbc.queryForList("""
                SELECT table_name, count(*) AS columns FROM information_schema.columns
                WHERE table_schema = 'community'
                  AND table_name IN ('posts','comments','reactions','moderation_cases')
                GROUP BY table_name ORDER BY table_name
                """)).containsExactly(
                Map.of("table_name", "comments", "columns", 11L),
                Map.of("table_name", "moderation_cases", "columns", 11L),
                Map.of("table_name", "posts", "columns", 13L),
                Map.of("table_name", "reactions", "columns", 5L));
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.columns
                WHERE table_schema = 'community' AND column_name = 'id'
                  AND table_name IN ('posts','comments','moderation_cases')
                  AND column_default LIKE 'nextval(%' ORDER BY table_name
                """, String.class)).containsExactly("comments", "moderation_cases", "posts");
    }

    @Test
    void AC05_databaseDefaultsApplyWhenInsertOmitsDefaultedColumns() throws Exception {
        long user = registeredUser();
        long postId = insertPost(user);
        long commentId = jdbc.queryForObject("""
                INSERT INTO community.comments(post_id,author_id,content) VALUES (?,?,?) RETURNING id
                """, Long.class, postId, user, "Bình luận tự tạo");
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,'POST',?,'LIKE')", user, postId);
        long caseId = jdbc.queryForObject("""
                INSERT INTO community.moderation_cases(target_type,target_id) VALUES ('POST',?) RETURNING id
                """, Long.class, postId);
        Instant transactionTime = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", (rs, row) -> rs.getTimestamp(1).toInstant());
        Map<String, Object> post = jdbc.queryForMap("SELECT * FROM community.posts WHERE id=?", postId);
        assertThat(post.get("status")).isEqualTo("PENDING");
        assertThat(post.get("comments_locked")).isEqualTo(false);
        assertThat(post.get("reviewed_by")).isNull();
        assertThat(post.get("reviewed_at")).isNull();
        assertThat(post.get("review_reason")).isNull();
        assertThat(post.get("published_at")).isNull();
        Map<String, Object> comment = jdbc.queryForMap("SELECT * FROM community.comments WHERE id=?", commentId);
        assertThat(comment.get("depth")).isEqualTo(0);
        assertThat(comment.get("status")).isEqualTo("VISIBLE");
        assertThat(comment.get("parent_id")).isNull();
        assertThat(comment.get("moderated_by")).isNull();
        assertThat(comment.get("moderation_reason")).isNull();
        Map<String, Object> moderationCase = jdbc.queryForMap("SELECT * FROM community.moderation_cases WHERE id=?", caseId);
        assertThat(moderationCase.get("report_count")).isEqualTo(1);
        assertThat(moderationCase.get("status")).isEqualTo("PENDING");
        assertThat(moderationCase.get("auto_hidden")).isEqualTo(false);
        assertThat(moderationCase.get("reasons").toString()).isEqualTo("[]");
        assertThat(moderationCase.get("handled_by")).isNull();
        assertThat(moderationCase.get("handled_at")).isNull();
        assertThat(moderationCase.get("decision_note")).isNull();
        assertThat(readInstant("SELECT created_at FROM community.posts WHERE id=?", postId)).isEqualTo(transactionTime);
        assertThat(readInstant("SELECT updated_at FROM community.posts WHERE id=?", postId)).isEqualTo(transactionTime);
        assertThat(readInstant("SELECT created_at FROM community.comments WHERE id=?", commentId)).isEqualTo(transactionTime);
        assertThat(readInstant("SELECT updated_at FROM community.comments WHERE id=?", commentId)).isEqualTo(transactionTime);
        assertThat(readInstant("SELECT created_at FROM community.reactions WHERE user_id=? AND target_type='POST' AND target_id=? AND kind='LIKE'", user, postId)).isEqualTo(transactionTime);
        assertThat(readInstant("SELECT created_at FROM community.moderation_cases WHERE id=?", caseId)).isEqualTo(transactionTime);
        entityManager.clear();
        Post mappedPost = entityManager.find(Post.class, postId);
        assertThat(mappedPost.getStatus()).isEqualTo(PostStatus.PENDING);
        assertThat(mappedPost.isCommentsLocked()).isFalse();
        assertThat(mappedPost.getCreatedAt()).isEqualTo(transactionTime);
        assertThat(mappedPost.getUpdatedAt()).isEqualTo(transactionTime);
        Comment mappedComment = entityManager.find(Comment.class, commentId);
        assertThat(mappedComment.getDepth()).isZero();
        assertThat(mappedComment.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(mappedComment.getCreatedAt()).isEqualTo(transactionTime);
        assertThat(mappedComment.getUpdatedAt()).isEqualTo(transactionTime);
        Reaction mappedReaction = entityManager.find(Reaction.class, new ReactionId(user, TargetType.POST, postId, ReactionKind.LIKE));
        assertThat(mappedReaction.getCreatedAt()).isEqualTo(transactionTime);
        ModerationCase mappedCase = entityManager.find(ModerationCase.class, caseId);
        assertThat(mappedCase.getReportCount()).isEqualTo(1);
        assertThat(mappedCase.getReasons()).isEmpty();
        assertThat(mappedCase.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(mappedCase.isAutoHidden()).isFalse();
        assertThat(mappedCase.getCreatedAt()).isEqualTo(transactionTime);
    }

    static Stream<Arguments> foreignKeys() {
        return Stream.of(
                Arguments.of("posts", "author_id", "auth.users", "id"),
                Arguments.of("posts", "reviewed_by", "auth.users", "id"),
                Arguments.of("comments", "post_id", "community.posts", "id"),
                Arguments.of("comments", "parent_id", "community.comments", "id"),
                Arguments.of("comments", "author_id", "auth.users", "id"),
                Arguments.of("comments", "moderated_by", "auth.users", "id"),
                Arguments.of("reactions", "user_id", "auth.users", "id"),
                Arguments.of("moderation_cases", "handled_by", "auth.users", "id"));
    }

    @ParameterizedTest
    @MethodSource("foreignKeys")
    void AC05_foreignKeysHaveScalarTargetsAndNoCascade(String table, String column, String target, String targetColumn) {
        Map<String, Object> fk = jdbc.queryForMap("""
                SELECT c.confrelid::regclass::text AS target, a2.attname AS target_column,
                       c.confdeltype::text AS delete_action, c.confupdtype::text AS update_action
                FROM pg_constraint c
                JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
                JOIN pg_attribute a2 ON a2.attrelid=c.confrelid AND a2.attnum=c.confkey[1]
                WHERE c.contype='f' AND c.conrelid=to_regclass(?) AND a.attname=?
                """, "community." + table, column);
        assertThat(fk).containsEntry("target", target).containsEntry("target_column", targetColumn)
                .containsEntry("delete_action", "a").containsEntry("update_action", "a");
    }

    @Test
    void AC05_namedChecksRemainOwnedByFlyway() {
        Map<String, String> checks = jdbc.query("""
                SELECT conname,pg_get_constraintdef(oid) FROM pg_constraint
                WHERE connamespace='community'::regnamespace AND contype='c'
                """, rs -> {
            Map<String, String> result = new java.util.HashMap<>();
            while (rs.next()) result.put(rs.getString(1), rs.getString(2));
            return result;
        });
        assertThat(checks.get("ck_post_reject")).contains("REJECTED", "review_reason IS NOT NULL");
        assertThat(checks.get("ck_post_self")).contains("reviewed_by IS NULL", "reviewed_by <> author_id");
        assertThat(checks.get("ck_post_pub")).contains("APPROVED", "published_at IS NOT NULL");
        assertThat(checks.get("ck_cmt_self")).contains("parent_id IS NULL", "parent_id <> id");
        assertThat(checks.get("ck_cmt_mod")).contains("VISIBLE", "moderation_reason IS NOT NULL");
        assertThat(checks.get("ck_mod_handled")).contains("PENDING", "handled_by IS NOT NULL", "decision_note IS NOT NULL");
    }

    @Test
    void AC05_indexesPreserveOrderAndPartialPredicates() {
        Map<String, String> indexes = jdbc.query("""
                SELECT indexname,indexdef FROM pg_indexes WHERE schemaname='community'
                AND tablename IN ('posts','comments','reactions','moderation_cases')
                """, rs -> {
            Map<String, String> result = new java.util.HashMap<>();
            while (rs.next()) result.put(rs.getString(1), rs.getString(2));
            return result;
        });
        assertThat(indexes.get("ix_post_feed")).contains("(status, published_at DESC)", "WHERE", "APPROVED");
        assertThat(indexes.get("ix_post_author")).contains("(author_id, created_at DESC)").doesNotContain("WHERE");
        assertThat(indexes.get("ix_post_queue")).contains("(status, created_at)", "WHERE", "PENDING");
        assertThat(indexes.get("ix_cmt_post")).contains("(post_id, created_at)", "WHERE", "<>", "DELETED");
        assertThat(indexes.get("ix_cmt_parent")).contains("(parent_id)").doesNotContain("WHERE");
        assertThat(indexes.get("ix_react_target")).contains("(target_type, target_id, kind)").doesNotContain("WHERE");
        assertThat(indexes.get("uq_mod_target")).contains("UNIQUE INDEX", "(target_type, target_id)", "WHERE", "PENDING");
        assertThat(indexes.get("ix_mod_queue")).contains("(status, report_count DESC)", "WHERE", "PENDING");
        assertThat(indexes.get("reactions_pkey")).contains("UNIQUE INDEX", "(user_id, target_type, target_id, kind)");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE connamespace='community'::regnamespace AND contype='u'
                  AND conrelid='community.moderation_cases'::regclass
                """, Integer.class)).isZero();
    }

    static Stream<Arguments> invalidValues() {
        return Stream.of(
                Arguments.of("posts", "category", "UNKNOWN", "23514"),
                Arguments.of("posts", "status", "UNKNOWN", "23514"),
                Arguments.of("posts", "title", "x".repeat(301), "22001"),
                Arguments.of("posts", "status", "REJECTED", "23514"),
                Arguments.of("posts", "status", "APPROVED", "23514"),
                Arguments.of("posts", "title", null, "23502"),
                Arguments.of("comments", "status", "UNKNOWN", "23514"),
                Arguments.of("comments", "status", "HIDDEN", "23514"),
                Arguments.of("comments", "status", "DELETED", "23514"),
                Arguments.of("comments", "depth", -1, "23514"),
                Arguments.of("comments", "depth", 4, "23514"),
                Arguments.of("reactions", "target_type", "UNKNOWN", "23514"),
                Arguments.of("reactions", "kind", "UNKNOWN", "23514"),
                Arguments.of("moderation_cases", "target_type", "USER", "23514"),
                Arguments.of("moderation_cases", "target_type", "UNKNOWN", "23514"),
                Arguments.of("moderation_cases", "status", "UNKNOWN", "23514"),
                Arguments.of("moderation_cases", "status", "ACTIONED", "23514"),
                Arguments.of("moderation_cases", "status", "REJECTED", "23514"),
                Arguments.of("moderation_cases", "report_count", 0, "23514"),
                Arguments.of("moderation_cases", "report_count", -1, "23514"),
                Arguments.of("moderation_cases", "reasons", null, "23502"));
    }

    @ParameterizedTest(name = "AC06_{0}.{1} rejects invalid value {2}")
    @MethodSource("invalidValues")
    void AC06_checksRejectInvalidDomainsInIndependentTransactions(String table, String column, Object invalid, String sqlState) throws Exception {
        long user = registeredUser();
        long post = insertPost(user);
        long comment = jdbc.queryForObject("INSERT INTO community.comments(post_id,author_id,content) VALUES (?,?,'Test') RETURNING id", Long.class, post, user);
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,'POST',?,'LIKE')", user, post);
        long moderationCase = jdbc.queryForObject("INSERT INTO community.moderation_cases(target_type,target_id) VALUES ('POST',?) RETURNING id", Long.class, post);
        // Identifiers come only from the fixed method source above, never from a request.
        String sql = "UPDATE community." + table + " SET " + column + "=? WHERE " + (table.equals("reactions") ? "user_id" : "id") + "=?";
        long key = switch (table) {
            case "posts" -> post;
            case "comments" -> comment;
            case "reactions" -> user;
            case "moderation_cases" -> moderationCase;
            default -> throw new IllegalArgumentException("Unexpected test table");
        };
        assertSqlState(sqlState, () -> jdbc.update(sql, invalid, key));
        // The failed transaction ends with this invocation; no queries follow an integrity failure.
    }

    @ParameterizedTest
    @MethodSource("foreignKeys")
    void AC05_foreignKeyViolationsAreEnforced(String table, String column, String target, String targetColumn) throws Exception {
        long user = registeredUser();
        long post = insertPost(user);
        long comment = jdbc.queryForObject("INSERT INTO community.comments(post_id,author_id,content) VALUES (?,?,'Test') RETURNING id", Long.class, post, user);
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,'POST',?,'LIKE')", user, post);
        long moderationCase = jdbc.queryForObject("INSERT INTO community.moderation_cases(target_type,target_id) VALUES ('POST',?) RETURNING id", Long.class, post);
        String sql = "UPDATE community." + table + " SET " + column + "=? WHERE " + (table.equals("reactions") ? "user_id" : "id") + "=?";
        long id = switch (table) {
            case "posts" -> post;
            case "comments" -> comment;
            case "reactions" -> user;
            case "moderation_cases" -> moderationCase;
            default -> throw new IllegalArgumentException("Unexpected test table");
        };
        assertSqlState("23503", () -> jdbc.update(sql, Long.MAX_VALUE, id));
    }

    @Test
    void AC05_postCannotReviewItself() throws Exception {
        long user = registeredUser();
        long post = insertPost(user);
        assertSqlState("23514", () -> jdbc.update("UPDATE community.posts SET reviewed_by=? WHERE id=?", user, post));
    }

    @Test
    void AC05_commentCannotReferenceItself() throws Exception {
        long user = registeredUser();
        long post = insertPost(user);
        long comment = jdbc.queryForObject("INSERT INTO community.comments(post_id,author_id,content) VALUES (?,?,'Test') RETURNING id", Long.class, post, user);
        assertSqlState("23514", () -> jdbc.update("UPDATE community.comments SET parent_id=? WHERE id=?", comment, comment));
    }

    @Test
    void AC05_reactionCompositePrimaryKeyRejectsAnIdenticalInsert() throws Exception {
        long user = registeredUser();
        long post = insertPost(user);
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,'POST',?,'LIKE')", user, post);
        assertSqlState("23505", () -> jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind) VALUES (?,'POST',?,'LIKE')", user, post));
    }

    @Test
    void AC05_partialUniqueIndexAllowsHistoryButOnlyOnePendingCase() throws Exception {
        long handler = registeredUser();
        long target = insertPost(handler);
        jdbc.update("INSERT INTO community.moderation_cases(target_type,target_id,status,handled_by,decision_note) VALUES ('POST',?,'ACTIONED',?,'Handled')", target, handler);
        jdbc.update("INSERT INTO community.moderation_cases(target_type,target_id,status,handled_by,decision_note) VALUES ('POST',?,'REJECTED',?,'Rejected')", target, handler);
        jdbc.update("INSERT INTO community.moderation_cases(target_type,target_id) VALUES ('POST',?)", target);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM community.moderation_cases WHERE target_type='POST' AND target_id=?", Integer.class, target)).isEqualTo(3);
        assertSqlState("23505", () -> jdbc.update("INSERT INTO community.moderation_cases(target_type,target_id) VALUES ('POST',?)", target));
    }

    @Test
    void AC05_closedCaseRequiresBothHandlerAndDecisionNote() throws Exception {
        long handler = registeredUser();
        long target = insertPost(handler);
        long id = jdbc.queryForObject("INSERT INTO community.moderation_cases(target_type,target_id,status,handled_by,decision_note) VALUES ('POST',?,'ACTIONED',?,'Handled') RETURNING id", Long.class, target, handler);
        assertThat(jdbc.queryForObject("SELECT handled_at IS NULL FROM community.moderation_cases WHERE id=?", Boolean.class, id)).isTrue();
        assertSqlState("23514", () -> jdbc.update("UPDATE community.moderation_cases SET decision_note=NULL WHERE id=?", id));
    }

    @Test
    void AC05_closedCaseWithoutHandlerFailsEvenWithNote() throws Exception {
        assertSqlState("23514", () -> jdbc.update("INSERT INTO community.moderation_cases(target_type,target_id,status,decision_note) VALUES ('POST',1,'ACTIONED','A note')"));
    }

    private Instant readInstant(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, (rs, row) -> rs.getTimestamp(1).toInstant(), arguments);
    }

    private long insertPost(long author) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content) VALUES (?,'QUESTION','Test title','Test content') RETURNING id", Long.class, author);
    }

    private long registeredUser() throws Exception {
        String password = UUID.randomUUID() + "Aa1";
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "community-schema-" + UUID.randomUUID() + "@example.test",
                "password", password, "confirmPassword", password,
                "displayName", "Người dùng kiểm thử", "acceptTerms", true));
        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).path("userId").asLong();
        assertThat(id).isPositive();
        return id;
    }

    private void assertSqlState(String expected, Runnable statement) {
        Throwable failure = catchThrowable(statement::run);
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        assertThat(cause).isInstanceOf(SQLException.class);
        assertThat(((SQLException) cause).getSQLState()).isEqualTo(expected);
    }
}
