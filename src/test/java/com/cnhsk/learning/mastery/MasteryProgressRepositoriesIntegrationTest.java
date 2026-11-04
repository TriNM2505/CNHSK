package com.cnhsk.learning.mastery;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.cnhsk.learning.progress.UserProgress;
import com.cnhsk.learning.pronunciation.PronStageProgressRepository;
import com.cnhsk.learning.review.LexemeSchedule;
import com.cnhsk.learning.review.LexemeScheduleRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MasteryProgressRepositoriesIntegrationTest extends AbstractIntegrationTest {

    private static final Instant SEEN_AT = Instant.parse("2026-11-04T01:00:00Z");
    private static final String TEST_PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private KnowledgeProgressRepository knowledge;
    @Autowired private LexemeScheduleRepository schedules;
    @Autowired private PronStageProgressRepository pronunciation;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;

    @Test
    void AT03_oldAndNewEntitiesValidateAgainstTheSameFlywayTable() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(entityManager.getMetamodel().getEntities()).extracting(entity -> entity.getJavaType().getName())
                .contains(KnowledgeProgress.class.getName(), LexemeSchedule.class.getName(), UserProgress.class.getName());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.flyway_schema_history WHERE version='6' AND success", Long.class))
                .isEqualTo(1L);
    }

    @Test
    void AT03_knowledgeProgressDecimalsArrayAndUtcDeadlineRoundTripWithoutChangingFsrsDefaults() {
        long userId = user();
        KnowledgeProgress progress = new KnowledgeProgress(userId, 901L);
        progress.setMastery(new BigDecimal("0.533"));
        progress.setMpState("review");
        progress.setConfidence(new BigDecimal("2.407"));
        // The locked schema stores bigint IDs without an element FK.
        progress.setEvidenceItemIds(new Long[] {41L, 42L});
        progress.setNextReviewAt(SEEN_AT.plusSeconds(3 * 86400));
        entityManager.persist(progress);
        entityManager.flush();
        entityManager.clear();

        KnowledgeProgress loaded = knowledge.findByUserIdAndTargetId(userId, 901).orElseThrow();
        assertThat(loaded.getUserId()).isEqualTo(userId);
        assertThat(loaded.getTargetType()).isEqualTo("KNOWLEDGE_POINT");
        assertThat(loaded.getTargetId()).isEqualTo(901L);
        assertThat(loaded.getMastery()).isEqualByComparingTo("0.533");
        assertThat(loaded.getMpState()).isEqualTo("review");
        assertThat(loaded.getConfidence()).isEqualByComparingTo("2.407");
        assertThat(loaded.getEvidenceItemIds()).containsExactly(41L, 42L);
        assertThat(loaded.getNextReviewAt()).isEqualTo(SEEN_AT.plusSeconds(3 * 86400));
        assertThat(loaded.getUpdatedAt()).isNotNull();

        loaded.setMastery(new BigDecimal("0.611"));
        loaded.setMpState("consolidate");
        loaded.setConfidence(new BigDecimal("3.000"));
        loaded.setEvidenceItemIds(new Long[] {41L, 42L, 43L});
        loaded.setNextReviewAt(SEEN_AT.plusSeconds(7 * 86400));
        entityManager.flush();
        entityManager.clear();
        KnowledgeProgress updated = knowledge.findByUserIdAndTargetId(userId, 901).orElseThrow();
        assertThat(updated.getMastery()).isEqualByComparingTo("0.611");
        assertThat(updated.getMpState()).isEqualTo("consolidate");
        assertThat(updated.getConfidence()).isEqualByComparingTo("3.000");
        assertThat(updated.getEvidenceItemIds()).containsExactly(41L, 42L, 43L);
        assertThat(updated.getNextReviewAt()).isEqualTo(SEEN_AT.plusSeconds(7 * 86400));
        assertThat(jdbc.queryForMap("SELECT state, stability, difficulty, percent FROM learning.user_progress "
                + "WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=901", userId))
                .containsEntry("state", "LEARNING").containsEntry("stability", new BigDecimal("0.100"))
                .containsEntry("difficulty", new BigDecimal("5.00")).containsEntry("percent", null);
    }

    @Test
    void AT03_lexemeScheduleDecimalsCountersAndUtcTimesRoundTripWithoutMpColumns() {
        long userId = user();
        LexemeSchedule schedule = new LexemeSchedule(userId, 901L, SEEN_AT);
        schedule.setMastery(new BigDecimal("0.900"));
        schedule.setStability(new BigDecimal("3.173"));
        schedule.setDifficulty(new BigDecimal("5.25"));
        schedule.setReviewCount(2);
        schedule.setLapseCount(1);
        schedule.setNextReviewAt(SEEN_AT.plusSeconds(3 * 86400));
        entityManager.persist(schedule);
        entityManager.flush();
        entityManager.clear();

        LexemeSchedule loaded = schedules.findByUserIdAndTargetId(userId, 901).orElseThrow();
        assertThat(loaded.getUserId()).isEqualTo(userId);
        assertThat(loaded.getTargetType()).isEqualTo("LEXEME");
        assertThat(loaded.getTargetId()).isEqualTo(901L);
        assertThat(loaded.getMastery()).isEqualByComparingTo("0.900");
        assertThat(loaded.getStability()).isEqualByComparingTo("3.173");
        assertThat(loaded.getDifficulty()).isEqualByComparingTo("5.25");
        assertThat(loaded.getReviewCount()).isEqualTo(2);
        assertThat(loaded.getLapseCount()).isEqualTo(1);
        assertThat(loaded.getLastSeenAt()).isEqualTo(SEEN_AT);
        assertThat(loaded.getNextReviewAt()).isEqualTo(SEEN_AT.plusSeconds(3 * 86400));
        assertThat(loaded.getUpdatedAt()).isNotNull();

        loaded.setLastSeenAt(SEEN_AT.plusSeconds(86400));
        loaded.setReviewCount(3);
        loaded.setNextReviewAt(SEEN_AT.plusSeconds(4 * 86400));
        entityManager.flush();
        entityManager.clear();
        LexemeSchedule updated = schedules.findByUserIdAndTargetId(userId, 901).orElseThrow();
        assertThat(updated.getLastSeenAt()).isEqualTo(SEEN_AT.plusSeconds(86400));
        assertThat(updated.getReviewCount()).isEqualTo(3);
        assertThat(updated.getNextReviewAt()).isEqualTo(SEEN_AT.plusSeconds(4 * 86400));
        assertThat(jdbc.queryForMap("SELECT state, mp_state, confidence, cardinality(evidence_item_ids) AS evidence_count "
                + "FROM learning.user_progress WHERE user_id=? AND target_type='LEXEME' AND target_id=901", userId))
                .containsEntry("state", "LEARNING").containsEntry("mp_state", null)
                .containsEntry("confidence", null).containsEntry("evidence_count", 0);
    }

    @Test
    void AT03_newEntityDefaultsMatchTheExistingStorageDefaults() {
        long userId = user();
        entityManager.persist(new KnowledgeProgress(userId, 901L));
        entityManager.persist(new LexemeSchedule(userId, 901L, SEEN_AT));
        entityManager.flush();
        entityManager.clear();
        KnowledgeProgress progress = knowledge.findByUserIdAndTargetId(userId, 901).orElseThrow();
        assertThat(progress.getMastery()).isEqualByComparingTo("0");
        assertThat(progress.getMpState()).isEqualTo("notyet");
        assertThat(progress.getConfidence()).isNull();
        assertThat(progress.getEvidenceItemIds()).isEmpty();
        assertThat(progress.getNextReviewAt()).isNull();
        LexemeSchedule schedule = schedules.findByUserIdAndTargetId(userId, 901).orElseThrow();
        assertThat(schedule.getMastery()).isEqualByComparingTo("0");
        assertThat(schedule.getStability()).isEqualByComparingTo("0.1");
        assertThat(schedule.getDifficulty()).isEqualByComparingTo("5");
        assertThat(schedule.getReviewCount()).isZero();
        assertThat(schedule.getLapseCount()).isZero();
        assertThat(schedule.getNextReviewAt()).isNull();
    }

    @Test
    void AT03_repositoriesFilterBothUserAndLiteralTargetTypeWhilePronunciationStillReadsItsRows() {
        long firstUser = user();
        long secondUser = user();
        KnowledgeProgress first = new KnowledgeProgress(firstUser, 901L);
        first.setMastery(new BigDecimal("0.111"));
        KnowledgeProgress second = new KnowledgeProgress(secondUser, 901L);
        second.setMastery(new BigDecimal("0.222"));
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.persist(new KnowledgeProgress(firstUser, 903L));
        entityManager.persist(new LexemeSchedule(firstUser, 901L, SEEN_AT));
        entityManager.persist(new LexemeSchedule(secondUser, 901L, SEEN_AT));
        entityManager.persist(new LexemeSchedule(firstUser, 904L, SEEN_AT));
        entityManager.flush();
        jdbc.update("INSERT INTO learning.user_progress (user_id, target_type, target_id, percent, state) "
                + "VALUES (?, 'PRON_STAGE', 901, 85.00, 'COMPLETED'), (?, 'PRON_STAGE', 902, NULL, 'AVAILABLE')", firstUser, firstUser);
        entityManager.clear();

        assertThat(knowledge.findByUserId(firstUser)).extracting(KnowledgeProgress::getTargetId)
                .containsExactlyInAnyOrder(901L, 903L);
        assertThat(schedules.findByUserId(firstUser)).extracting(LexemeSchedule::getTargetId)
                .containsExactlyInAnyOrder(901L, 904L);
        assertThat(knowledge.findByUserId(secondUser)).hasSize(1).allSatisfy(row -> assertThat(row.getUserId()).isEqualTo(secondUser));
        assertThat(schedules.findByUserId(secondUser)).hasSize(1).allSatisfy(row -> assertThat(row.getUserId()).isEqualTo(secondUser));
        assertThat(knowledge.findByUserIdAndTargetId(firstUser, 901).orElseThrow().getMastery()).isEqualByComparingTo("0.111");
        assertThat(knowledge.findByUserIdAndTargetId(secondUser, 901).orElseThrow().getMastery()).isEqualByComparingTo("0.222");
        assertThat(knowledge.findByUserIdAndTargetId(firstUser, 902)).isEmpty();
        assertThat(knowledge.findByUserIdAndTargetId(firstUser, 904)).isEmpty();
        assertThat(knowledge.findByUserIdAndTargetId(secondUser, 903)).isEmpty();
        assertThat(schedules.findByUserIdAndTargetId(firstUser, 902)).isEmpty();
        assertThat(schedules.findByUserIdAndTargetId(firstUser, 903)).isEmpty();
        assertThat(schedules.findByUserIdAndTargetId(secondUser, 904)).isEmpty();
        assertThat(knowledge.findByUserId(-1)).isEmpty();
        assertThat(schedules.findByUserId(-1)).isEmpty();
        assertThat(pronunciation.findByUserIdAndTargetType(firstUser, "PRON_STAGE"))
                .hasSize(2).anySatisfy(row -> {
                    assertThat(row.getTargetId()).isEqualTo(901L);
                    assertThat(row.getPercent()).isEqualByComparingTo("85.00");
                }).anySatisfy(row -> {
                    assertThat(row.getTargetId()).isEqualTo(902L);
                    assertThat(row.getPercent()).isNull();
                });
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "at03-repo-" + UUID.randomUUID() + "@example.test", TEST_PASSWORD_HASH, "Người kiểm thử");
    }
}
