package com.cnhsk.learning.mastery;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.cnhsk.CnhskApplication;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.ServedSnapshot;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Explicit test-classpath launcher; the class name is not discovered by Surefire or Failsafe. */
public final class MasteryAcceptanceServer {
    private static final String PASSWORD_PROMPT = "Enter disposable fixture password (minimum 12 characters):";

    private MasteryAcceptanceServer() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        System.setProperty("spring.devtools.livereload.enabled", "false");
        System.err.println(PASSWORD_PROMPT);
        String password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Provide a disposable fixture password on standard input.");
        }

        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
                    .withDatabaseName("mastery_acceptance").withUsername("fixture_owner")
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
            SpringApplication application = new SpringApplication(CnhskApplication.class);
            application.setEnvironment(environment);
            String testClasses = MasteryAcceptanceServer.class.getProtectionDomain().getCodeSource()
                    .getLocation().toExternalForm();
            application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                    "masteryAcceptanceTestFilter", new TypeExcludeFilter() {
                        @Override
                        public boolean match(MetadataReader reader, MetadataReaderFactory factory)
                                throws java.io.IOException {
                            return reader.getResource().getURL().toExternalForm().startsWith(testClasses);
                        }

                        @Override public boolean equals(Object other) { return this == other; }
                        @Override public int hashCode() { return System.identityHashCode(this); }
                    }));

            try (var context = application.run(
                    "--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=8080",
                    "--debug=false", "--spring.devtools.restart.enabled=false",
                    "--spring.devtools.livereload.enabled=false", "--springdoc.api-docs.enabled=true",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.driver-class-name=org.postgresql.Driver",
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                    "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                    "--spring.flyway.url=" + postgres.getJdbcUrl(), "--spring.flyway.user=" + postgres.getUsername(),
                    "--spring.flyway.password=" + postgres.getPassword(),
                    "--spring.data.redis.host=" + redis.getHost(),
                    "--spring.data.redis.port=" + redis.getMappedPort(6379),
                    "--spring.mail.host=" + mail.getHost(), "--spring.mail.port=" + mail.getMappedPort(1025),
                    "--JWT_SECRET=" + UUID.randomUUID() + UUID.randomUUID(), "--COOKIE_DOMAIN=127.0.0.1",
                    "--COOKIE_SECURE=false", "--APP_WEB_BASE_URL=http://127.0.0.1:5173",
                    "--cnhsk.cors.allowed-origins=http://127.0.0.1:5173",
                    "--cnhsk.billing.card-code-pepper=" + UUID.randomUUID() + UUID.randomUUID(),
                    "--CNHSK_BOOTSTRAP_ADMIN_EMAIL=", "--CNHSK_BOOTSTRAP_ADMIN_PASSWORD=")) {
                seed(context.getBean(JdbcTemplate.class), context.getBean(ObjectMapper.class), password);
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("MASTERY_ACCEPTANCE_STOPPING");
                    context.close();
                    mail.stop();
                    redis.stop();
                    postgres.stop();
                }, "mastery-acceptance-cleanup"));
                System.out.println("MASTERY_ACCEPTANCE_FIXTURE_USERS=learner-new@mastery.acceptance.test,"
                        + "learner-active@mastery.acceptance.test,learner-review@mastery.acceptance.test");
                System.out.println("MASTERY_ACCEPTANCE_READY bind=127.0.0.1 port=8080 profile=test max_minutes=90");
                new CountDownLatch(1).await(90, TimeUnit.MINUTES);
            }
        } finally {
            password = null;
        }
    }

    private static void seed(JdbcTemplate jdbc, ObjectMapper mapper, String password) throws Exception {
        String hash = new BCryptPasswordEncoder(12).encode(password);
        long newLearner = fixtureUser(jdbc, "learner-new@mastery.acceptance.test", hash);
        long activeLearner = fixtureUser(jdbc, "learner-active@mastery.acceptance.test", hash);
        long reviewLearner = fixtureUser(jdbc, "learner-review@mastery.acceptance.test", hash);

        Map<String, Long> nodes = seedKnowledgeTree(jdbc);
        jdbc.update("UPDATE learning.knowledge_points SET prereq_ids = ARRAY[?]::bigint[] WHERE code = ?",
                nodes.get("TV1-901"), "TV1-902");
        Map<String, Long> questions = seedQuestions(jdbc, nodes);
        seedMastery(jdbc, mapper, activeLearner, nodes, questions);
        seedReviewSchedules(jdbc, reviewLearner);

        // The new learner intentionally has no user_progress or attempts rows.
        if (jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id = ?", Integer.class,
                newLearner) != 0) {
            throw new IllegalStateException("New-learner fixture unexpectedly has progress rows.");
        }
    }

    private static long fixtureUser(JdbcTemplate jdbc, String email, String passwordHash) {
        long id = jdbc.queryForObject("""
                INSERT INTO auth.users(email, password_hash, display_name, email_verified_at)
                VALUES (?, ?, ?, now()) RETURNING id
                """, Long.class, email, passwordHash, "Học viên nghiệm thu tự soạn");
        jdbc.update("INSERT INTO auth.user_roles(user_id, role_code) VALUES (?, 'LEARNER')", id);
        return id;
    }

    private static Map<String, Long> seedKnowledgeTree(JdbcTemplate jdbc) {
        Map<String, Long> ids = new LinkedHashMap<>();
        List<NodeFixture> fixtures = List.of(
                new NodeFixture("TV1-901", "TV", 1, "Từ vựng thử A"),
                new NodeFixture("TV1-902", "TV", 1, "Từ vựng thử B"),
                new NodeFixture("TV1-903", "TV", 1, "Từ vựng thử C"),
                new NodeFixture("TV1-904", "TV", 1, "Từ vựng thử D"),
                new NodeFixture("TV1-905", "TV", 1, "Từ vựng thử E"),
                new NodeFixture("TV1-906", "TV", 1, "Từ vựng thử F"),
                new NodeFixture("TV1-907", "TV", 1, "Từ vựng thử G"),
                new NodeFixture("TV1-908", "TV", 1, "Từ vựng thử H"),
                new NodeFixture("CH2-901", "CH", 2, "Chữ thử I"),
                new NodeFixture("NP3-901", "NP", 3, "Ngữ pháp thử J"),
                new NodeFixture("VB1-901", "VB", 1, "Biểu đạt thử K"));
        for (NodeFixture node : fixtures) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO learning.knowledge_points(code, title, skill_type, hsk_level, domain, name_zh, can_do)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, node.code(), node.title(), skillFor(node.domain()), node.level(), node.domain(),
                    "自造測試", "Nội dung minh họa do nhóm tự soạn.");
            ids.put(node.code(), id);
        }
        return Map.copyOf(ids);
    }

    private static String skillFor(String domain) {
        return switch (domain) {
            case "CH" -> "CHARACTER";
            case "NP" -> "GRAMMAR";
            case "VB" -> "WRITING";
            default -> "VOCAB";
        };
    }

    private static Map<String, Long> seedQuestions(JdbcTemplate jdbc, Map<String, Long> nodes) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (String kid : nodes.keySet()) {
            if (kid.equals("TV1-903")) continue; // Deliberately has no approved question.
            long id = jdbc.queryForObject("""
                    INSERT INTO learning.questions(qtype, hsk_level, stem, options, correct_key,
                            source, status, reusable, code)
                    VALUES ('MULTIPLE_CHOICE', ?, ?, ?::jsonb, 'A', 'MANUAL', 'APPROVED', true, ?)
                    RETURNING id
                    """, Long.class, levelFromKid(kid), "Câu hỏi minh họa tự soạn cho " + kid,
                    questionOptionsJson(), "MASTERY-ACCEPTANCE-" + kid);
            jdbc.update("""
                    INSERT INTO learning.question_knowledge_points(question_id, knowledge_point_id, role)
                    VALUES (?, ?, 'PRIMARY')
                    """, id, nodes.get(kid));
            ids.put(kid, id);
        }
        return Map.copyOf(ids);
    }

    private static short levelFromKid(String kid) {
        return Short.parseShort(kid.substring(2, 3));
    }

    private static String questionOptionsJson() {
        return "[{\"key\":\"A\",\"text\":\"Đáp án tự soạn\",\"is_correct\":true},"
                + "{\"key\":\"B\",\"text\":\"Lựa chọn tự soạn B\",\"is_correct\":false},"
                + "{\"key\":\"C\",\"text\":\"Lựa chọn tự soạn C\",\"is_correct\":false},"
                + "{\"key\":\"D\",\"text\":\"Lựa chọn tự soạn D\",\"is_correct\":false}]";
    }

    private static void seedMastery(JdbcTemplate jdbc, ObjectMapper mapper, long userId,
            Map<String, Long> nodes, Map<String, Long> questions) throws Exception {
        Instant now = Instant.now();
        addProgress(jdbc, mapper, userId, nodes.get("TV1-902"), "extend", "MOCK_EXAM",
                questions.get("TV1-902"), new boolean[] {true, true, true}, now, true);
        addProgress(jdbc, mapper, userId, nodes.get("TV1-904"), "insufficient", "PLACEMENT",
                questions.get("TV1-904"), new boolean[] {false}, now, false);
        addProgress(jdbc, mapper, userId, nodes.get("TV1-905"), "review", "PLACEMENT",
                questions.get("TV1-905"), new boolean[] {false, false, false}, now, false);
        addProgress(jdbc, mapper, userId, nodes.get("TV1-906"), "consolidate", "PLACEMENT",
                questions.get("TV1-906"), new boolean[] {true, true, false}, now, false);
        addProgress(jdbc, mapper, userId, nodes.get("TV1-907"), "extend", "PLACEMENT",
                questions.get("TV1-907"), new boolean[] {true, true, true}, now, false);
    }

    private static void addProgress(JdbcTemplate jdbc, ObjectMapper mapper, long userId, long nodeId,
            String savedState, String source, long questionId, boolean[] answers, Instant now,
            boolean practiceExam) throws Exception {
        List<Long> evidenceIds = new ArrayList<>();
        for (int index = 0; index < answers.length; index++) {
            Instant answeredAt = now.minusSeconds(index * 86_400L);
            boolean correct = answers[index];
            String snapshot = mapper.writeValueAsString(new ServedSnapshot(Map.of(), List.of(
                    new ServedItem(1, ItemType.MEANING_CHOICE, Skill.VOCAB, null,
                            new ServedItem.Prompt("Fixture question", null, null, null, null, null, null,
                                    List.of(), null),
                            fixtureOptions(), "A", null, null, new ServedItem.Rules(0, 0, false), questionId))));
            String kind = practiceExam ? "EXAM" : "QUIZ";
            Long attemptId = jdbc.queryForObject("""
                    INSERT INTO learning.attempts(user_id, kind, ref_type, status, served_at, submitted_at,
                            score, max_score, suspicious, served_items)
                    VALUES (?, ?, ?, 'SUBMITTED', ?, ?, 0, 100, false, ?::jsonb)
                    RETURNING id
                    """, Long.class, userId, kind, source, java.sql.Timestamp.from(answeredAt),
                    java.sql.Timestamp.from(answeredAt), snapshot);
            Long itemId = jdbc.queryForObject("""
                    INSERT INTO learning.attempt_items(attempt_id, question_id, answer, is_correct,
                            duration_ms, order_index, answered_at, item_type, skill, meta)
                    VALUES (?, ?, ?, ?, 500, 1, ?, 'MEANING_CHOICE', 'VOCAB', '{}'::jsonb)
                    RETURNING id
                    """, Long.class, attemptId, questionId, correct ? "A" : "B", correct,
                    java.sql.Timestamp.from(answeredAt));
            evidenceIds.add(itemId);
        }
        String arrayValues = String.join(",", java.util.Collections.nCopies(evidenceIds.size(), "?"));
        String sql = """
                INSERT INTO learning.user_progress(user_id, target_type, target_id, mp_state, confidence,
                        evidence_item_ids, next_review_at)
                VALUES (?, 'KNOWLEDGE_POINT', ?, ?, 2.000, ARRAY[%s]::bigint[], ?)
                """.formatted(arrayValues);
        List<Object> arguments = new ArrayList<>();
        arguments.add(userId);
        arguments.add(nodeId);
        arguments.add(savedState);
        evidenceIds.forEach(arguments::add);
        arguments.add(java.sql.Timestamp.from(savedState.equals("consolidate")
                ? now.minusSeconds(3_600) : now.plusSeconds(86_400)));
        jdbc.update(sql, arguments.toArray());
    }

    private static List<ServedItem.Option> fixtureOptions() {
        return List.of(new ServedItem.Option("A", "Đáp án tự soạn"),
                new ServedItem.Option("B", "Lựa chọn tự soạn B"),
                new ServedItem.Option("C", "Lựa chọn tự soạn C"),
                new ServedItem.Option("D", "Lựa chọn tự soạn D"));
    }

    private static void seedReviewSchedules(JdbcTemplate jdbc, long userId) {
        Instant now = Instant.now();
        long dueWord = lexeme(jdbc, "WORD", "自造詞甲", "Nghĩa từ tự soạn A");
        lexeme(jdbc, "WORD", "自造詞乙", "Nghĩa từ tự soạn B");
        lexeme(jdbc, "WORD", "自造詞丙", "Nghĩa từ tự soạn C");
        lexeme(jdbc, "WORD", "自造詞丁", "Nghĩa từ tự soạn D");
        long futureWord = lexeme(jdbc, "WORD", "自造詞戊", "Nghĩa từ tự soạn E");
        long dueGrammar = lexeme(jdbc, "GRAMMAR", "自造句型", "Mẫu câu tự soạn");
        schedule(jdbc, userId, dueWord, now.minusSeconds(86_400));
        schedule(jdbc, userId, dueGrammar, now.minusSeconds(43_200));
        schedule(jdbc, userId, futureWord, now.plusSeconds(172_800));
    }

    private static long lexeme(JdbcTemplate jdbc, String kind, String text, String meaning) {
        return jdbc.queryForObject("""
                INSERT INTO learning.lexemes(kind, text, meaning_vi, hsk_level)
                VALUES (?, ?, ?, 1) RETURNING id
                """, Long.class, kind, text, meaning);
    }

    private static void schedule(JdbcTemplate jdbc, long userId, long lexemeId, Instant nextReviewAt) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO learning.user_progress(user_id, target_type, target_id, next_review_at,
                        stability, difficulty, last_seen_at)
                VALUES (?, 'LEXEME', ?, ?, 2.000, 5.00, ?)
                """, userId, lexemeId, java.sql.Timestamp.from(nextReviewAt), java.sql.Timestamp.from(now));
    }

    private record NodeFixture(String code, String domain, int level, String title) { }
}
