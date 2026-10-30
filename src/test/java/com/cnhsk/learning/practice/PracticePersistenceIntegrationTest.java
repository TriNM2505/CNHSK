package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@AutoConfigureMockMvc
@Transactional
class PracticePersistenceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant SERVED_AT = Instant.parse("2026-10-30T23:30:00Z");

    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MockMvc mockMvc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void FR001_migrationsRunThroughV4AndHibernateValidatesTheSchema() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM learning.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).containsSubsequence("1", "2", "2.1", "3", "4");
        assertThat(entityManager.getEntityManagerFactory().getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate");
    }

    @Test
    void FR001_upgradeFromV3PreservesContentAndValidatesWithoutReapplyingV4() {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("cnhsk_practice_upgrade_test")
                .withUsername("cnhsk_test_owner")
                .withPassword(UUID.randomUUID().toString())) {
            database.start();
            Flyway.configure()
                    .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("learning", "auth", "community", "shared").defaultSchema("learning")
                    .target("3").load().migrate();
            JdbcTemplate upgradeJdbc = new JdbcTemplate(new DriverManagerDataSource(
                    database.getJdbcUrl(), database.getUsername(), database.getPassword()));
            long topicId = upgradeJdbc.queryForObject(
                    "INSERT INTO learning.topics (code, name, hsk_level) VALUES (?, ?, ?) RETURNING id",
                    Long.class, "V4_UPGRADE", "Chủ đề tự tạo", 9);
            long lexemeId = upgradeJdbc.queryForObject(
                    "INSERT INTO learning.lexemes (kind, text, meaning_vi, hsk_level) "
                            + "VALUES ('CHAR', ?, ?, ?) RETURNING id",
                    Long.class, "我", "tôi", 1);
            upgradeJdbc.update("INSERT INTO learning.topic_items (topic_id, lexeme_id) VALUES (?, ?)",
                    topicId, lexemeId);

            Flyway flyway = Flyway.configure()
                    .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("learning", "auth", "community", "shared").defaultSchema("learning")
                    .target("4").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(upgradeJdbc.queryForObject(
                    "SELECT count(*) FROM learning.topic_items WHERE topic_id=? AND lexeme_id=?",
                    Long.class, topicId, lexemeId)).isEqualTo(1L);
            assertThat(upgradeJdbc.queryForObject(
                    "SELECT hsk_level FROM learning.topics WHERE id=?", Short.class, topicId))
                    .isEqualTo((short) 9);
        }
    }

    @Test
    void FR001_itemColumnsHaveTheSpecifiedTypesNullabilityAndDefaults() {
        assertThat(jdbc.queryForMap("SELECT data_type, character_maximum_length, is_nullable, column_default "
                + "FROM information_schema.columns WHERE table_schema='learning' "
                + "AND table_name='attempt_items' AND column_name='item_type'"))
                .containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 30)
                .containsEntry("is_nullable", "NO").containsEntry("column_default", null);
        assertThat(jdbc.queryForMap("SELECT data_type, character_maximum_length, is_nullable, column_default "
                + "FROM information_schema.columns WHERE table_schema='learning' "
                + "AND table_name='attempt_items' AND column_name='skill'"))
                .containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 20)
                .containsEntry("is_nullable", "NO").containsEntry("column_default", null);
        assertThat(jdbc.queryForMap("SELECT data_type, is_nullable, column_default "
                + "FROM information_schema.columns WHERE table_schema='learning' "
                + "AND table_name='attempt_items' AND column_name='meta'"))
                .containsEntry("data_type", "jsonb").containsEntry("is_nullable", "NO")
                .containsEntry("column_default", "'{}'::jsonb");
    }

    @ParameterizedTest
    @ValueSource(strings = {"VOCAB", "LISTENING", "READING", "CHARACTER", "WRITING", "GRAMMAR", "PRONUNCIATION"})
    void FR001_allSevenSkillsCanBePersistedWithoutPostgresEnums(String skill) throws Exception {
        Attempt attempt = attempt("PRACTICE", "TOPIC_PRACTICE");
        AttemptItem item = new AttemptItem(attempt.getId(), 1, "MEANING_CHOICE", skill);
        item.setAnswer("A");
        item.setCorrect(true);
        items.saveAndFlush(item);
        entityManager.clear();
        assertThat(items.findById(item.getId()).orElseThrow().getSkill()).isEqualTo(skill);
    }

    @Test
    void FR001_unknownSkillIsRejectedByTheDatabase() throws Exception {
        Attempt attempt = attempt("PRACTICE", "TOPIC_PRACTICE");
        assertThatThrownBy(() -> items.saveAndFlush(new AttemptItem(attempt.getId(), 1, "MEANING_CHOICE", "OTHER")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"item_type", "skill", "meta"})
    void FR001_newColumnsRejectNull(String column) throws Exception {
        Attempt attempt = attempt("PRACTICE", "TOPIC_PRACTICE");
        // The parameter only selects one of three fixed statements, never SQL from user input.
        String sql = switch (column) {
            case "item_type" -> "INSERT INTO learning.attempt_items (attempt_id, item_type, skill) VALUES (?, NULL, 'VOCAB')";
            case "skill" -> "INSERT INTO learning.attempt_items (attempt_id, item_type, skill) VALUES (?, 'STUDY_CARD', NULL)";
            default -> "INSERT INTO learning.attempt_items (attempt_id, item_type, skill, meta) VALUES (?, 'STUDY_CARD', 'VOCAB', NULL)";
        };
        assertThatThrownBy(() -> jdbc.update(sql, attempt.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void FR001_databaseSuppliesEmptyMetadataForSqlInserts() throws Exception {
        Attempt attempt = attempt("PRACTICE", "TOPIC_STUDY");
        long itemId = jdbc.queryForObject(
                "INSERT INTO learning.attempt_items (attempt_id, item_type, skill, order_index) "
                        + "VALUES (?, 'STUDY_CARD', 'VOCAB', 1) RETURNING id", Long.class, attempt.getId());
        assertThat(items.findById(itemId).orElseThrow().getMeta()).isEmpty();
    }

    @Test
    void FR001_AT05_duplicateItemNumberIsRejectedButDifferentSessionsCanReuseIt() throws Exception {
        Attempt first = attempt("PRACTICE", "TOPIC_STUDY");
        Attempt second = attempt("PRACTICE", "TOPIC_STUDY");
        items.saveAndFlush(new AttemptItem(first.getId(), 1, "STUDY_CARD", "VOCAB"));
        items.saveAndFlush(new AttemptItem(second.getId(), 1, "STUDY_CARD", "VOCAB"));
        assertThat(items.existsByAttemptIdAndOrderIndex(first.getId(), 1)).isTrue();
        assertThat(items.existsByAttemptIdAndOrderIndex(first.getId(), 2)).isFalse();
        assertThatThrownBy(() -> items.saveAndFlush(new AttemptItem(first.getId(), 1, "STUDY_CARD", "VOCAB")))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_ai_order");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void FR001_AT05_concurrentAnswersPersistOnlyOneItemForTheSameNumber() throws Exception {
        Attempt attempt = attempt("PRACTICE", "TOPIC_STUDY");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> insert = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent insert did not start");
                }
                try {
                    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                            items.saveAndFlush(new AttemptItem(attempt.getId(), 1, "STUDY_CARD", "VOCAB")));
                    return true;
                } catch (DataIntegrityViolationException duplicate) {
                    assertThat(duplicate).hasMessageContaining("uq_ai_order");
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(insert);
            Future<Boolean> second = executor.submit(insert);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(items.findByAttemptIdOrderByOrderIndexAsc(attempt.getId())).hasSize(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void FR001_AT13_sessionWriteLockBlocksAnotherTransactionUntilReleased() throws Exception {
        Attempt attempt = attempt("TOPIC_TEST", "TOPIC_TEST");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            transaction.executeWithoutResult(status -> {
                assertThat(attempts.findByIdForUpdate(attempt.getId())).isPresent();
                Future<Boolean> acquired = executor.submit(() -> {
                    try {
                        return transaction.execute(contender -> {
                            jdbc.execute("SET LOCAL lock_timeout = '1s'");
                            return attempts.findByIdForUpdate(attempt.getId()).isPresent();
                        });
                    } catch (PessimisticLockingFailureException locked) {
                        return false;
                    }
                });
                try {
                    assertThat(acquired.get(10, TimeUnit.SECONDS)).isFalse();
                } catch (Exception failure) {
                    throw new IllegalStateException("Concurrent lock check failed", failure);
                }
            });
            Boolean acquiredAfterRelease = transaction.execute(
                    status -> attempts.findByIdForUpdate(attempt.getId()).isPresent());
            assertThat(acquiredAfterRelease).isTrue();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void FR001_lookupIndexesMatchTheSessionAndLearnedWordQueries() {
        assertThat(indexDefinition("uq_ai_order"))
                .contains("UNIQUE", "(attempt_id, order_index)");
        assertThat(indexDefinition("ix_att_user_ref"))
                .contains("(user_id, ref_type, ref_id, status)").doesNotContain("UNIQUE", "WHERE");
        assertThat(indexDefinition("ix_ai_lexeme"))
                .contains("(lexeme_id, attempt_id)", "WHERE (lexeme_id IS NOT NULL)");
    }

    @Test
    void FR002_snapshotRemainsUnchangedWhileDeferredDraftCanBeReplaced() throws Exception {
        Attempt attempt = attempt("TOPIC_TEST", "TOPIC_TEST");
        JsonNode snapshot = attempt.getServedItems().deepCopy();
        attempt.setDraftAnswers(mapper.readTree("{\"1\":{\"answer\":\"B\",\"durationMs\":1000}}"));
        attempt.setDraftSavedAt(SERVED_AT.plusSeconds(10));
        attempts.saveAndFlush(attempt);
        entityManager.clear();

        Attempt loaded = attempts.findById(attempt.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(attempt.getId());
        assertThat(loaded.getUserId()).isEqualTo(attempt.getUserId());
        assertThat(loaded.getKind()).isEqualTo("TOPIC_TEST");
        assertThat(loaded.getRefType()).isEqualTo("TOPIC_TEST");
        assertThat(loaded.getRefId()).isEqualTo(42L);
        assertThat(loaded.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(loaded.getServedAt()).isEqualTo(SERVED_AT);
        assertThat(loaded.getExpiresAt()).isEqualTo(SERVED_AT.plusSeconds(7200));
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getSubmittedAt()).isNull();
        assertThat(loaded.getScore()).isNull();
        assertThat(loaded.getMaxScore()).isNull();
        assertThat(loaded.getDurationMs()).isNull();
        assertThat(loaded.isSuspicious()).isFalse();
        assertThat(loaded.getDraftAnswers().at("/1/answer").asText()).isEqualTo("B");
        assertThat(loaded.getDraftSavedAt()).isEqualTo(SERVED_AT.plusSeconds(10));
        assertThat(loaded.getServedItems()).isEqualTo(snapshot);

        ((ObjectNode) loaded.getServedItems().at("/items/0")).put("answer", "B");
        loaded.setDraftAnswers(mapper.readTree("{\"1\":{\"answer\":\"A\",\"durationMs\":2000}}"));
        loaded.setDraftSavedAt(SERVED_AT.plusSeconds(20));
        attempts.saveAndFlush(loaded);
        entityManager.clear();
        Attempt updated = attempts.findById(attempt.getId()).orElseThrow();
        assertThat(updated.getServedItems()).isEqualTo(snapshot);
        assertThat(updated.getDraftAnswers().at("/1/answer").asText()).isEqualTo("A");
        assertThat(updated.getDraftSavedAt()).isEqualTo(SERVED_AT.plusSeconds(20));
    }

    @Test
    void FR001_serverResultFieldsKeepBigintScoresAndUtcTimestamps() throws Exception {
        Attempt attempt = attempt("GAME", "GAME_ROUND");
        attempt.setGameCode("TEST_GAME");
        attempt.setStatus("SUBMITTED");
        attempt.setSubmittedAt(SERVED_AT.plusSeconds(30));
        attempt.setExpiresAt(SERVED_AT.plusSeconds(60));
        attempt.setScore(3_000_000_000L);
        attempt.setMaxScore(4_000_000_000L);
        attempt.setDurationMs(30_000);
        attempt.setSuspicious(true);
        attempts.saveAndFlush(attempt);
        entityManager.clear();

        Attempt loaded = attempts.findByIdForUpdate(attempt.getId()).orElseThrow();
        assertThat(loaded.getGameCode()).isEqualTo("TEST_GAME");
        assertThat(loaded.getStatus()).isEqualTo("SUBMITTED");
        assertThat(loaded.getSubmittedAt()).isEqualTo(SERVED_AT.plusSeconds(30));
        assertThat(loaded.getExpiresAt()).isEqualTo(SERVED_AT.plusSeconds(60));
        assertThat(loaded.getScore()).isEqualTo(3_000_000_000L);
        assertThat(loaded.getMaxScore()).isEqualTo(4_000_000_000L);
        assertThat(loaded.getDurationMs()).isEqualTo(30_000);
        assertThat(loaded.isSuspicious()).isTrue();
        assertThat(attempts.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    void FR007_recordItemsKeepNullableCorrectnessAndJsonEvidenceInQuestionOrder() throws Exception {
        Attempt attempt = attempt("PRACTICE", "TOPIC_STUDY");
        long lexemeId = jdbc.queryForObject(
                "INSERT INTO learning.lexemes (kind, text, meaning_vi, hsk_level) "
                        + "VALUES ('CHAR', ?, ?, ?) RETURNING id", Long.class, "我", "tôi", 1);
        AttemptItem second = new AttemptItem(attempt.getId(), 2, "STUDY_CARD", "VOCAB");
        second.setLexemeId(lexemeId);
        second.setQuestionId(null);
        second.setAnswer("KNOWN");
        second.setCorrect(null);
        second.setDurationMs(200);
        second.setMeta(Map.of("selfDeclared", true, "suspicious", true,
                "evidence", Map.of("replayCount", 0)));
        items.saveAndFlush(second);
        items.saveAndFlush(new AttemptItem(attempt.getId(), 1, "STUDY_CARD", "VOCAB"));
        entityManager.clear();

        List<AttemptItem> loaded = items.findByAttemptIdOrderByOrderIndexAsc(attempt.getId());
        assertThat(loaded).extracting(AttemptItem::getOrderIndex).containsExactly(1, 2);
        AttemptItem recorded = loaded.get(1);
        assertThat(recorded.getId()).isEqualTo(second.getId());
        assertThat(recorded.getAttemptId()).isEqualTo(attempt.getId());
        assertThat(recorded.getItemType()).isEqualTo("STUDY_CARD");
        assertThat(recorded.getSkill()).isEqualTo("VOCAB");
        assertThat(recorded.getLexemeId()).isEqualTo(lexemeId);
        assertThat(recorded.getQuestionId()).isNull();
        assertThat(recorded.getAnswer()).isEqualTo("KNOWN");
        assertThat(recorded.getCorrect()).isNull();
        assertThat(recorded.getDurationMs()).isEqualTo(200);
        assertThat(recorded.getAnsweredAt()).isNotNull();
        assertThat(recorded.getMeta()).isEqualTo(second.getMeta());
        assertThat(loaded.get(0).getMeta()).isEmpty();
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(-1L)).isEmpty();

        recorded.setMeta(Map.of("selfDeclared", false));
        items.saveAndFlush(recorded);
        entityManager.clear();
        assertThat(items.findById(recorded.getId()).orElseThrow().getMeta()).isEqualTo(second.getMeta());
    }

    @Test
    void FR001_itemMustReferenceAnExistingAttempt() {
        assertThatThrownBy(() -> items.saveAndFlush(new AttemptItem(-1L, 1, "STUDY_CARD", "VOCAB")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String indexDefinition(String name) {
        return jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='learning' AND indexname=?",
                String.class, name);
    }

    private Attempt attempt(String kind, String source) throws Exception {
        String password = UUID.randomUUID() + "Aa1";
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "practice-" + UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password,
                                "displayName", "Người học kiểm thử", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long userId = mapper.readTree(response).path("userId").asLong();
        JsonNode snapshot = mapper.readTree("""
                {"meta":{},"items":[{"no":1,"type":"MEANING_CHOICE","skill":"VOCAB",
                "prompt":{"text":"我"},"options":[{"key":"A","text":"tôi"},{"key":"B","text":"bạn"}],
                "answer":"A","accept":null,"rules":{"minDurationMs":800}}]}
                """);
        Attempt attempt = new Attempt(userId, kind, source, 42L,
                SERVED_AT, SERVED_AT.plusSeconds(7200), snapshot);
        if ("GAME".equals(kind)) {
            attempt.setGameCode("TEST_GAME");
        }
        return attempts.saveAndFlush(attempt);
    }
}
