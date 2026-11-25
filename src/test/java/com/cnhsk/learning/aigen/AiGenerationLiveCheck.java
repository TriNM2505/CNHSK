package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.ai.AnthropicClaudeClient;
import com.cnhsk.learning.ai.ClaudeClient;
import com.cnhsk.learning.ai.DeepSeekChatClient;
import com.cnhsk.learning.aigen.embedding.EmbeddingEngine;
import com.cnhsk.learning.aigen.embedding.EmbeddingProperties;
import com.cnhsk.learning.aigen.embedding.OnnxEmbeddingEngine;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Manual end-to-end check against the real sentence model and the real AI provider, on a throwaway local
 * PostgreSQL (Testcontainers), never on Neon. It is not part of the normal test run: the class name does not
 * match the surefire patterns and it only runs when {@code CNHSK_LIVE_AI=1}. Knowledge data comes from the
 * local data pack folder named by {@code CNHSK_LIVE_DATA}; nothing from it is stored in the repository.
 * The report lists generated questions so a person can judge their quality.
 */
@EnabledIfEnvironmentVariable(named = "CNHSK_LIVE_AI", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiGenerationLiveCheck extends AbstractIntegrationTest {
    private static final List<String> NODES = List.of("CH1-011", "TV1-001", "TV2-008");
    private static final StringBuilder REPORT = new StringBuilder();

    @Autowired AiGenerationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired EmbeddingEngine engine;
    @MockitoSpyBean AiGenerateConfig config;

    @TestConfiguration
    static class LiveBeans {
        @Bean @Primary EmbeddingEngine liveEmbeddingEngine(EmbeddingProperties properties) {
            return new OnnxEmbeddingEngine(properties);
        }

        @Bean @Primary ClaudeClient liveClaudeClient(Environment environment) {
            ClaudeClient real = "anthropic".equals(environment.getProperty("cnhsk.ai.provider"))
                    ? new AnthropicClaudeClient(environment) : new DeepSeekChatClient(environment);
            // Keeps each exchange so the report shows exactly what the model saw and answered.
            return (model, system, messages, maxTokens, timeout) -> {
                var completion = real.complete(model, system, messages, maxTokens, timeout);
                EXCHANGES.add(new String[] {system.get(system.size() - 1).text() + "\n\n"
                        + messages.get(messages.size() - 1).text(), completion.text()});
                return completion;
            };
        }
    }

    private static final List<String[]> EXCHANGES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Paraphrases of real bank stems (must count as the same question) and same-template stems that must not. */
    private static final List<String[]> SAME = List.of(
            new String[] {"Chữ 爱 đọc là:", "Cách đọc đúng của chữ 爱 là gì?"},
            new String[] {"Theo hệ 214 bộ thủ, chữ 爱 thuộc bộ nào?", "Chữ 爱 thuộc bộ thủ nào trong hệ 214 bộ?"},
            new String[] {"Từ 朋友 có nghĩa là gì?", "Nghĩa tiếng Việt của từ 朋友 là:"},
            new String[] {"Chọn từ đúng điền vào chỗ trống: 我___学生。", "Điền từ thích hợp vào chỗ trống: 我___学生。"},
            new String[] {"Câu 你好 dùng khi nào?", "Khi nào thì nói 你好?"},
            new String[] {"Chữ 二 có mấy nét?", "Chữ 二 được viết bằng bao nhiêu nét?"},
            new String[] {"Từ nào có nghĩa là 'bạn bè'?", "Chọn từ mang nghĩa 'bạn bè':"},
            new String[] {"他是老师。Câu này nghĩa là gì?", "Dịch câu sau: 他是老师。"});
    private static final List<String[]> DIFFERENT = List.of(
            new String[] {"Chữ 爱 đọc là:", "Chữ 二 đọc là:"},
            new String[] {"Từ 朋友 có nghĩa là gì?", "Từ 老师 có nghĩa là gì?"},
            new String[] {"Chữ 二 có mấy nét?", "Chữ 爱 có mấy nét?"},
            new String[] {"Theo hệ 214 bộ thủ, chữ 爱 thuộc bộ nào?", "Chữ 爱 đọc là:"},
            new String[] {"Chọn từ đúng điền vào chỗ trống: 我___学生。", "Chọn từ đúng điền vào chỗ trống: 他___老师。"},
            new String[] {"他是老师。Câu này nghĩa là gì?", "我是学生。Câu này nghĩa là gì?"});

    @Test
    @Order(1)
    void sentenceModelSeparatesParaphrasesFromOtherQuestions() throws IOException {
        assertThat(engine.prepare()).as("real sentence model loads").isTrue();
        double threshold = config.semanticThreshold() / 1000.0;
        REPORT.append("# AI generation live check\n\nModel: ").append(engine.modelId())
                .append(", threshold ").append(threshold).append("\n\n## 1. Sentence model on hand-made pairs\n\n")
                .append("| Kind | Cosine | Counted as duplicate | A | B |\n|---|---|---|---|---|\n");
        int sameHit = pairs(SAME, "same meaning", threshold);
        int differentHit = pairs(DIFFERENT, "different", threshold);
        REPORT.append("\nSame meaning flagged: ").append(sameHit).append('/').append(SAME.size())
                .append("; different flagged: ").append(differentHit).append('/').append(DIFFERENT.size())
                .append(" (the answer must also match before a flag drops a draft).\n");

        List<CSVRecord> bank = csv("05-question-bank-hsk.csv");
        REPORT.append("\n## 2. Real bank questions with the same correct answer\n\n")
                .append("| Node | Pairs | Max cosine | Pairs at or above threshold |\n|---|---|---|---|\n");
        for (String kid : NODES) {
            List<CSVRecord> rows = bank.stream().filter(row -> kid.equals(row.get("kid_primary"))).toList();
            List<float[]> vectors = engine.embed(rows.stream().map(row -> row.get("stem")).toList());
            int pairs = 0, over = 0;
            double max = 0;
            List<String> examples = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                for (int j = i + 1; j < rows.size(); j++) {
                    if (!correctText(rows.get(i)).equals(correctText(rows.get(j)))) continue;
                    if (!sameItem(rows.get(i).get("stem"), rows.get(j).get("stem"))) continue;
                    pairs++;
                    double cosine = SemanticIndex.cosine(vectors.get(i), vectors.get(j));
                    max = Math.max(max, cosine);
                    if (cosine >= threshold) {
                        over++;
                        if (examples.size() < 3) examples.add(rows.get(i).get("stem") + " ⇄ " + rows.get(j).get("stem"));
                    }
                }
            }
            REPORT.append("| ").append(kid).append(" | ").append(pairs).append(" | ").append(fmt(max)).append(" | ")
                    .append(over).append(examples.isEmpty() ? "" : " — " + String.join("; ", examples)).append(" |\n");
        }
        write();
    }

    @Test
    @Order(2)
    void realProviderDraftsQuestionsThatPassValidationAndDuplicateChecks() throws Exception {
        long admin = seed();
        doReturn(Math.toIntExact(admin)).when(config).systemUserId();
        REPORT.append("\n## 3. Real AI runs on nodes ").append(String.join(", ", NODES)).append("\n\n")
                .append("Model: ").append(config.getGenerateModel()).append("\n");
        for (int run = 1; run <= 2; run++) {
            var result = service.runOnce();
            REPORT.append("\n### Run ").append(run).append(": ").append(result.outcome()).append(", batches ")
                    .append(result.batches()).append(", completed ").append(result.completed()).append(", failed ")
                    .append(result.failed()).append(", saved ").append(result.questions()).append(", tokens ")
                    .append(result.inputTokens()).append('/').append(result.outputTokens()).append("\n");
        }
        Map<String, Integer> reasons = new LinkedHashMap<>();
        int saved = 0;
        for (Map<String, Object> request : jdbc.queryForList("SELECT id, status, error_message, payload::text AS payload, result::text AS result "
                + "FROM learning.service_requests WHERE kind='AI_GENERATE' AND user_id=? ORDER BY id", admin)) {
            JsonNode payload = mapper.readTree((String) request.get("payload"));
            JsonNode result = request.get("result") == null ? mapper.createObjectNode() : mapper.readTree((String) request.get("result"));
            REPORT.append("\n#### Request ").append(request.get("id")).append(" · ").append(payload.path("kid").asText())
                    .append(" · ").append(request.get("status")).append(request.get("error_message") == null ? "" : " · " + request.get("error_message"))
                    .append("\n\n");
            result.path("dropped").forEach(drop -> reasons.merge(drop.path("reason").asText(), 1, Integer::sum));
            if (result.path("dropped").size() > 0) REPORT.append("Dropped: ").append(result.path("dropped")).append("\n\n");
            for (Map<String, Object> question : jdbc.queryForList("SELECT qtype, stem, options::text AS options, correct_key, status "
                    + "FROM learning.questions WHERE created_by_request_id=? ORDER BY id", request.get("id"))) {
                saved++;
                REPORT.append("- [").append(question.get("qtype")).append(", ").append(question.get("status")).append("] ")
                        .append(question.get("stem")).append("\n");
                for (JsonNode option : mapper.readTree((String) question.get("options"))) {
                    REPORT.append("  - ").append(option.path("key").asText()).append(". ").append(option.path("text").asText())
                            .append(option.path("is_correct").asBoolean() ? " ✔" : "").append("\n");
                }
            }
        }
        REPORT.append("\nSaved for review: ").append(saved).append(". Dropped by reason: ").append(reasons).append("\n");
        REPORT.append("\n## 4. Raw exchanges\n");
        for (String[] exchange : EXCHANGES) {
            REPORT.append("\n### Prompt\n\n```\n").append(exchange[0]).append("\n```\n\n### Answer\n\n```\n")
                    .append(exchange[1]).append("\n```\n");
        }
        write();
        assertThat(saved).as("the real provider produced at least one valid draft").isPositive();
    }

    private int pairs(List<String[]> pairs, String kind, double threshold) {
        int hits = 0;
        for (String[] pair : pairs) {
            List<float[]> vectors = engine.embed(List.of(pair[0], pair[1]));
            double cosine = SemanticIndex.cosine(vectors.get(0), vectors.get(1));
            boolean hit = cosine >= threshold;
            if (hit) hits++;
            REPORT.append("| ").append(kind).append(" | ").append(fmt(cosine)).append(" | ").append(hit ? "yes" : "no")
                    .append(" | ").append(pair[0]).append(" | ").append(pair[1]).append(" |\n");
        }
        return hits;
    }

    /** Loads HSK 1–3 lexemes, the chosen nodes, their word links and approved bank, plus one learner's recent mistakes. */
    private long seed() throws IOException {
        for (CSVRecord row : csv("07-lexeme-hsk1-3-utf8.csv")) {
            jdbc.update("INSERT INTO learning.lexemes(kind,text,pinyin,sino_viet,meaning_vi,hsk_level) VALUES (?,?,?,?,?,?) "
                    + "ON CONFLICT (kind,text) DO NOTHING", row.get("kind"), row.get("text"), blank(row.get("pinyin")),
                    blank(row.get("sino_viet")), row.get("meaning_vi"), Integer.parseInt(row.get("hsk_level")));
        }
        Map<String, Long> ids = new LinkedHashMap<>();
        for (CSVRecord row : csv("01-knowledge-node.csv")) {
            String kid = row.get("kid");
            if (!NODES.contains(kid)) continue;
            ids.put(kid, jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level,can_do) "
                    + "VALUES (?,?,?,?,?,?) RETURNING id", Long.class, kid, row.get("name_vi"), kid.substring(0, 2),
                    row.get("name_zh"), Character.getNumericValue(kid.charAt(2)), row.get("can_do")));
        }
        assertThat(ids.keySet()).containsExactlyInAnyOrderElementsOf(NODES);
        for (CSVRecord row : csv("06-node-link-lexeme.csv")) {
            Long node = ids.get(row.get("kid"));
            if (node != null) jdbc.update("UPDATE learning.lexemes SET knowledge_point_id=? WHERE kind=? AND text=?",
                    node, row.get("target_type"), row.get("target_key"));
        }
        Map<String, Long> firstQuestion = new LinkedHashMap<>();
        for (CSVRecord row : csv("05-question-bank-hsk.csv")) {
            Long node = ids.get(row.get("kid_primary"));
            if (node == null) continue;
            List<Map<String, Object>> options = new ArrayList<>();
            for (String key : List.of("a", "b", "c", "d", "e", "f")) {
                String text = row.get("option_" + key);
                if (!text.isBlank()) options.add(Map.of("key", key.toUpperCase(Locale.ROOT), "text", text,
                        "is_correct", key.equalsIgnoreCase(row.get("correct"))));
            }
            long id = jdbc.queryForObject("INSERT INTO learning.questions(code,qtype,hsk_level,stem,options,correct_key,source,status) "
                    + "VALUES (?,?,?,?,?::jsonb,?,'IMPORT','APPROVED') RETURNING id", Long.class, row.get("code"), row.get("qtype"),
                    Integer.parseInt(row.get("hsk_level")), row.get("stem"), mapper.writeValueAsString(options), row.get("correct"));
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')", id, node);
            firstQuestion.putIfAbsent(row.get("kid_primary"), id);
        }
        long admin = user();
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'ADMIN')", admin);
        long learner = user();
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'LEARNER')", learner);
        Instant now = Instant.now();
        for (long question : firstQuestion.values()) {
            long attempt = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,ref_type,suspicious) "
                    + "VALUES (?,'PRACTICE','TOPIC_TEST',false) RETURNING id", Long.class, learner);
            for (int i = 1; i <= 5; i++) {
                jdbc.update("INSERT INTO learning.attempt_items(attempt_id,question_id,answer,is_correct,order_index,answered_at,item_type,skill) "
                        + "VALUES (?,?,'Z',false,?,?,'QUESTION_CHOICE','READING')", attempt, question, i, Timestamp.from(now));
            }
        }
        return admin;
    }

    private long user() {
        return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'x','Live check') RETURNING id",
                Long.class, "live-" + UUID.randomUUID() + "@example.test");
    }

    /** Mirrors the validator: a short Chinese part is the tested item and must match. */
    private static boolean sameItem(String first, String second) {
        String left = hanzi(first), right = hanzi(second);
        return left.equals(right) || Math.max(left.codePointCount(0, left.length()), right.codePointCount(0, right.length())) > 4;
    }

    private static String hanzi(String text) {
        StringBuilder result = new StringBuilder();
        text.codePoints().filter(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN).forEach(result::appendCodePoint);
        return result.toString();
    }

    private static String correctText(CSVRecord row) {
        return row.get("option_" + row.get("correct").toLowerCase(Locale.ROOT)).strip();
    }

    private static List<CSVRecord> csv(String name) throws IOException {
        String folder = System.getenv("CNHSK_LIVE_DATA");
        assertThat(folder).as("CNHSK_LIVE_DATA names the local data pack folder").isNotBlank();
        String text = Files.readString(Path.of(folder, name), StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        try (Reader reader = new StringReader(text)) {
            return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader).getRecords();
        }
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value; }

    private static String fmt(double value) { return String.format(Locale.ROOT, "%.3f", value); }

    private static void write() throws IOException {
        Path target = Path.of("target", "live-ai-report.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, REPORT, StandardCharsets.UTF_8);
    }
}
