package com.cnhsk.community.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** The union of community events that make up the bell, and the seen marker kept on the user. */
@Transactional
class CommunityNotificationRepositoryIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Instant SINCE = NOW.minus(Duration.ofDays(90));

    @Autowired private CommunityNotificationRepository repository;
    @Autowired private AuthApi auth;
    @Autowired private JdbcTemplate jdbc;
    private long me;
    private long an;
    private long binh;
    private long moderator;

    @BeforeEach
    void setUp() {
        me = user("Tôi");
        an = user("An");
        binh = user("Bình");
        moderator = user("Kiểm duyệt");
    }

    @Test
    void collectsReviewsCommentsRepliesLikesAndFollowsNewestFirst() {
        long mine = post(me, "Bài của tôi", "APPROVED", moderator, NOW.minusSeconds(600));
        long theirs = post(an, "Bài của An", "APPROVED", moderator, NOW.minusSeconds(900));
        long myComment = comment(theirs, null, me, "Ý kiến của tôi", "VISIBLE", NOW.minusSeconds(550));
        long comment = comment(mine, null, an, "Hay quá", "VISIBLE", NOW.minusSeconds(500));
        long reply = comment(theirs, myComment, binh, "Đồng ý", "VISIBLE", NOW.minusSeconds(400));
        like(an, mine, NOW.minusSeconds(300));
        like(binh, mine, NOW.minusSeconds(200));
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind,created_at) VALUES (?,'USER',?,'FOLLOW',?)",
                an, me, Timestamp.from(NOW.minusSeconds(100)));

        var items = repository.recent(me, SINCE, 50);

        assertThat(items).extracting(CommunityNotificationRepository.Item::type)
                .containsExactly("FOLLOW", "LIKE", "REPLY", "COMMENT", "POST_APPROVED");
        assertThat(items.get(0).actorId()).isEqualTo(an);
        var likes = items.get(1);
        assertThat(likes.key()).isEqualTo("like:" + mine);
        assertThat(likes.total()).isEqualTo(2);
        assertThat(likes.actorId()).isEqualTo(binh);
        assertThat(likes.at()).isEqualTo(NOW.minusSeconds(200));
        assertThat(items.get(2).commentId()).isEqualTo(reply);
        assertThat(items.get(2).postId()).isEqualTo(theirs);
        assertThat(items.get(3).commentId()).isEqualTo(comment);
        assertThat(items.get(3).excerpt()).isEqualTo("Hay quá");
        assertThat(items.get(4).postTitle()).isEqualTo("Bài của tôi");
    }

    @Test
    void leavesOutMyOwnActionsHiddenCommentsAndAnythingOlderThanTheWindow() {
        long mine = post(me, "Bài của tôi", "APPROVED", moderator, NOW.minus(Duration.ofDays(91)));
        comment(mine, null, me, "Tự bình luận", "VISIBLE", NOW.minusSeconds(50));
        jdbc.update("INSERT INTO community.comments(post_id,author_id,content,status,moderation_reason,created_at) "
                + "VALUES (?,?,'Bị ẩn','HIDDEN','Spam',?)", mine, an, Timestamp.from(NOW.minusSeconds(40)));
        comment(mine, null, an, "Quá cũ", "VISIBLE", NOW.minus(Duration.ofDays(91)));
        like(me, mine, NOW.minusSeconds(30));

        assertThat(repository.recent(me, SINCE, 50)).isEmpty();
        assertThat(repository.countAfter(me, SINCE, null)).isZero();
    }

    @Test
    void countsOnlyItemsAfterTheSeenMarkerAndLimitsTheList() {
        long mine = post(me, "Bài của tôi", "APPROVED", moderator, NOW.minusSeconds(1000));
        for (int i = 0; i < 5; i++) comment(mine, null, an, "Bình luận " + i, "VISIBLE", NOW.minusSeconds(900 - i * 100L));

        assertThat(repository.countAfter(me, SINCE, null)).isEqualTo(6);
        assertThat(repository.countAfter(me, SINCE, NOW.minusSeconds(750))).isEqualTo(3);
        assertThat(repository.countAfter(me, SINCE, NOW)).isZero();
        assertThat(repository.recent(me, SINCE, 2)).extracting(CommunityNotificationRepository.Item::excerpt)
                .containsExactly("Bình luận 4", "Bình luận 3");
    }

    @Test
    void theSeenMarkerNeverMovesBackwards() {
        assertThat(auth.notificationsSeenAt(me)).isEmpty();
        auth.markNotificationsSeen(me, NOW);
        auth.markNotificationsSeen(me, NOW.minusSeconds(60));
        assertThat(auth.notificationsSeenAt(me)).contains(NOW);
        auth.markNotificationsSeen(me, NOW.plusSeconds(60));
        assertThat(auth.notificationsSeenAt(me)).contains(NOW.plusSeconds(60));
    }

    private long post(long author, String title, String status, long reviewer, Instant reviewedAt) {
        return jdbc.queryForObject("INSERT INTO community.posts(author_id,category,title,content,status,reviewed_by,reviewed_at,"
                        + "published_at,created_at,updated_at) VALUES (?,'QUESTION',?,'Nội dung',?,?,?,?,?,?) RETURNING id", Long.class,
                author, title, status, reviewer, Timestamp.from(reviewedAt), Timestamp.from(reviewedAt),
                Timestamp.from(reviewedAt.minusSeconds(60)), Timestamp.from(reviewedAt));
    }

    private long comment(long post, Long parent, long author, String content, String status, Instant at) {
        Integer depth = parent == null ? 0 : jdbc.queryForObject("SELECT depth + 1 FROM community.comments WHERE id = ?",
                Integer.class, parent);
        return jdbc.queryForObject("INSERT INTO community.comments(post_id,parent_id,author_id,content,depth,status,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?) RETURNING id", Long.class, post, parent, author, content, depth, status,
                Timestamp.from(at), Timestamp.from(at));
    }

    private void like(long user, long post, Instant at) {
        jdbc.update("INSERT INTO community.reactions(user_id,target_type,target_id,kind,created_at) VALUES (?,'POST',?,'LIKE',?)",
                user, post, Timestamp.from(at));
    }

    private long user(String name) {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", "not-a-real-hash", name);
    }
}
