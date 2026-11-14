package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Own infrastructure for global ledger fixtures; never shares the other suites' datasource. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
public abstract class LedgerAdminIntegrationSupport {
    protected static final String BASE = "/api/admin/credit-transactions";
    protected static final Instant AT = Instant.parse("2031-11-14T17:00:00Z");
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("cnhsk_ledger_admin").withUsername("ledger_test_owner").withPassword(UUID.randomUUID().toString());
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    private static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27")).withExposedPorts(1025, 8025);
    static { POSTGRES.start(); REDIS.start(); MAILPIT.start(); }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.mail.host", MAILPIT::getHost);
        properties.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        properties.add("test.mailpit.base-url", () -> "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025));
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JwtService jwt;
    @Autowired protected PlatformTransactionManager manager;
    protected long admin;
    protected final List<Long> ownedUsers = new ArrayList<>();

    @BeforeEach
    protected void createAdministrator() { admin = createUser("Ledger administrator", "ADMIN"); }

    @AfterEach
    protected void removeOwnedFixtures() {
        SecurityContextHolder.clearContext();
        List<Executable> cleanup = new ArrayList<>();
        List<Long> ids = List.copyOf(ownedUsers);
        for (long id : ids) cleanup.add(() -> jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id = ?", id));
        // Delete all owned ledger references before deleting their users (including performed_by).
        for (long id : ids) cleanup.add(() -> jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id = ?", id));
        for (long id : ids) cleanup.add(() -> jdbc.update("DELETE FROM auth.user_roles WHERE user_id = ?", id));
        for (long id : ids) cleanup.add(() -> jdbc.update("DELETE FROM auth.users WHERE id = ?", id));
        for (long id : ids) cleanup.add(() -> {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE id = ?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id = ?", Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id = ?", Long.class, id)).isZero();
        });
        assertAll("Remove only owned fixtures and retain every cleanup failure", cleanup.stream());
        ownedUsers.clear();
    }

    protected long createUser(String name, String... roles) {
        Long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,email_verified_at) VALUES (?,?,?,now()) RETURNING id",
                Long.class, "ledger-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, name);
        ownedUsers.add(id);
        for (String role : roles) jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,?)", id, role);
        return id;
    }

    protected long newUser(int balance) {
        long id = createUser("Ledger learner", "LEARNER");
        if (balance != 0) insertLedger(id, 1, "TOPUP", balance, 0, balance, null, null, AT);
        return id;
    }

    protected String bearer() { return bearer(admin, "ADMIN"); }
    protected String bearer(long userId, String... roles) {
        return "Bearer " + jwt.issueAccessToken(userId, List.of(roles), true, UUID.randomUUID());
    }
    protected void principal() {
        var user = new AuthenticatedUser(admin, Set.of("ADMIN"), true, UUID.randomUUID().toString(), UUID.randomUUID(), Instant.now());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    protected long insertLedger(long userId, long seq, String type, long amount, long before, long after,
            String feature, String reason, Instant createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO learning.credit_transactions(user_id,seq,tx_type,amount,balance_before,balance_after,feature_code,reason,performed_by,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, Long.class, userId, Math.toIntExact(seq), type, Math.toIntExact(amount), Math.toIntExact(before),
                Math.toIntExact(after), feature, reason, "ADJUSTMENT".equals(type) ? admin : null, Timestamp.from(createdAt));
    }

    protected MockHttpServletRequestBuilder filtered(String suffix) {
        return get(BASE + suffix).header("Authorization", bearer()).param("from", "2031-11-15").param("to", "2031-11-15");
    }
    protected MvcResult response(MockHttpServletRequestBuilder request, int status) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as("HTTP status: %s", result.getResponse().getContentAsString()).isEqualTo(status);
        return result;
    }
    protected JsonNode json(MockHttpServletRequestBuilder request, int status) throws Exception {
        return mapper.readTree(response(request, status).getResponse().getContentAsByteArray());
    }
    protected long auditCount() { return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id = ?", Long.class, admin); }
    protected JsonNode audit(String action) throws Exception {
        Map<String, Object> row = jdbc.queryForMap("SELECT target_type,target_id,detail::text AS detail FROM shared.audit_logs WHERE actor_id=? AND action=? ORDER BY id DESC LIMIT 1", admin, action);
        assertThat(row).containsEntry("target_type", "CREDIT_LEDGER").containsEntry("target_id", null);
        return mapper.readTree(row.get("detail").toString());
    }
    protected BusinessSnapshot snapshot() {
        return new BusinessSnapshot(jdbc.queryForList("SELECT * FROM learning.credit_transactions ORDER BY id"),
                jdbc.queryForList("SELECT * FROM auth.users ORDER BY id"), jdbc.queryForList("SELECT * FROM auth.user_roles ORDER BY user_id,role_code"),
                jdbc.queryForList("SELECT * FROM learning.plans ORDER BY code"), jdbc.queryForList("SELECT * FROM learning.credit_cards ORDER BY id"));
    }
    protected record BusinessSnapshot(List<Map<String, Object>> ledger, List<Map<String, Object>> users,
            List<Map<String, Object>> roles, List<Map<String, Object>> plans, List<Map<String, Object>> cards) {}
}
