package com.cnhsk.shared.audit;

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
class AuditServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void FR064_recordPersistsActorTargetAndBeforeAfterDetailToSharedSchema() throws Exception {
        String email = "audit-" + UUID.randomUUID() + "@example.test";
        Long actorId = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) "
                        + "VALUES (?, ?, ?) RETURNING id",
                Long.class,
                email,
                "test-hash",
                "Audit Test");

        auditService.record(actorId, "ROLE_GRANT", "user:42", List.of("LEARNER"), List.of("LEARNER", "TEACHER"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_id, action, target_type, target_id, detail::text AS detail "
                        + "FROM shared.audit_logs WHERE actor_id = ? AND action = ?",
                actorId,
                "ROLE_GRANT");

        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(actorId);
        assertThat(row.get("action")).isEqualTo("ROLE_GRANT");
        assertThat(row.get("target_type")).isEqualTo("user");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(42L);
        JsonNode detail = objectMapper.readTree(row.get("detail").toString());
        assertThat(detail.path("before").get(0).asText()).isEqualTo("LEARNER");
        assertThat(detail.path("after").get(0).asText()).isEqualTo("LEARNER");
        assertThat(detail.path("after").get(1).asText()).isEqualTo("TEACHER");
        assertThat(detail).isEqualTo(objectMapper.valueToTree(Map.of(
                "before", List.of("LEARNER"), "after", List.of("LEARNER", "TEACHER"))));
    }

    @Test
    void VIEW_USER_PII_persistsAndReadsJsonbDetail() throws Exception {
        String email = "audit-pii-" + UUID.randomUUID() + "@example.test";
        Long actorId = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) "
                        + "VALUES (?, ?, ?) RETURNING id",
                Long.class,
                email,
                "test-hash",
                "Audit PII Test");
        Long targetId = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) "
                        + "VALUES (?, ?, ?) RETURNING id",
                Long.class,
                "target-" + UUID.randomUUID() + "@example.test",
                "test-hash",
                "Target Test");

        auditService.record(actorId, "VIEW_USER_PII", "user:" + targetId, null, null);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_id, action, target_type, target_id, detail::text AS detail "
                        + "FROM shared.audit_logs WHERE actor_id = ? AND action = ? AND target_id = ?",
                actorId,
                "VIEW_USER_PII",
                targetId);
        JsonNode detail = objectMapper.readTree(row.get("detail").toString());

        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(actorId);
        assertThat(row.get("action")).isEqualTo("VIEW_USER_PII");
        assertThat(row.get("target_type")).isEqualTo("user");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(targetId);
        assertThat(detail.has("before")).isTrue();
        assertThat(detail.get("before").isNull()).isTrue();
        assertThat(detail.has("after")).isTrue();
        assertThat(detail.get("after").isNull()).isTrue();
        assertThat(detail).isEqualTo(objectMapper.createObjectNode().putNull("before").putNull("after"));
    }

    @Test
    void legacyRecordStillRejectsUppercaseEntityTypesAndOverflowingIds() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> auditService.record(null, "POST_APPROVED", "POST:42", null, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> auditService.record(null, "POST_APPROVED", "post:9223372036854775808", null, null));
    }

    @Test
    void recordDetailPersistsExactUppercaseTargetAndEmptyDetail() throws Exception {
        Long actorId = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "audit-approve-" + UUID.randomUUID() + "@example.test", "test-hash", "Audit Approve");

        auditService.recordDetail(actorId, "POST_APPROVED", "POST", 42L, Map.of());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_id, action, target_type, target_id, detail::text AS detail "
                        + "FROM shared.audit_logs WHERE actor_id = ?", actorId);
        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(actorId);
        assertThat(row.get("action")).isEqualTo("POST_APPROVED");
        assertThat(row.get("target_type")).isEqualTo("POST");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(42L);
        assertThat(objectMapper.readTree(row.get("detail").toString())).isEqualTo(objectMapper.createObjectNode());
    }

    @Test
    void recordDetailPreservesUnicodeReasonAndDirectDetailShape() throws Exception {
        Long actorId = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "audit-reject-" + UUID.randomUUID() + "@example.test", "test-hash", "Audit Reject");
        String reason = "  Nội dung cần sửa: 中文 😀\nGiữ nguyên khoảng trắng.  ";
        Map<String, ?> expectedDetail = Map.of("reason", reason);

        auditService.recordDetail(actorId, "POST_REJECTED", "POST", 43L, expectedDetail);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_id, action, target_type, target_id, detail::text AS detail "
                        + "FROM shared.audit_logs WHERE actor_id = ?", actorId);
        assertThat(((Number) row.get("actor_id")).longValue()).isEqualTo(actorId);
        assertThat(row.get("action")).isEqualTo("POST_REJECTED");
        assertThat(row.get("target_type")).isEqualTo("POST");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(43L);
        assertThat(objectMapper.readTree(row.get("detail").toString()))
                .isEqualTo(objectMapper.valueToTree(expectedDetail));
    }

    @Test
    void recordDetailAcceptsStorageBoundariesAndEmptyActionWithoutTransformation() throws Exception {
        String action = UUID.randomUUID() + "A".repeat(14);
        String targetType = "P" + "O".repeat(27) + "_9";

        auditService.recordDetail(null, action, targetType, Long.MAX_VALUE, Map.of("value", List.of(1, 2)));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT actor_id, action, target_type, target_id, detail::text AS detail "
                        + "FROM shared.audit_logs WHERE action = ?", action);
        assertThat(row.get("actor_id")).isNull();
        assertThat(row.get("action")).isEqualTo(action);
        assertThat(row.get("target_type")).isEqualTo(targetType);
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(Long.MAX_VALUE);
        assertThat(objectMapper.readTree(row.get("detail").toString()))
                .isEqualTo(objectMapper.valueToTree(Map.of("value", List.of(1, 2))));

        Long actorId = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "audit-empty-" + UUID.randomUUID() + "@example.test", "test-hash", "Audit Empty");
        auditService.recordDetail(actorId, "", "POST", 1L, Map.of());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT action FROM shared.audit_logs WHERE actor_id = ?", String.class, actorId)).isEmpty();
    }

    @Test
    void recordDetailRejectsNullRequiredParameters() {
        assertThatNullPointerException().isThrownBy(
                () -> auditService.recordDetail(null, null, "POST", 1L, Map.of()));
        assertThatNullPointerException().isThrownBy(
                () -> auditService.recordDetail(null, "POST_APPROVED", null, 1L, Map.of()));
        assertThatNullPointerException().isThrownBy(
                () -> auditService.recordDetail(null, "POST_APPROVED", "POST", 1L, null));
    }

    @Test
    void recordDetailRejectsInvalidActionTargetTypeAndTargetId() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> auditService.recordDetail(null, "A".repeat(51), "POST", 1L, Map.of()));
        for (String targetType : List.of("", "post", "_POST", "1POST", "POST-ID", " POST", "POST ",
                "P".repeat(31), "POST\n")) {
            assertThatIllegalArgumentException().as("target type %s", targetType).isThrownBy(
                    () -> auditService.recordDetail(null, "POST_APPROVED", targetType, 1L, Map.of()));
        }
        for (long targetId : new long[] {0L, -1L, Long.MIN_VALUE}) {
            assertThatIllegalArgumentException().as("target id %s", targetId).isThrownBy(
                    () -> auditService.recordDetail(null, "POST_APPROVED", "POST", targetId, Map.of()));
        }
    }

    @Test
    void recordDetailJoinsCallerTransactionAndRollsBackWithItsDatabaseChanges() {
        Long actorId = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "audit-rollback-" + UUID.randomUUID() + "@example.test", "test-hash", "Before Audit");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE auth.users SET display_name = ? WHERE id = ?", "After Audit", actorId);
            Long transactionId = jdbcTemplate.queryForObject("SELECT txid_current()", Long.class);

            auditService.recordDetail(actorId, "POST_APPROVED", "POST", 44L, Map.of());

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM shared.audit_logs WHERE actor_id = ?", Long.class, actorId))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT xmin::text::bigint FROM shared.audit_logs WHERE actor_id = ?", Long.class, actorId))
                    .isEqualTo(transactionId);
            throw new IllegalStateException("Roll back the caller transaction");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Roll back the caller transaction");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_name FROM auth.users WHERE id = ?", String.class, actorId)).isEqualTo("Before Audit");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shared.audit_logs WHERE actor_id = ?", Long.class, actorId)).isZero();
    }

    @Test
    void recordDetailWithoutNumericTargetPersistsNullAndNullableMetadata() throws Exception {
        Long actor = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, "audit-batch-" + UUID.randomUUID() + "@example.test", "test-hash", "Batch Audit");
        try {
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("batchId", "B20261107-ABC123");
            metadata.put("value", null);
            metadata.put("planDays", 30);
            metadata.put("totalValue", null);
            auditService.recordDetail(actor, "CARD_BATCH_CREATE", "CARD_BATCH", metadata);
            metadata.put("planDays", 365);
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT target_type,target_id,detail::text AS detail FROM shared.audit_logs WHERE actor_id=?", actor);
            assertThat(row.get("target_type")).isEqualTo("CARD_BATCH");
            assertThat(row.get("target_id")).isNull();
            JsonNode detail = objectMapper.readTree(row.get("detail").toString());
            assertThat(detail.path("planDays").asInt()).isEqualTo(30);
            assertThat(detail.path("value").isNull()).isTrue();
            assertThat(detail.path("totalValue").isNull()).isTrue();
            assertThat(detail.path("batchId").asText()).isEqualTo("B20261107-ABC123");
        } finally {
            jdbcTemplate.update("DELETE FROM shared.audit_logs WHERE actor_id=?", actor);
            jdbcTemplate.update("DELETE FROM auth.users WHERE id=?", actor);
        }
    }

    @Test
    void recordDetailWithoutNumericTargetKeepsRequiredFieldAndStorageValidation() {
        assertThatNullPointerException().isThrownBy(() -> auditService.recordDetail(null, null, "CARD_BATCH", Map.of()));
        assertThatNullPointerException().isThrownBy(() -> auditService.recordDetail(null, "CREATE", null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> auditService.recordDetail(null, "CREATE", "CARD_BATCH", null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> auditService.recordDetail(null, "A".repeat(51), "CARD_BATCH", Map.of()));
        for (String type : List.of("", "card_batch", "_CARD", "1CARD", "CARD-BATCH", "CARD_BATCH\n", "T".repeat(31))) {
            assertThatIllegalArgumentException().isThrownBy(() -> auditService.recordDetail(null, "CREATE", type, Map.of()));
        }
        String action = "A".repeat(50);
        String type = "B".repeat(30);
        try {
            auditService.recordDetail(null, action, type, Map.of());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM shared.audit_logs WHERE actor_id IS NULL AND action=? AND target_type=? AND target_id IS NULL",
                    Long.class, action, type)).isEqualTo(1L);
        } finally {
            jdbcTemplate.update("DELETE FROM shared.audit_logs WHERE actor_id IS NULL AND action=? AND target_type=?", action, type);
        }
    }

    @Test
    void recordDetailWithoutNumericTargetSharesAndRollsBackTheCallerTransaction() {
        Long actor = jdbcTemplate.queryForObject(
                "INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, "audit-tx-" + UUID.randomUUID() + "@example.test", "test-hash", "Before Batch Audit");
        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.update("UPDATE auth.users SET display_name=? WHERE id=?", "After Batch Audit", actor);
                Long txid = jdbcTemplate.queryForObject("SELECT txid_current()", Long.class);
                auditService.recordDetail(actor, "CARD_BATCH_VOID", "CARD_BATCH", Map.of("voided", 0));
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT xmin::text::bigint FROM shared.audit_logs WHERE actor_id=?", Long.class, actor)).isEqualTo(txid);
                throw new IllegalStateException("Roll back batch audit");
            })).isInstanceOf(IllegalStateException.class).hasMessage("Roll back batch audit");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=?", Long.class, actor)).isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT display_name FROM auth.users WHERE id=?", String.class, actor))
                    .isEqualTo("Before Batch Audit");
        } finally {
            jdbcTemplate.update("DELETE FROM shared.audit_logs WHERE actor_id=?", actor);
            jdbcTemplate.update("DELETE FROM auth.users WHERE id=?", actor);
        }
    }
}
