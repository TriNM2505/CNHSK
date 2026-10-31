package com.cnhsk.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.community.comment.Comment;
import com.cnhsk.community.comment.CommentRepository;
import com.cnhsk.community.common.CaseStatus;
import com.cnhsk.community.common.CommentStatus;
import com.cnhsk.community.common.PostCategory;
import com.cnhsk.community.common.PostStatus;
import com.cnhsk.community.common.ReactionKind;
import com.cnhsk.community.common.ReportReason;
import com.cnhsk.community.common.TargetType;
import com.cnhsk.community.moderation.ModerationCase;
import com.cnhsk.community.moderation.ModerationCaseRepository;
import com.cnhsk.community.moderation.ReportEntry;
import com.cnhsk.community.post.Post;
import com.cnhsk.community.post.PostRepository;
import com.cnhsk.community.reaction.Reaction;
import com.cnhsk.community.reaction.ReactionId;
import com.cnhsk.community.reaction.ReactionRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class CommunityPersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-31T00:01:02.123456Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-31T07:08:09.654321Z");

    @Autowired private PostRepository posts;
    @Autowired private CommentRepository comments;
    @Autowired private ReactionRepository reactions;
    @Autowired private ModerationCaseRepository cases;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private Flyway flyway;

    @Test
    void AC05_fullFlywayHistoryAndHibernateValidationUsePostgres18() {
        assertThat(jdbc.queryForObject("SELECT current_setting('server_version_num')::integer / 10000",
                Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForList(
                "SELECT version FROM learning.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).containsSubsequence("1", "2", "2.1", "3", "4");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(entityManager.getEntityManagerFactory().getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate");
        assertThat(posts).isNotNull();
        assertThat(comments).isNotNull();
        assertThat(reactions).isNotNull();
        assertThat(cases).isNotNull();
    }

    @Test
    void AC05_postsRetainAllColumnsAndExplicitUpdatesPreserveCreationTime() throws Exception {
        long authorId = registeredUser();
        long reviewerId = registeredUser();
        Post post = posts.saveAndFlush(new Post(authorId, PostCategory.EXPERIENCE,
                "学".repeat(300), "Nội dung tự tạo\n你好", CREATED_AT));
        assertThat(post.getId()).isPositive();
        entityManager.clear();

        Post initial = posts.findById(post.getId()).orElseThrow();
        assertThat(initial.getAuthorId()).isEqualTo(authorId);
        assertThat(initial.getCategory()).isEqualTo(PostCategory.EXPERIENCE);
        assertThat(initial.getTitle()).isEqualTo("学".repeat(300));
        assertThat(initial.getContent()).isEqualTo("Nội dung tự tạo\n你好");
        assertThat(initial.getStatus()).isEqualTo(PostStatus.PENDING);
        assertThat(initial.getReviewedBy()).isNull();
        assertThat(initial.getReviewedAt()).isNull();
        assertThat(initial.getReviewReason()).isNull();
        assertThat(initial.isCommentsLocked()).isFalse();
        assertThat(initial.getPublishedAt()).isNull();
        assertThat(initial.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(initial.getUpdatedAt()).isEqualTo(CREATED_AT);

        initial.setCategory(PostCategory.CULTURE);
        initial.setTitle("Tiêu đề đã sửa 学习");
        initial.setContent("Nội dung đã sửa\n" + "中文".repeat(1000));
        initial.setStatus(PostStatus.APPROVED);
        initial.setReviewedBy(reviewerId);
        initial.setReviewedAt(UPDATED_AT.minusSeconds(1));
        initial.setReviewReason("Ghi chú kiểm duyệt tự tạo");
        initial.setCommentsLocked(true);
        initial.setPublishedAt(UPDATED_AT);
        initial.setUpdatedAt(UPDATED_AT);
        posts.saveAndFlush(initial);
        entityManager.clear();
        jdbc.execute("SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh'");

        Post loaded = posts.findById(post.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(post.getId());
        assertThat(loaded.getAuthorId()).isEqualTo(authorId);
        assertThat(loaded.getCategory()).isEqualTo(PostCategory.CULTURE);
        assertThat(loaded.getTitle()).isEqualTo("Tiêu đề đã sửa 学习");
        assertThat(loaded.getContent()).isEqualTo("Nội dung đã sửa\n" + "中文".repeat(1000));
        assertThat(loaded.getStatus()).isEqualTo(PostStatus.APPROVED);
        assertThat(loaded.getReviewedBy()).isEqualTo(reviewerId);
        assertThat(loaded.getReviewedAt()).isEqualTo(UPDATED_AT.minusSeconds(1));
        assertThat(loaded.getReviewReason()).isEqualTo("Ghi chú kiểm duyệt tự tạo");
        assertThat(loaded.isCommentsLocked()).isTrue();
        assertThat(loaded.getPublishedAt()).isEqualTo(UPDATED_AT);
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(loaded.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void AC06_postCategoriesAndStatusesRoundTripAsTheirSqlStrings() throws Exception {
        long authorId = registeredUser();
        for (PostCategory category : PostCategory.values()) {
            Post post = posts.saveAndFlush(new Post(authorId, category, "Danh mục " + category,
                    "Nội dung tự tạo", CREATED_AT));
            entityManager.clear();
            assertThat(posts.findById(post.getId()).orElseThrow().getCategory()).isEqualTo(category);
            assertThat(jdbc.queryForObject("SELECT category FROM community.posts WHERE id=?",
                    String.class, post.getId())).isEqualTo(category.name());
        }
        for (PostStatus status : PostStatus.values()) {
            Post post = new Post(authorId, PostCategory.QUESTION, "Trạng thái " + status,
                    "Nội dung tự tạo", CREATED_AT);
            post.setStatus(status);
            if (status == PostStatus.APPROVED) {
                post.setPublishedAt(UPDATED_AT);
            }
            if (status == PostStatus.REJECTED) {
                post.setReviewReason("Lý do từ chối tự tạo");
            }
            posts.saveAndFlush(post);
            entityManager.clear();
            assertThat(posts.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(status);
            assertThat(jdbc.queryForObject("SELECT status FROM community.posts WHERE id=?",
                    String.class, post.getId())).isEqualTo(status.name());
        }
    }

    @Test
    void AC05_commentsRetainRootChildrenDepthsAndNullableModerationMetadata() throws Exception {
        long authorId = registeredUser();
        long moderatorId = registeredUser();
        Post post = posts.saveAndFlush(new Post(authorId, PostCategory.STUDY_BUDDY,
                "Bài tự tạo", "Nội dung tự tạo", CREATED_AT));
        Comment root = comments.saveAndFlush(new Comment(post.getId(), null, authorId,
                "Bình luận gốc 你好", (short) 0, CREATED_AT));
        entityManager.clear();

        Comment loadedRoot = comments.findById(root.getId()).orElseThrow();
        assertThat(loadedRoot.getId()).isPositive();
        assertThat(loadedRoot.getPostId()).isEqualTo(post.getId());
        assertThat(loadedRoot.getParentId()).isNull();
        assertThat(loadedRoot.getAuthorId()).isEqualTo(authorId);
        assertThat(loadedRoot.getContent()).isEqualTo("Bình luận gốc 你好");
        assertThat(loadedRoot.getDepth()).isZero();
        assertThat(loadedRoot.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(loadedRoot.getModeratedBy()).isNull();
        assertThat(loadedRoot.getModerationReason()).isNull();
        assertThat(loadedRoot.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(loadedRoot.getUpdatedAt()).isEqualTo(CREATED_AT);

        loadedRoot.setContent("Bình luận gốc đã sửa\n学习");
        loadedRoot.setStatus(CommentStatus.HIDDEN);
        loadedRoot.setModeratedBy(moderatorId);
        loadedRoot.setModerationReason("Lý do ẩn bình luận gốc tự tạo");
        loadedRoot.setUpdatedAt(UPDATED_AT);
        comments.saveAndFlush(loadedRoot);
        entityManager.clear();

        Comment updatedRoot = comments.findById(root.getId()).orElseThrow();
        assertThat(updatedRoot.getId()).isEqualTo(root.getId());
        assertThat(updatedRoot.getPostId()).isEqualTo(post.getId());
        assertThat(updatedRoot.getParentId()).isNull();
        assertThat(updatedRoot.getAuthorId()).isEqualTo(authorId);
        assertThat(updatedRoot.getDepth()).isZero();
        assertThat(updatedRoot.getContent()).isEqualTo("Bình luận gốc đã sửa\n学习");
        assertThat(updatedRoot.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(updatedRoot.getModeratedBy()).isEqualTo(moderatorId);
        assertThat(updatedRoot.getModerationReason()).isEqualTo("Lý do ẩn bình luận gốc tự tạo");
        assertThat(updatedRoot.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(updatedRoot.getUpdatedAt()).isEqualTo(UPDATED_AT);

        Long parentId = root.getId();
        for (short depth = 1; depth <= 3; depth++) {
            Comment child = new Comment(post.getId(), parentId, authorId,
                    "Nội dung gốc " + depth, depth, CREATED_AT);
            child.setContent("Nội dung được giữ lại\n学习 " + depth);
            child.setStatus(CommentStatus.HIDDEN);
            child.setModeratedBy(moderatorId);
            child.setModerationReason("Lý do ẩn tự tạo " + depth);
            child.setUpdatedAt(UPDATED_AT);
            comments.saveAndFlush(child);
            entityManager.clear();

            Comment loaded = comments.findById(child.getId()).orElseThrow();
            assertThat(loaded.getId()).isPositive();
            assertThat(loaded.getPostId()).isEqualTo(post.getId());
            assertThat(loaded.getParentId()).isEqualTo(parentId);
            assertThat(loaded.getAuthorId()).isEqualTo(authorId);
            assertThat(loaded.getContent()).isEqualTo("Nội dung được giữ lại\n学习 " + depth);
            assertThat(loaded.getDepth()).isEqualTo(depth);
            assertThat(loaded.getStatus()).isEqualTo(CommentStatus.HIDDEN);
            assertThat(loaded.getModeratedBy()).isEqualTo(moderatorId);
            assertThat(loaded.getModerationReason()).isEqualTo("Lý do ẩn tự tạo " + depth);
            assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(loaded.getUpdatedAt()).isEqualTo(UPDATED_AT);
            parentId = child.getId();
        }
    }

    @Test
    void AC06_commentStatusesKeepTheirSqlStringsAndStoredContent() throws Exception {
        long authorId = registeredUser();
        Post post = posts.saveAndFlush(new Post(authorId, PostCategory.JOB,
                "Bài tự tạo", "Nội dung tự tạo", CREATED_AT));
        for (CommentStatus status : CommentStatus.values()) {
            Comment comment = new Comment(post.getId(), null, authorId,
                    "Nội dung còn lưu " + status, (short) 0, CREATED_AT);
            comment.setStatus(status);
            if (status != CommentStatus.VISIBLE) {
                comment.setModerationReason("Lý do tự tạo");
            }
            comments.saveAndFlush(comment);
            entityManager.clear();
            Comment loaded = comments.findById(comment.getId()).orElseThrow();
            assertThat(loaded.getStatus()).isEqualTo(status);
            assertThat(loaded.getContent()).isEqualTo("Nội dung còn lưu " + status);
            assertThat(jdbc.queryForObject("SELECT status FROM community.comments WHERE id=?",
                    String.class, comment.getId())).isEqualTo(status.name());
        }
    }

    @Test
    void AC06_reactionsReloadWithReconstructedCompositeKeysAcrossEveryTargetAndKind() throws Exception {
        long userId = registeredUser();
        long otherUserId = registeredUser();
        long targetId = 73001L;
        for (TargetType targetType : TargetType.values()) {
            for (ReactionKind kind : ReactionKind.values()) {
                ReactionId id = new ReactionId(userId, targetType, targetId, kind);
                reactions.saveAndFlush(new Reaction(id, CREATED_AT));
                entityManager.clear();
                ReactionId equivalentId = new ReactionId(userId, targetType, targetId, kind);
                Reaction loaded = reactions.findById(equivalentId).orElseThrow();
                assertThat(loaded.getId()).isEqualTo(equivalentId);
                assertThat(loaded.getId().getUserId()).isEqualTo(userId);
                assertThat(loaded.getId().getTargetType()).isEqualTo(targetType);
                assertThat(loaded.getId().getTargetId()).isEqualTo(targetId);
                assertThat(loaded.getId().getKind()).isEqualTo(kind);
                assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
                assertThat(jdbc.queryForMap("""
                        SELECT user_id, target_type, target_id, kind
                        FROM community.reactions WHERE user_id=? AND target_type=? AND target_id=? AND kind=?
                        """, userId, targetType.name(), targetId, kind.name()))
                        .containsEntry("user_id", userId).containsEntry("target_type", targetType.name())
                        .containsEntry("target_id", targetId).containsEntry("kind", kind.name());
                assertThat(reactions.findById(new ReactionId(otherUserId, targetType, targetId, kind))).isEmpty();
                assertThat(reactions.findById(new ReactionId(userId, targetType, targetId + 1, kind))).isEmpty();
            }
        }
    }

    @Test
    void AC05_casesRetainAllColumnsAndNullablePendingMetadata() throws Exception {
        long handlerId = registeredUser();
        ModerationCase moderationCase = cases.saveAndFlush(new ModerationCase(TargetType.POST, 74001L, CREATED_AT));
        assertThat(moderationCase.getId()).isPositive();
        entityManager.clear();

        ModerationCase initial = cases.findById(moderationCase.getId()).orElseThrow();
        assertThat(initial.getTargetType()).isEqualTo(TargetType.POST);
        assertThat(initial.getTargetId()).isEqualTo(74001L);
        assertThat(initial.getReportCount()).isEqualTo(1);
        assertThat(initial.getReasons()).isEmpty();
        assertThat(initial.getStatus()).isEqualTo(CaseStatus.PENDING);
        assertThat(initial.isAutoHidden()).isFalse();
        assertThat(initial.getHandledBy()).isNull();
        assertThat(initial.getHandledAt()).isNull();
        assertThat(initial.getDecisionNote()).isNull();
        assertThat(initial.getCreatedAt()).isEqualTo(CREATED_AT);

        ReportEntry report = new ReportEntry(handlerId, ReportReason.SPAM, "Chi tiết tự tạo", CREATED_AT);
        initial.setReportCount(7);
        initial.setReasons(List.of(report));
        initial.setStatus(CaseStatus.ACTIONED);
        initial.setAutoHidden(true);
        initial.setHandledBy(handlerId);
        initial.setHandledAt(UPDATED_AT);
        initial.setDecisionNote("Đã xử lý nội dung tự tạo\n学习");
        cases.saveAndFlush(initial);
        entityManager.clear();

        ModerationCase loaded = cases.findById(moderationCase.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(moderationCase.getId());
        assertThat(loaded.getTargetType()).isEqualTo(TargetType.POST);
        assertThat(loaded.getTargetId()).isEqualTo(74001L);
        assertThat(loaded.getReportCount()).isEqualTo(7);
        assertThat(loaded.getReasons()).containsExactly(report);
        assertThat(loaded.getStatus()).isEqualTo(CaseStatus.ACTIONED);
        assertThat(loaded.isAutoHidden()).isTrue();
        assertThat(loaded.getHandledBy()).isEqualTo(handlerId);
        assertThat(loaded.getHandledAt()).isEqualTo(UPDATED_AT);
        assertThat(loaded.getDecisionNote()).isEqualTo("Đã xử lý nội dung tự tạo\n学习");
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void AC06_caseTargetsAndStatusesRoundTripAsSqlStrings() throws Exception {
        long handlerId = registeredUser();
        for (TargetType targetType : List.of(TargetType.POST, TargetType.COMMENT, TargetType.COLLECTION)) {
            for (CaseStatus status : CaseStatus.values()) {
                ModerationCase moderationCase = new ModerationCase(targetType, 75001L, CREATED_AT);
                moderationCase.setStatus(status);
                if (status != CaseStatus.PENDING) {
                    moderationCase.setHandledBy(handlerId);
                    moderationCase.setHandledAt(UPDATED_AT);
                    moderationCase.setDecisionNote("Quyết định tự tạo");
                }
                cases.saveAndFlush(moderationCase);
                entityManager.clear();
                ModerationCase loaded = cases.findById(moderationCase.getId()).orElseThrow();
                assertThat(loaded.getTargetType()).isEqualTo(targetType);
                assertThat(loaded.getStatus()).isEqualTo(status);
                assertThat(jdbc.queryForMap("SELECT target_type, status FROM community.moderation_cases WHERE id=?",
                        moderationCase.getId())).containsEntry("target_type", targetType.name())
                        .containsEntry("status", status.name());
            }
        }
    }

    @Test
    void AC09_reportEntriesRoundTripBetweenTypedListsAndCanonicalJsonb() throws Exception {
        long firstUserId = registeredUser();
        long secondUserId = registeredUser();
        List<ReportEntry> entries = List.of(
                new ReportEntry(firstUserId, ReportReason.SPAM, null, CREATED_AT),
                new ReportEntry(secondUserId, ReportReason.OFFENSIVE, "Tiếng Việt\n中文 😀", UPDATED_AT),
                new ReportEntry(firstUserId, ReportReason.HARASSMENT, "Chi tiết tự tạo", CREATED_AT.plusSeconds(1)),
                new ReportEntry(secondUserId, ReportReason.COPYRIGHT, "Trích dẫn tự tạo", CREATED_AT.plusSeconds(2)),
                new ReportEntry(firstUserId, ReportReason.OTHER, "", CREATED_AT.plusSeconds(3)));
        ModerationCase moderationCase = new ModerationCase(TargetType.COMMENT, 76001L, CREATED_AT);
        moderationCase.setReportCount(entries.size());
        moderationCase.setReasons(entries);
        cases.saveAndFlush(moderationCase);
        entityManager.clear();

        assertThat(cases.findById(moderationCase.getId()).orElseThrow().getReasons()).containsExactlyElementsOf(entries);
        JsonNode persistedJson = mapper.readTree(jdbc.queryForObject(
                "SELECT reasons::text FROM community.moderation_cases WHERE id=?", String.class, moderationCase.getId()));
        // Compare parsed JSON on both sides, independently of Jackson's in-memory int/long node choice.
        assertThat(persistedJson).isEqualTo(mapper.readTree(canonicalJson(entries).toString()));
        assertThat(persistedJson.get(0).get("detail").isNull()).isTrue();
        for (int index = 0; index < entries.size(); index++) {
            List<String> keys = new ArrayList<>();
            persistedJson.get(index).fieldNames().forEachRemaining(keys::add);
            assertThat(keys).containsExactlyInAnyOrder("userId", "reason", "detail", "reportedAt");
            assertThat(persistedJson.get(index).get("userId").isIntegralNumber()).isTrue();
            assertThat(persistedJson.get(index).get("userId").longValue()).isEqualTo(entries.get(index).userId());
            assertThat(persistedJson.get(index).get("reason").asText()).isEqualTo(entries.get(index).reason().name());
            assertThat(persistedJson.get(index).get("reportedAt").isTextual()).isTrue();
            assertThat(persistedJson.get(index).get("reportedAt").asText()).isEqualTo(entries.get(index).reportedAt().toString());
        }

        String nativeJson = """
                [{"userId":%d,"reason":"OTHER","detail":null,"reportedAt":"2026-10-31T07:08:09.654321Z"},
                 {"userId":%d,"reason":"SPAM","detail":"Tự tạo\\n你好 😀","reportedAt":"2026-10-31T00:01:02.123456Z"}]
                """.formatted(secondUserId, firstUserId);
        jdbc.update("UPDATE community.moderation_cases SET reasons=CAST(? AS jsonb), report_count=? WHERE id=?",
                nativeJson, 2, moderationCase.getId());
        entityManager.clear();
        ModerationCase fromNative = cases.findById(moderationCase.getId()).orElseThrow();
        assertThat(fromNative.getReportCount()).isEqualTo(2);
        assertThat(fromNative.getReasons()).containsExactly(
                new ReportEntry(secondUserId, ReportReason.OTHER, null, UPDATED_AT),
                new ReportEntry(firstUserId, ReportReason.SPAM, "Tự tạo\n你好 😀", CREATED_AT));

        fromNative.setReasons(List.of());
        cases.saveAndFlush(fromNative);
        entityManager.clear();
        assertThat(cases.findById(moderationCase.getId()).orElseThrow().getReasons()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT reasons::text FROM community.moderation_cases WHERE id=?",
                String.class, moderationCase.getId())).isEqualTo("[]");
    }

    private ArrayNode canonicalJson(List<ReportEntry> entries) {
        ArrayNode expected = mapper.createArrayNode();
        for (ReportEntry entry : entries) {
            ObjectNode report = expected.addObject();
            report.put("userId", entry.userId());
            report.put("reason", entry.reason().name());
            if (entry.detail() == null) {
                report.putNull("detail");
            } else {
                report.put("detail", entry.detail());
            }
            report.put("reportedAt", entry.reportedAt().toString());
        }
        return expected;
    }

    private long registeredUser() throws Exception {
        String password = UUID.randomUUID() + "Aa1";
        String body = mapper.writeValueAsString(Map.of(
                "email", "community-persistence-" + UUID.randomUUID() + "@example.test",
                "password", password, "confirmPassword", password,
                "displayName", "Người dùng kiểm thử", "acceptTerms", true));
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long userId = mapper.readTree(response).path("userId").asLong();
        assertThat(userId).isPositive();
        return userId;
    }
}
