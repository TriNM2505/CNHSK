package com.cnhsk.learning.grading;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.cnhsk.CnhskApplication;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Explicit local-only test-classpath launcher, not an automatically discovered test. */
public final class GradingAcceptanceServer {
    private GradingAcceptanceServer() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        String password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Provide a disposable fixture password on standard input.");
        }
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
                    .withDatabaseName("grading_acceptance").withUsername("fixture_owner")
                    .withPassword(UUID.randomUUID().toString());
             var redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
             var mail = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27"))
                    .withExposedPorts(1025, 8025)) {
            postgres.start();
            redis.start();
            mail.start();
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("spring.profiles.active", "test");
            properties.put("server.address", "127.0.0.1");
            properties.put("server.port", 8080);
            properties.put("debug", false);
            properties.put("logging.level.org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration", "OFF");
            properties.put("spring.devtools.restart.enabled", false);
            properties.put("spring.devtools.livereload.enabled", false);
            properties.put("springdoc.api-docs.enabled", true);
            properties.put("spring.datasource.url", postgres.getJdbcUrl());
            properties.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
            properties.put("spring.datasource.username", postgres.getUsername());
            properties.put("spring.datasource.password", postgres.getPassword());
            properties.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            properties.put("spring.jpa.hibernate.ddl-auto", "validate");
            properties.put("spring.flyway.enabled", true);
            properties.put("spring.flyway.url", postgres.getJdbcUrl());
            properties.put("spring.flyway.user", postgres.getUsername());
            properties.put("spring.flyway.password", postgres.getPassword());
            properties.put("spring.data.redis.host", redis.getHost());
            properties.put("spring.data.redis.port", redis.getMappedPort(6379));
            properties.put("spring.mail.host", mail.getHost());
            properties.put("spring.mail.port", mail.getMappedPort(1025));
            properties.put("JWT_SECRET", UUID.randomUUID().toString() + UUID.randomUUID());
            properties.put("COOKIE_DOMAIN", "127.0.0.1");
            properties.put("COOKIE_SECURE", false);
            properties.put("APP_WEB_BASE_URL", "http://127.0.0.1:5173");
            properties.put("cnhsk.cors.allowed-origins", "http://127.0.0.1:5173");
            properties.put("cnhsk.billing.card-code-pepper", UUID.randomUUID().toString() + UUID.randomUUID());
            properties.put("CNHSK_BOOTSTRAP_ADMIN_EMAIL", "");
            properties.put("CNHSK_BOOTSTRAP_ADMIN_PASSWORD", "");
            environment.getPropertySources().addFirst(new MapPropertySource("gradingAcceptance", properties));
            SpringApplication application = new SpringApplication(CnhskApplication.class);
            application.setEnvironment(environment);
            String testClasses = GradingAcceptanceServer.class.getProtectionDomain().getCodeSource()
                    .getLocation().toExternalForm();
            application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                    "gradingAcceptanceTestFilter", new TypeExcludeFilter() {
                        @Override public boolean match(MetadataReader reader, MetadataReaderFactory factory)
                                throws java.io.IOException {
                            return reader.getResource().getURL().toExternalForm().startsWith(testClasses);
                        }
                        @Override public boolean equals(Object other) { return this == other; }
                        @Override public int hashCode() { return System.identityHashCode(this); }
                    }));
            try (var context = application.run()) {
                if (!(context.getBean(ClaudeClient.class) instanceof StubClaudeClient)) {
                    throw new IllegalStateException("Acceptance requires StubClaudeClient.");
                }
                seed(context.getBean(JdbcTemplate.class), password);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    context.close();
                    mail.stop();
                    redis.stop();
                    postgres.stop();
                }, "grading-acceptance-cleanup"));
                System.out.println("GRADING_ACCEPTANCE_READY bind=127.0.0.1 port=8080 profile=test database=local max_minutes=90");
                new CountDownLatch(1).await(90, TimeUnit.MINUTES);
            }
        }
    }

    private static void seed(JdbcTemplate jdbc, String password) {
        String hash = new BCryptPasswordEncoder(12).encode(password);
        fixtureUser(jdbc, "learner@grading.acceptance.test", hash, "LEARNER", 0);
        fixtureUser(jdbc, "quota@grading.acceptance.test", hash, "LEARNER", 10);
        fixtureUser(jdbc, "teacher-a@grading.acceptance.test", hash, "TEACHER", 0);
        fixtureUser(jdbc, "teacher-b@grading.acceptance.test", hash, "TEACHER", 0);
    }

    private static void fixtureUser(JdbcTemplate jdbc, String email, String hash, String role, int used) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        Timestamp month = Timestamp.from(LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant());
        long id = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name, email_verified_at, free_usage, usage_reset_at)
                VALUES (?, ?, 'Grading acceptance fixture', now(), CAST(? AS jsonb), ?) RETURNING id
                """, Long.class, email, hash, "{\"GRADING\":" + used + "}", month);
        jdbc.update("INSERT INTO auth.user_roles(user_id, role_code) VALUES (?, ?)", id, role);
    }
}
