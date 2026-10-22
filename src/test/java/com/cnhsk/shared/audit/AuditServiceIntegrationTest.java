package com.cnhsk.shared.audit;

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
    }
}
