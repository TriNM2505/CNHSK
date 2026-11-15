package com.cnhsk.shared.settings;

import static com.cnhsk.shared.settings.SettingKey.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.learning.library.LibraryRateLimiter;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler;
import com.cnhsk.learning.quiz.QuizCatalog;
import com.cnhsk.learning.quiz.QuizSessionService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

class SettingsLibraryQuizConsumerIntegrationTest extends SettingsConsumerTestSupport {
    @Autowired private LibraryRateLimiter limiter;
    @Autowired private QuizCatalog catalog;
    @Autowired private QuizSessionService quizzes;
    @Autowired private ApplicationContext context;

    @Test
    void configuredDefaultsRemainAvailableThroughTheRealSettingsBean() {
        for (SettingKey key : KEYS) assertThat(api.getInt(key)).isEqualTo(defaultValue(key));
        assertThat(context.getBean(SettingsApi.class)).isSameAs(settings);
        assertThat(context.getBean(LibraryRateLimiter.class)).isSameAs(limiter);
        assertThat(context.getBean(QuizSessionService.class)).isSameAs(quizzes);
    }

    @Test
    void dictionaryAndReviewChangesAndResetsKeepCountersAndSeparateIdentities() throws Exception {
        String hour = DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC).format(clock.instant());
        String guestKey = "library:dictionary:guest:" + guest + ":" + hour;
        String memberKey = "library:dictionary:user:" + learner + ":" + hour;
        String reviewKey = "library:flashcards:review:user:" + learner + ":" + hour;
        change(LIBRARY_GUEST_LOOKUPS_PER_HOUR, 1);
        change(LIBRARY_AUTHENTICATED_LOOKUPS_PER_HOUR, 1);
        change(LIBRARY_FLASHCARD_REVIEWS_PER_HOUR, 1);
        limiter.check(null, guest);
        limited(() -> limiter.check(null, guest));
        limiter.check(learner, guest);
        limited(() -> limiter.check(learner, guest));
        limiter.checkFlashcardReview(learner);
        limited(() -> limiter.checkFlashcardReview(learner));
        for (String key : List.of(guestKey, memberKey, reviewKey)) assertThat(redis.opsForValue().get(key)).isEqualTo("2");
        change(LIBRARY_GUEST_LOOKUPS_PER_HOUR, 3);
        change(LIBRARY_AUTHENTICATED_LOOKUPS_PER_HOUR, 3);
        change(LIBRARY_FLASHCARD_REVIEWS_PER_HOUR, 3);
        assertThatCode(() -> limiter.check(null, guest)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(learner, guest)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkFlashcardReview(learner)).doesNotThrowAnyException();
        reset(LIBRARY_GUEST_LOOKUPS_PER_HOUR);
        reset(LIBRARY_AUTHENTICATED_LOOKUPS_PER_HOUR);
        reset(LIBRARY_FLASHCARD_REVIEWS_PER_HOUR);
        limiter.check(null, guest);
        limiter.check(learner, guest);
        limiter.checkFlashcardReview(learner);
        for (String key : List.of(guestKey, memberKey, reviewKey)) {
            assertThat(redis.opsForValue().get(key)).isEqualTo("4");
            assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isPositive();
        }
    }

    @Test
    void noteCreateAndRestoreUseTheCurrentLimitWithoutRemovingExistingNotes() throws Exception {
        change(LIBRARY_MAX_NOTES, 2);
        long first = note("Một").path("id").asLong();
        note("Hai");
        change(LIBRARY_MAX_NOTES, 1);
        JsonNode denied = call("POST", "/api/notes", learner, "LEARNER", Map.of("content", "Ba"), 422);
        assertLimit(denied, "NOTES", 1);
        assertThat(call("GET", "/api/notes", learner, "LEARNER", null, 200).path("total").asInt()).isEqualTo(2);
        call("DELETE", "/api/notes/" + first, learner, "LEARNER", null, 204);
        assertLimit(call("POST", "/api/notes/" + first + "/restore", learner, "LEARNER", null, 422), "NOTES", 1);
        change(LIBRARY_MAX_NOTES, 2);
        call("POST", "/api/notes/" + first + "/restore", learner, "LEARNER", null, 200);
        reset(LIBRARY_MAX_NOTES);
        note("Ba");
        assertThat(call("GET", "/api/notes", learner, "LEARNER", null, 200).path("total").asInt()).isEqualTo(3);
    }

    @Test
    void decksCardsAndTopicCopyUseNewLimitsAndResetsWithoutDeletingContent() throws Exception {
        change(LIBRARY_MAX_DECKS, 1);
        change(LIBRARY_MAX_CARDS, 1);
        long deck = deck("Bộ một", null).path("id").asLong();
        assertLimit(call("POST", "/api/flashcard-decks", learner, "LEARNER", Map.of("name", "Bộ hai"), 422), "DECKS", 1);
        card(deck, "Một");
        assertLimit(call("POST", "/api/flashcard-decks/" + deck + "/cards", learner, "LEARNER",
                Map.of("front", "Hai", "back", "Nghĩa hai"), 422), "CARDS", 1);
        change(LIBRARY_MAX_CARDS, 2);
        card(deck, "Hai");
        change(LIBRARY_MAX_CARDS, 1);
        assertThat(call("GET", "/api/flashcard-decks/" + deck, learner, "LEARNER", null, 200).path("cards").size()).isEqualTo(2);
        assertLimit(call("POST", "/api/flashcard-decks/" + deck + "/cards", learner, "LEARNER",
                Map.of("front", "Ba", "back", "Nghĩa ba"), 422), "CARDS", 1);
        change(LIBRARY_MAX_DECKS, 2);
        long topic = topic(0);
        for (int index = 0; index < 4; index++) {
            long lexeme = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi,hsk_level) VALUES ('WORD',?,?,?,1) RETURNING id",
                    Long.class, "字" + UUID.randomUUID(), "zi4", "Từ tự soạn");
            lexemes.add(lexeme);
            jdbc.update("INSERT INTO learning.topic_items(topic_id,lexeme_id,order_index) VALUES (?,?,?)", topic, lexeme, index);
        }
        change(LIBRARY_MAX_CARDS, 3);
        assertThat(deck("Từ chủ đề", topic).path("cards").size()).isEqualTo(3);
        reset(LIBRARY_MAX_CARDS);
        reset(LIBRARY_MAX_DECKS);
        card(deck, "Ba");
        deck("Bộ sau reset", null);
        assertThat(call("GET", "/api/flashcard-decks/" + deck, learner, "LEARNER", null, 200).path("cards").size()).isEqualTo(3);
    }

    @Test
    void questionCountChangesCatalogAndNewStartsWhileResumeKeepsItsSnapshot() throws Exception {
        long topic = topic(12);
        var first = quizzes.start(learner, topic);
        assertThat(first.created()).isTrue();
        assertThat(first.session().items()).hasSize(defaultValue(QUIZ_QUESTION_COUNT));
        var firstStored = quizzes.start(learner, topic).session();
        assertThat(firstStored.items()).isEqualTo(first.session().items());
        change(QUIZ_QUESTION_COUNT, 12);
        assertThat(catalog.list(learner, 1, 0, 50).items()).anySatisfy(item -> {
            assertThat(item.topicId()).isEqualTo(topic);
            assertThat(item.questionCount()).isEqualTo(12);
        });
        var resumed = quizzes.start(learner, topic);
        assertThat(resumed.created()).isFalse();
        assertThat(resumed.session()).isEqualTo(firstStored);
        long secondUser = user();
        var second = quizzes.start(secondUser, topic).session();
        assertThat(second.items()).hasSize(12);
        var secondStored = quizzes.start(secondUser, topic).session();
        change(QUIZ_QUESTION_COUNT, 13);
        assertThat(catalog.list(learner, 1, 0, 50).items()).noneMatch(item -> item.topicId() == topic);
        reset(QUIZ_QUESTION_COUNT);
        assertThat(quizzes.start(secondUser, topic).session()).isEqualTo(secondStored);
        assertThat(quizzes.start(user(), topic).session().items()).hasSize(defaultValue(QUIZ_QUESTION_COUNT));
    }

    @Test
    void bothQuizHandlersReadTheCurrentTimeThresholdIncludingZeroAndValuesAboveYamlRange() throws Exception {
        var items = Collections.nCopies(10, Mockito.mock(PracticeSourceHandler.GradedItem.class));
        change(QUIZ_MIN_SECONDS_PER_QUESTION, 20);
        for (String name : List.of("firstQuizHandler", "retryQuizHandler")) {
            var handler = context.getBean(name, PracticeSourceHandler.class);
            assertThat(handler.isSuspicious(finish(handler.source(), 199, items))).isTrue();
            assertThat(handler.isSuspicious(finish(handler.source(), 200, items))).isFalse();
        }
        change(QUIZ_MIN_SECONDS_PER_QUESTION, 0);
        for (String name : List.of("firstQuizHandler", "retryQuizHandler")) {
            assertThat(context.getBean(name, PracticeSourceHandler.class).isSuspicious(finish(PracticeSource.QUIZ, 0, items))).isFalse();
        }
        reset(QUIZ_MIN_SECONDS_PER_QUESTION);
        assertThat(context.getBean("firstQuizHandler", PracticeSourceHandler.class)
                .isSuspicious(finish(PracticeSource.QUIZ, defaultValue(QUIZ_MIN_SECONDS_PER_QUESTION) * 10L - 1, items))).isTrue();
    }

    private PracticeSourceHandler.FinishContext finish(PracticeSource source, long seconds, List<PracticeSourceHandler.GradedItem> items) {
        return new PracticeSourceHandler.FinishContext(learner, 1, source, 1L, 80L, 100L,
                false, Instant.EPOCH, Instant.EPOCH.plusSeconds(seconds), items);
    }
    private JsonNode note(String content) throws Exception { return call("POST", "/api/notes", learner, "LEARNER", Map.of("content", content), 201); }
    private JsonNode deck(String name, Long topic) throws Exception {
        return call("POST", "/api/flashcard-decks", learner, "LEARNER", topic == null ? Map.of("name", name) : Map.of("name", name, "topicId", topic), 201);
    }
    private void card(long deck, String front) throws Exception { call("POST", "/api/flashcard-decks/" + deck + "/cards", learner, "LEARNER", Map.of("front", front, "back", "Nghĩa " + front), 201); }
    private void assertLimit(JsonNode error, String kind, int limit) {
        assertThat(error.path("error_code").asText()).isEqualTo("LIBRARY_LIMIT_REACHED");
        assertThat(error.path("details").path("kind").asText()).isEqualTo(kind);
        assertThat(error.path("details").path("limit").asInt()).isEqualTo(limit);
    }
    private void limited(Runnable request) {
        assertThatThrownBy(request::run).isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.RATE_LIMITED));
    }
}

/** Real HTTP/settings fixtures shared only by the two consumer integration suites. */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = "cnhsk.assistant.help-location=classpath*:assistant-help/valid/*.md")
abstract class SettingsConsumerTestSupport extends AbstractIntegrationTest {
    static final List<SettingKey> KEYS = Arrays.stream(SettingKey.values()).filter(key ->
            key.key().startsWith("library.") || key.key().startsWith("quiz.") || key.key().startsWith("grading.")).toList();
    private static final String FILTER = "key IN (" + KEYS.stream().map(key -> "?").collect(Collectors.joining(",")) + ")";
    private static final String HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired SettingsApi api;
    @Autowired SettingsService settings;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @Autowired StringRedisTemplate redis;
    @Autowired Clock clock;
    @Autowired Environment environment;
    final List<Long> users = new ArrayList<>();
    final List<Long> topics = new ArrayList<>();
    final List<Long> kids = new ArrayList<>();
    final List<Long> questions = new ArrayList<>();
    final List<Long> lexemes = new ArrayList<>();
    private List<Map<String, Object>> previous;
    long actor;
    long learner;
    long teacher;
    String guest;

    @BeforeEach
    void consumerFixture() {
        previous = jdbc.queryForList("SELECT * FROM shared.app_settings WHERE " + FILTER, KEYS.stream().map(SettingKey::key).toArray());
        jdbc.update("DELETE FROM shared.app_settings WHERE " + FILTER, KEYS.stream().map(SettingKey::key).toArray());
        KEYS.forEach(settings::invalidate);
        actor = user(); learner = user(); teacher = user();
        guest = "fixture-" + UUID.randomUUID();
    }

    @AfterEach
    void restoreConsumerFixture() {
        jdbc.update("DELETE FROM shared.app_settings WHERE " + FILTER, KEYS.stream().map(SettingKey::key).toArray());
        for (var row : previous) jdbc.update("INSERT INTO shared.app_settings(key,value,updated_by,updated_at) VALUES (?,?,?,?)",
                row.get("key"), row.get("value"), row.get("updated_by"), row.get("updated_at"));
        KEYS.forEach(settings::invalidate);
        clear("library:dictionary:guest:" + guest + ":*");
        for (long user : users) jdbc.update("DELETE FROM learning.credit_transactions WHERE user_id=?", user);
        for (long user : users) {
            for (String pattern : List.of("library:dictionary:user:" + user + ":*", "library:flashcards:review:user:" + user + ":*",
                    "billing:*:" + user + ":*", "practice:*:" + user + ":*", "quiz:start:" + user)) clear(pattern);
            jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", user);
            jdbc.update("DELETE FROM learning.service_requests WHERE user_id=?", user);
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id=?)", user);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", user);
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", user);
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", user);
            jdbc.update("DELETE FROM learning.collection_items WHERE collection_id IN (SELECT id FROM learning.collections WHERE user_id=?)", user);
            jdbc.update("DELETE FROM learning.collections WHERE user_id=?", user);
        }
        for (long topic : topics) { jdbc.update("DELETE FROM learning.topic_items WHERE topic_id=?", topic); jdbc.update("DELETE FROM learning.topics WHERE id=?", topic); }
        for (long question : questions) { jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=?", question); jdbc.update("DELETE FROM learning.questions WHERE id=?", question); }
        for (long kid : kids) jdbc.update("DELETE FROM learning.knowledge_points WHERE id=?", kid);
        for (long lexeme : lexemes) jdbc.update("DELETE FROM learning.lexemes WHERE id=?", lexeme);
        for (long user : users) { jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", user); jdbc.update("DELETE FROM auth.users WHERE id=?", user); }
    }

    long user() {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id", Long.class,
                "settings-fixture-" + UUID.randomUUID() + "@example.test", HASH, "Người dùng tự soạn");
        users.add(id);
        return id;
    }
    JsonNode call(String method, String path, long user, String role, Object body, int expected) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + jwt.issueAccessToken(user, List.of(role), true, UUID.randomUUID()));
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        byte[] response = mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray();
        return response.length == 0 ? mapper.createObjectNode() : mapper.readTree(response);
    }
    void change(SettingKey key, int value) throws Exception {
        call("PUT", "/api/admin/settings/" + key.key(), actor, "ADMIN", Map.of("value", value, "reason", "Kiểm thử consumer tự soạn"), 200);
        assertThat(api.getInt(key)).isEqualTo(value);
    }
    void reset(SettingKey key) throws Exception {
        call("DELETE", "/api/admin/settings/" + key.key(), actor, "ADMIN", Map.of("reason", "Khôi phục mặc định"), 200);
        assertThat(api.getInt(key)).isEqualTo(defaultValue(key));
    }
    int defaultValue(SettingKey key) { return Integer.parseInt(environment.getRequiredProperty(key.defaultProperty())); }
    private void clear(String pattern) { var keys = redis.keys(pattern); if (keys != null && !keys.isEmpty()) redis.delete(keys); }
    long topic(int count) {
        String code = jdbc.queryForObject("SELECT 'VB1-' || lpad(n::text,3,'0') FROM generate_series(0,999) n WHERE NOT EXISTS "
                + "(SELECT 1 FROM learning.knowledge_points WHERE code='VB1-' || lpad(n::text,3,'0')) ORDER BY n LIMIT 1", String.class);
        long kid = jdbc.queryForObject("INSERT INTO learning.knowledge_points(code,title,domain,name_zh,hsk_level) VALUES (?,'Bài tự soạn','VB','阅读',1) RETURNING id", Long.class, code);
        kids.add(kid);
        long topic = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level,knowledge_point_id,order_index) VALUES (?,'Chủ đề tự soạn',1,?,-30000) RETURNING id", Long.class, "SETTING-" + UUID.randomUUID(), kid);
        topics.add(topic);
        for (int index = 0; index < count; index++) {
            long question = jdbc.queryForObject("INSERT INTO learning.questions(qtype,hsk_level,stem,options,correct_key,status,code) VALUES ('MULTIPLE_CHOICE',1,'Chọn nghĩa của 水.',?::jsonb,'A','APPROVED',?) RETURNING id",
                    Long.class, "[{\"key\":\"A\",\"text\":\"nước\",\"is_correct\":true},{\"key\":\"B\",\"text\":\"lửa\",\"is_correct\":false}]", "SETTING-" + UUID.randomUUID());
            questions.add(question);
            jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')", question, kid);
        }
        return topic;
    }
}
