package com.cnhsk.learning.library;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.cnhsk.CnhskApplication;
import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.content.text.TextNormalizer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Explicit test-classpath launcher; never discovered as a Surefire/Failsafe test. */
public final class LibraryAcceptanceServer {
    private LibraryAcceptanceServer() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        String password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (password == null || password.length() < 12) throw new IllegalArgumentException("Provide a disposable fixture password on standard input.");
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
                .withDatabaseName("library_acceptance").withUsername("fixture_owner").withPassword(UUID.randomUUID().toString());
             var redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
             var mail = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27")).withExposedPorts(1025, 8025)) {
            postgres.start(); redis.start(); mail.start();
            StandardEnvironment environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            SpringApplication application = new SpringApplication(CnhskApplication.class);
            application.setEnvironment(environment);
            String testClasses = LibraryAcceptanceServer.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
            application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                    "libraryAcceptanceTestFilter", new TypeExcludeFilter() {
                        @Override public boolean match(MetadataReader reader, MetadataReaderFactory factory) throws java.io.IOException {
                            return reader.getResource().getURL().toExternalForm().startsWith(testClasses);
                        }
                        @Override public boolean equals(Object other) { return this == other; }
                        @Override public int hashCode() { return System.identityHashCode(this); }
                    }));
            try (var context = application.run(
                    "--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=8080", "--debug=false",
                    "--spring.devtools.livereload.enabled=false",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.driver-class-name=org.postgresql.Driver",
                    "--spring.datasource.username=" + postgres.getUsername(), "--spring.datasource.password=" + postgres.getPassword(),
                    "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                    "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                    "--spring.flyway.url=" + postgres.getJdbcUrl(), "--spring.flyway.user=" + postgres.getUsername(),
                    "--spring.flyway.password=" + postgres.getPassword(),
                    "--spring.data.redis.host=" + redis.getHost(), "--spring.data.redis.port=" + redis.getMappedPort(6379),
                    "--spring.mail.host=" + mail.getHost(), "--spring.mail.port=" + mail.getMappedPort(1025),
                    "--JWT_SECRET=" + UUID.randomUUID() + UUID.randomUUID(), "--COOKIE_DOMAIN=127.0.0.1", "--COOKIE_SECURE=false",
                    "--APP_WEB_BASE_URL=http://127.0.0.1:5173", "--cnhsk.cors.allowed-origins=http://127.0.0.1:5173",
                    "--cnhsk.billing.card-code-pepper=test-only-acceptance-pepper-not-secret",
                    "--CNHSK_BOOTSTRAP_ADMIN_EMAIL=", "--CNHSK_BOOTSTRAP_ADMIN_PASSWORD=")) {
                if (!(context.getBean(ClaudeClient.class) instanceof StubClaudeClient stub)) {
                    throw new IllegalStateException("Acceptance requires StubClaudeClient.");
                }
                seed(context.getBean(JdbcTemplate.class), password);
                stub.enqueue(new ClaudeClient.Completion("{\"translation\":\"Tôi đọc sách.\"}", 2, 3));
                stub.enqueueFailure(AiUnavailableException.Reason.TIMEOUT);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("ACCEPTANCE_STUB_CALLS=" + stub.callCount());
                    context.close(); mail.stop(); redis.stop(); postgres.stop();
                }, "library-acceptance-cleanup"));
                System.out.println("LIBRARY_ACCEPTANCE_READY bind=127.0.0.1 port=8080 profile=test provider=stub max_minutes=90");
                new CountDownLatch(1).await(90, TimeUnit.MINUTES);
            }
        }
    }

    private static void seed(JdbcTemplate jdbc, String password) {
        String hash = new BCryptPasswordEncoder(12).encode(password);
        long learner = fixtureUser(jdbc, "learner@acceptance.test", hash, 0);
        fixtureUser(jdbc, "quota@acceptance.test", hash, 10);
        long read = word(jdbc, "WORD", "读书", "dú shū", "đọc sách", null, null);
        long family = word(jdbc, "WORD", "家人", "jiā rén", "người nhà", null, null);
        word(jdbc, "CHAR", "日", "rì", "ngày; mặt trời", "日", 4);
        word(jdbc, "CHAR", "口", "kǒu", "miệng", "口", 3);
        long topic = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level) VALUES ('T15-DEMO','Chủ đề tự soạn',1) RETURNING id", Long.class);
        for (int index = 0; index < 2; index++) jdbc.update("INSERT INTO learning.topic_items(topic_id,lexeme_id,order_index) VALUES (?,?,?)", topic, List.of(read, family).get(index), index);
        long notebook = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'NOTEBOOK','Sổ tay nghiệm thu') RETURNING id", Long.class, learner);
        jdbc.update("INSERT INTO learning.collection_items(collection_id,lexeme_id,title,content,source_type) VALUES (?,?,'读书','Ghi chú tự soạn <script>văn bản</script>','DICTIONARY')", notebook, read);
    }

    private static long fixtureUser(JdbcTemplate jdbc, String email, String hash, int used) {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,email_verified_at,free_usage) VALUES (?,?,'Người học nghiệm thu',now(),cast(? AS jsonb)) RETURNING id", Long.class, email, hash, "{\"TRANSLATE\":" + used + "}");
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'LEARNER')", id);
        return id;
    }

    private static long word(JdbcTemplate jdbc, String kind, String text, String pinyin, String meaning, String radical, Integer strokes) {
        return jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,pinyin_norm,meaning_vi,meaning_norm,radical,stroke_count,hsk_level) VALUES (?,?,?,?,?,?,?,?,1) RETURNING id",
                Long.class, kind, text, pinyin, PinyinUtils.normalize(pinyin), meaning, TextNormalizer.normalizeMeaning(meaning), radical, strokes);
    }
}
