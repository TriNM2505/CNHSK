package com.cnhsk.community.contest;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/** The ranking SQL: one best round per player, points first, then the faster round, then who got there first. */
@Transactional
class GameContestRepositoryIntegrationTest extends AbstractIntegrationTest {
    private static final Instant START = Instant.parse("2035-11-14T04:00:00Z");
    private static final Instant END = START.plusSeconds(3600);

    @Autowired private GameContestRepository repository;
    @Autowired private JdbcTemplate jdbc;
    private long moderator;
    private Contest contest;

    @BeforeEach
    void setUp() {
        moderator = user("Kiểm duyệt");
        long id = jdbc.queryForObject("INSERT INTO community.contests(name,starts_at,ends_at,status,created_by,game_code,prize_count) "
                + "VALUES ('Đua chữ',?,?,'PUBLISHED',?,'CHAR_RAIN',3) RETURNING id", Long.class,
                Timestamp.from(START), Timestamp.from(END), moderator);
        contest = Contest.game("Đua chữ", null, "CHAR_RAIN", START, END, 3, moderator, START);
        ReflectionTestUtils.setField(contest, "id", id);
    }

    @Test
    void ranksEachPlayersBestRoundByPointsThenDurationThenSubmission() {
        long an = user("An");
        long binh = user("Bình");
        long cuong = user("Cường");
        long dung = user("Dũng");
        round(an, 900, 50_000, 10);
        round(an, 1500, 70_000, 20);      // An's best: highest score wins over a faster lower round
        round(an, 1500, 65_000, 30);      // same score, faster: this is An's row
        round(binh, 1500, 60_000, 40);    // ties An on points but faster
        round(cuong, 1500, 60_000, 50);   // ties Bình on points and time, submitted later
        round(dung, 700, 30_000, 60);

        var ranking = repository.ranking(contest, Set.of(), 50);

        assertThat(ranking.total()).isEqualTo(4);
        assertThat(ranking.rows()).extracting(GameContestRepository.Row::userId).containsExactly(binh, cuong, an, dung);
        assertThat(ranking.rows()).extracting(GameContestRepository.Row::rank).containsExactly(1L, 2L, 3L, 4L);
        assertThat(ranking.rows().get(2).points()).isEqualTo(1500);
        assertThat(ranking.rows().get(2).durationMs()).isEqualTo(65_000);
        assertThat(ranking.rows().get(2).achievedAt()).isEqualTo(START.plusSeconds(30 + 1));
    }

    @Test
    void limitKeepsTheTotalOfEveryRankedPlayer() {
        for (int i = 0; i < 5; i++) round(user("Người " + i), 100L * (i + 1), 10_000, 10 + i);

        var ranking = repository.ranking(contest, Set.of(), 3);

        assertThat(ranking.rows()).hasSize(3).extracting(GameContestRepository.Row::points).containsExactly(500L, 400L, 300L);
        assertThat(ranking.total()).isEqualTo(5);
    }

    @Test
    void ignoresRoundsOutsideTheWindowOtherGamesAndContestsUnfinishedSuspiciousBannedAndExcluded() {
        long ranked = user("Hợp lệ");
        round(ranked, 100, 10_000, 10);
        insert(user("Trước giờ"), contest.getId(), "CHAR_RAIN", "SUBMITTED", false, 9_999, START.minusSeconds(1));
        insert(user("Đúng giờ đóng"), contest.getId(), "CHAR_RAIN", "SUBMITTED", false, 9_999, END);
        insert(user("Trò khác"), contest.getId(), "PINYIN_MATCH", "SUBMITTED", false, 9_999, START.plusSeconds(5));
        insert(user("Cuộc thi khác"), contest.getId() + 1_000_000, "CHAR_RAIN", "SUBMITTED", false, 9_999, START.plusSeconds(5));
        insert(user("Chưa nộp"), contest.getId(), "CHAR_RAIN", "IN_PROGRESS", false, 9_999, START.plusSeconds(5));
        insert(user("Đáng ngờ"), contest.getId(), "CHAR_RAIN", "SUBMITTED", true, 9_999, START.plusSeconds(5));
        long banned = user("Bị cấm");
        round(banned, 9_999, 1_000, 5);
        long excluded = user("Bị loại");
        round(excluded, 9_999, 1_000, 6);
        repository.exclude(contest.getId(), excluded, "Gian lận", moderator, START);

        var ranking = repository.ranking(contest, Set.of(banned), 50);

        assertThat(ranking.rows()).extracting(GameContestRepository.Row::userId).containsExactly(ranked);
        assertThat(ranking.total()).isEqualTo(1);
    }

    @Test
    void excludeUpsertsTheLatestDecisionAndIncludeRemovesIt() {
        long player = user("Người chơi");
        repository.exclude(contest.getId(), player, "Lần đầu", moderator, START);
        repository.exclude(contest.getId(), player, "Đã xác minh gian lận", moderator, START.plusSeconds(60));

        assertThat(repository.exclusions(contest.getId())).singleElement().satisfies(row -> {
            assertThat(row.userId()).isEqualTo(player);
            assertThat(row.note()).isEqualTo("Đã xác minh gian lận");
            assertThat(row.decidedAt()).isEqualTo(START.plusSeconds(60));
        });

        repository.include(contest.getId(), player);
        assertThat(repository.exclusions(contest.getId())).isEmpty();
    }

    private void round(long userId, long score, int durationMs, int servedAfterSeconds) {
        jdbc.update("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,game_code,status,served_at,submitted_at,score,duration_ms,suspicious) "
                        + "VALUES (?,'GAME','GAME_ROUND',?,'CHAR_RAIN','SUBMITTED',?,?,?,?,false)",
                userId, contest.getId(), Timestamp.from(START.plusSeconds(servedAfterSeconds)),
                Timestamp.from(START.plusSeconds(servedAfterSeconds + 1L)), score, durationMs);
    }

    private void insert(long userId, long contestId, String game, String status, boolean suspicious, long score, Instant served) {
        jdbc.update("INSERT INTO learning.attempts(user_id,kind,ref_type,ref_id,game_code,status,served_at,submitted_at,score,duration_ms,suspicious) "
                        + "VALUES (?,'GAME','GAME_ROUND',?,?,?,?,?,?,1000,?)",
                userId, contestId, game, status, Timestamp.from(served),
                status.equals("SUBMITTED") ? Timestamp.from(served.plusSeconds(1)) : null, score, suspicious);
    }

    private long user(String name) {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", "not-a-real-hash", name);
    }
}
