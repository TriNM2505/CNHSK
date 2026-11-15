package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** The bulk job has its own database; containers live as long as cached Spring contexts. */
@SpringBootTest
@ActiveProfiles("test")
abstract class PlanMaintenanceIntegrationSupport {
    protected static final Instant NOW = Instant.parse("2035-11-15T17:10:00Z");
    protected static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    protected static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    protected static final Map<String, Integer> BENEFITS = Map.of(
            "AI_GENERATE", 10, "TRANSLATE", 10, "ASSISTANT", 10, "GRADING", 10);
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("cnhsk_plan_maintenance").withUsername("maintenance_test_owner")
            .withPassword(UUID.randomUUID().toString());
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
    static { POSTGRES.start(); REDIS.start(); }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> 6);
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired protected AuthApi auth;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected NamedParameterJdbcTemplate named;
    @Autowired protected PlatformTransactionManager manager;
    @Autowired protected LedgerService ledger;
    @Autowired protected CardCodes codes;
    @Autowired protected RedeemRateLimitService attempts;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected AuditService audit;
    @Autowired protected BillingConfig config;
    @Autowired protected StringRedisTemplate redis;
    protected final List<Long> userIds = new ArrayList<>();
    protected final List<Long> cardIds = new ArrayList<>();
    private final List<String> addedPlans = new ArrayList<>();
    private List<Map<String, Object>> catalogBefore;
    private List<Map<String, Object>> auditBefore;

    @BeforeEach
    void prepareOwnedCatalog() {
        SecurityContextHolder.clearContext();
        catalogBefore = catalogRows();
        auditBefore = jdbc.queryForList("SELECT * FROM shared.audit_logs ORDER BY id");
        for (String code : List.of("PREMIUM", "PREMIUM_PLUS")) {
            int inserted = jdbc.update("""
                    INSERT INTO learning.plans(code,name,description,duration_days,price,benefits,is_active)
                    VALUES (?,?,?,30,120000.00,CAST(? AS jsonb),true) ON CONFLICT(code) DO NOTHING
                    """, code, "Maintenance " + code, "Maintenance fixture", jsonText(BENEFITS));
            if (inserted == 1) addedPlans.add(code);
        }
    }

    @AfterEach
    void removeOwnedFixtures() {
        SecurityContextHolder.clearContext();
        List<Executable> cleanup = new ArrayList<>();
        cleanup.add(() -> {
            var keys = redis.keys("billing:*");
            if (keys != null) {
                var ownedKeys = keys.stream().filter(key -> userIds.stream().anyMatch(id ->
                        key.startsWith("billing:idem:" + id + ":") || key.startsWith("billing:refund:" + id + ":")
                        || key.equals("billing:redeem:fail:user:" + id))).toList();
                if (!ownedKeys.isEmpty()) redis.delete(ownedKeys);
            }
        });
        if (!userIds.isEmpty()) {
            var ids = Map.of("ids", List.copyOf(userIds));
            cleanup.add(() -> named.update("DELETE FROM shared.audit_logs WHERE actor_id IN (:ids)", ids));
            cleanup.add(() -> named.update("DELETE FROM learning.credit_transactions WHERE user_id IN (:ids)", ids));
        }
        if (!cardIds.isEmpty()) cleanup.add(() -> named.update("DELETE FROM learning.credit_cards WHERE id IN (:ids)",
                Map.of("ids", List.copyOf(cardIds))));
        if (!userIds.isEmpty()) {
            var ids = Map.of("ids", List.copyOf(userIds));
            cleanup.add(() -> named.update("DELETE FROM auth.auth_tokens WHERE user_id IN (:ids)", ids));
            cleanup.add(() -> named.update("DELETE FROM auth.user_roles WHERE user_id IN (:ids)", ids));
            cleanup.add(() -> named.update("DELETE FROM auth.users WHERE id IN (:ids)", ids));
            cleanup.add(() -> assertThat(named.queryForObject("SELECT count(*) FROM auth.users WHERE id IN (:ids)",
                    ids, Long.class)).isZero());
        }
        for (var row : catalogBefore) cleanup.add(() -> jdbc.update("""
                INSERT INTO learning.plans(code,name,description,duration_days,price,benefits,is_active,created_at)
                VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?)
                ON CONFLICT(code) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description,
                    duration_days=EXCLUDED.duration_days,price=EXCLUDED.price,benefits=EXCLUDED.benefits,
                    is_active=EXCLUDED.is_active,created_at=EXCLUDED.created_at
                """, row.get("code"), row.get("name"), row.get("description"), row.get("duration_days"),
                row.get("price"), row.get("benefits"), row.get("is_active"), row.get("created_at")));
        for (String code : addedPlans) cleanup.add(() -> jdbc.update("""
                DELETE FROM learning.plans p WHERE p.code=?
                AND NOT EXISTS (SELECT 1 FROM auth.users u WHERE u.plan_code=p.code)
                """, code));
        cleanup.add(() -> assertThat(catalogRows()).isEqualTo(catalogBefore));
        cleanup.add(() -> assertThat(jdbc.queryForList("SELECT * FROM shared.audit_logs ORDER BY id")).isEqualTo(auditBefore));
        assertAll("Restore only owned fixtures and retain every cleanup failure", cleanup.stream());
    }

    protected PlanMaintenance maintenance() { return new PlanMaintenance(auth, FIXED_CLOCK); }

    protected long user(String planCode, Instant expiry) {
        long id = createUser("LEARNER");
        plan(id, planCode, expiry);
        return id;
    }

    protected long createUser(String... roles) {
        long id = jdbc.queryForObject("""
                INSERT INTO auth.users(email,password_hash,display_name,email_verified_at,usage_reset_at)
                VALUES (?,?,'Maintenance fixture',?,?) RETURNING id
                """, Long.class, "maintenance-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH,
                Timestamp.from(NOW), Timestamp.from(NOW));
        userIds.add(id);
        for (String role : roles) jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,?)", id, role);
        return id;
    }

    protected void plan(long id, String code, Instant expiry) {
        jdbc.update("UPDATE auth.users SET plan_code=?,plan_expires_at=? WHERE id=?", code,
                expiry == null ? null : Timestamp.from(expiry), id);
    }

    protected void usage(long id, Map<String, Integer> values, Instant reset) {
        tx(() -> { auth.lockPlan(id); auth.updateUsage(id, values, reset); });
    }

    protected PlanState state(long id) { return auth.readPlan(id).orElseThrow(); }

    protected IssuedCard card(String type, int value, long creator) {
        String raw = codes.generate();
        long id = jdbc.queryForObject("""
                INSERT INTO learning.credit_cards(batch_id,code_hash,card_type,value,plan_days,created_by)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, codes.newBatchId(), codes.hash(raw), type, value,
                "SUBSCRIPTION".equals(type) ? value : null, creator);
        cardIds.add(id);
        return new IssuedCard(id, raw, value);
    }

    protected record IssuedCard(long id, String raw, int value) {}

    protected <T> T tx(Supplier<T> action) { return new TransactionTemplate(manager).execute(status -> action.get()); }
    protected void tx(Runnable action) { new TransactionTemplate(manager).executeWithoutResult(status -> action.run()); }

    protected <T> T asLearner(long id, Supplier<T> action) { return asActor(id, Set.of("LEARNER"), action); }
    protected <T> T asAdmin(long id, Supplier<T> action) { return asActor(id, Set.of("ADMIN"), action); }

    private <T> T asActor(long id, Set<String> roles, Supplier<T> action) {
        var principal = new AuthenticatedUser(id, roles, true, UUID.randomUUID().toString(), UUID.randomUUID(), NOW);
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        try { return action.get(); }
        finally { SecurityContextHolder.setContext(previous); }
    }

    protected String jsonText(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid fixture JSON", exception); }
    }

    protected List<Map<String, Object>> catalogRows() {
        return jdbc.queryForList("""
                SELECT code,name,description,duration_days,price,benefits::text AS benefits,is_active,created_at
                FROM learning.plans ORDER BY code
                """);
    }
}
