package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.auth.service.AdminGuardService;
import com.cnhsk.auth.service.BootstrapAdminRunner;
import com.cnhsk.shared.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "CNHSK_BOOTSTRAP_ADMIN_EMAIL= ",
        "CNHSK_BOOTSTRAP_ADMIN_PASSWORD= "
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminRunnerIntegrationTest {

    private static final String PASSWORD = "BootstrapPass123";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("cnhsk_bootstrap_test")
            .withUsername("cnhsk_bootstrap_test")
            .withPassword("bootstrap-test-only-password");

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private Environment environment;
    @Autowired private AdminGuardService adminGuardService;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AuditService auditService;
    @Autowired private jakarta.validation.Validator validator;
    @Autowired private java.time.Clock clock;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private AuditService auditServiceSpy;

    @BeforeEach
    void isolateBootstrapRowsInDedicatedContainer() {
        jdbcTemplate.update("DELETE FROM shared.audit_logs");
        jdbcTemplate.update("DELETE FROM auth.auth_tokens");
        jdbcTemplate.update("DELETE FROM auth.user_roles");
        jdbcTemplate.update("DELETE FROM auth.users");
    }

    @Test
    void applicationStartsNormallyWhenBothBootstrapVariablesArePresentButBlank() {
        assertThat(environment.getProperty("CNHSK_BOOTSTRAP_ADMIN_EMAIL")).isBlank();
        assertThat(environment.getProperty("CNHSK_BOOTSTRAP_ADMIN_PASSWORD")).isBlank();
        assertThat(userRoleRepository.countByRoleCode(RoleCode.ADMIN)).isZero();
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void createsVerifiedAdminLearnerWithBcryptAndAuditAndIsIdempotent(CapturedOutput output) {
        String email = "  BOOTSTRAP-" + java.util.UUID.randomUUID() + "@Example.test  ";
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        BootstrapAdminRunner runner = runnerWith(email, PASSWORD);

        Instant verificationWindowStart = clock.instant();
        run(runner);
        Instant verificationWindowEnd = clock.instant().plus(1, ChronoUnit.MICROS);

        User user = userRepository.findByEmail(normalizedEmail).orElseThrow();
        assertThat(user.getDisplayName()).isEqualTo("CNHSK Administrator");
        assertThat(user.getEmailVerifiedAt()).isBetween(verificationWindowStart, verificationWindowEnd);
        assertThat(user.getPasswordHash()).startsWith("$2a$12$");
        assertThat(new BCryptPasswordEncoder().matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(user.getId()))
                .extracting(UserRole::getRoleCode)
                .containsExactly(RoleCode.ADMIN, RoleCode.LEARNER);
        assertThat(auditCount(user.getId())).isEqualTo(1);

        run(runner);
        assertThat(userRepository.findByEmail(normalizedEmail))
                .get()
                .extracting(User::getId)
                .isEqualTo(user.getId());
        assertThat(userRoleRepository.countByRoleCode(RoleCode.ADMIN)).isEqualTo(1);
        assertThat(auditCount(user.getId())).isEqualTo(1);
        assertThat(output.getOut() + output.getErr()).doesNotContain(email, PASSWORD, user.getPasswordHash());
    }

    @Test
    void skipsBootstrapWhenAnyAdminRoleAlreadyExists() {
        User existingAdmin = createUser("existing-admin-" + java.util.UUID.randomUUID() + "@example.test");
        userRoleRepository.saveAndFlush(new UserRole(existingAdmin.getId(), RoleCode.ADMIN, null));
        String configuredEmail = "unused-" + java.util.UUID.randomUUID() + "@example.test";

        run(runnerWith(configuredEmail, PASSWORD));

        assertThat(userRepository.findByEmail(configuredEmail)).isEmpty();
        assertThat(userRoleRepository.countByRoleCode(RoleCode.ADMIN)).isEqualTo(1);
        assertThat(auditCount(existingAdmin.getId())).isZero();
    }

    @Test
    void rejectsPartialCredentialsWithoutLoggingValues(CapturedOutput output) {
        String email = "private-" + java.util.UUID.randomUUID() + "@example.test";
        BootstrapAdminRunner runner = runnerWith(email, " ");

        assertThatThrownBy(() -> run(runner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap admin requires both credential variables.");
        assertThat(userRepository.findByEmail(email)).isEmpty();
        assertThat(output.getOut() + output.getErr()).doesNotContain(email);
    }

    @Test
    void rejectsInvalidRegistrationCredentialsWithoutPersistingThem() {
        String email = "invalid-" + java.util.UUID.randomUUID() + "@example.test";
        assertThatThrownBy(() -> run(runnerWith(email, "weakpass")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap admin initialization failed.");
        assertThat(userRepository.findByEmail(email)).isEmpty();
        assertThat(userRoleRepository.countByRoleCode(RoleCode.ADMIN)).isZero();
    }

    @Test
    void existingEmailFailsWithoutPromotingOrChangingItsPassword() {
        String email = "existing-learner-" + java.util.UUID.randomUUID() + "@example.test";
        User existingUser = createUser(email);
        String originalHash = existingUser.getPasswordHash();
        userRoleRepository.saveAndFlush(new UserRole(existingUser.getId(), RoleCode.LEARNER, null));

        assertThatThrownBy(() -> run(runnerWith(email, PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap admin initialization failed.");

        assertThat(userRepository.findByEmail(email).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
        assertThat(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(existingUser.getId()))
                .extracting(UserRole::getRoleCode)
                .containsExactly(RoleCode.LEARNER);
        assertThat(auditCount(existingUser.getId())).isZero();
    }

    @Test
    void concurrentRunnerInvocationsCreateOnlyOneBootstrapAccountAndAudit() throws Exception {
        String email = "concurrent-" + java.util.UUID.randomUUID() + "@example.test";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrentRun(email, ready, start));
            var second = executor.submit(() -> concurrentRun(email, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(userRepository.findAll().stream().filter(candidate -> candidate.getEmail().equals(email)))
                .hasSize(1);
        assertThat(userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(user.getId()))
                .extracting(UserRole::getRoleCode)
                .containsExactly(RoleCode.ADMIN, RoleCode.LEARNER);
        assertThat(auditCount(user.getId())).isEqualTo(1);
    }

    @Test
    void auditFailureRollsBackBootstrapAccountAndRoles() {
        String email = "audit-fail-" + java.util.UUID.randomUUID() + "@example.test";
        doThrow(new IllegalStateException("simulated audit failure"))
                .when(auditServiceSpy)
                .record(isNull(), eq("BOOTSTRAP_ADMIN"), any(), any(), any());

        assertThatThrownBy(() -> run(runnerWith(email, PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap admin initialization failed.");

        assertThat(userRepository.findByEmail(email)).isEmpty();
        assertThat(userRoleRepository.countByRoleCode(RoleCode.ADMIN)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.audit_logs WHERE action = 'BOOTSTRAP_ADMIN'", Long.class)).isZero();
    }

    private void concurrentRun(String email, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent bootstrap.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to start concurrent bootstrap.");
        }
        run(runnerWith(email, PASSWORD));
    }

    private BootstrapAdminRunner runnerWith(String email, String password) {
        Environment testEnvironment = new MockEnvironment()
                .withProperty("CNHSK_BOOTSTRAP_ADMIN_EMAIL", email)
                .withProperty("CNHSK_BOOTSTRAP_ADMIN_PASSWORD", password);
        return new BootstrapAdminRunner(testEnvironment, adminGuardService, userRepository,
                userRoleRepository, auditService, validator, clock, transactionManager);
    }

    private User createUser(String email) {
        return userRepository.saveAndFlush(new User(email, "existing-hash", "Existing User"));
    }

    private long auditCount(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shared.audit_logs "
                        + "WHERE action = 'BOOTSTRAP_ADMIN' AND target_type = 'user' AND target_id = ?",
                Long.class, userId);
    }

    private static void run(BootstrapAdminRunner runner) {
        ApplicationArguments args = new DefaultApplicationArguments(new String[0]);
        runner.run(args);
    }
}
