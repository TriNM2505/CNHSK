package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.ai.AiUnavailableException;
import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TranslationServiceIntegrationTest.TimeConfiguration.class)
class LibraryApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private Clock clock;
    @Autowired private StubClaudeClient stub;
    private TranslationTestData data;
    private final List<Long> users = new ArrayList<>();
    private final List<Long> topics = new ArrayList<>();
    private long user;
    private int calls;
    private int queued;

    @BeforeEach void setup() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        data = new TranslationTestData(jdbc, redis, clock);
        user = user();
        calls = stub.callCount();
        queued = 0;
    }

    @AfterEach void cleanup() {
        try {
            assertThat(stub.callCount() - calls).as("Every queued response was consumed in this test").isEqualTo(queued);
        } finally {
            for (long owner : users) {
                jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", owner);
                jdbc.update("DELETE FROM learning.collection_items WHERE collection_id IN (SELECT id FROM learning.collections WHERE user_id=?)", owner);
                jdbc.update("DELETE FROM learning.collections WHERE user_id=?", owner);
            }
            for (long topic : topics) {
                jdbc.update("DELETE FROM learning.topic_items WHERE topic_id=?", topic);
                jdbc.update("DELETE FROM learning.topics WHERE id=?", topic);
            }
            data.cleanup();
            users.clear();
            topics.clear();
        }
    }

    @Test
    void AT23_AT24_dictionaryAndSaveActionsRoundTripWithoutBilling() throws Exception {
        String word = "自" + new String(Character.toChars(0x24000 + Math.floorMod(UUID.randomUUID().hashCode(), 18000)));
        long lexeme = data.lexeme(word, "zì zuò");
        var search = call(HttpMethod.GET, "/api/public/dictionary/search?type=HANZI&q=" + word, null, null);
        assertThat(search.getStatusCode().value()).isEqualTo(200);
        assertThat(json(search).path("items").get(0).path("id").longValue()).isEqualTo(lexeme);
        assertThat(json(call(HttpMethod.GET, "/api/public/dictionary/lexemes/" + lexeme, null, null))
                .path("meaningVi").textValue()).isEqualTo("Mục tự soạn");
        var note = call(HttpMethod.POST, "/api/notes", Map.of("content", word, "lexemeId", lexeme, "sourceType", "DICTIONARY"), learner());
        assertThat(note.getStatusCode().value()).isEqualTo(201);
        var duplicate = call(HttpMethod.POST, "/api/notes", Map.of("content", "Bản nháp", "lexemeId", lexeme), learner());
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(json(duplicate).path("details").path("noteId").longValue()).isEqualTo(json(note).path("id").longValue());
        long deck = createDeck();
        assertThat(call(HttpMethod.POST, "/api/flashcard-decks/" + deck + "/cards", Map.of("lexemeId", lexeme), learner()).getStatusCode().value()).isEqualTo(201);
        var cardDuplicate = call(HttpMethod.POST, "/api/flashcard-decks/" + deck + "/cards", Map.of("lexemeId", lexeme), learner());
        assertThat(cardDuplicate.getStatusCode().value()).isEqualTo(409);
        assertThat(json(cardDuplicate).path("error_code").textValue()).isEqualTo("CARD_DUPLICATE");
        assertThat(data.used(user)).isZero();
    }

    @Test
    void AT25_translationCachePopupSaveAndRefundUseRealBilling() throws Exception {
        var request = data.uniqueRequest();
        stub.enqueue(data.completion("Bản dịch nối thật tự soạn.")); queued++;
        var first = call(HttpMethod.POST, "/api/translate", request, learner());
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(json(first).path("consumption").textValue()).isEqualTo("QUOTA");
        assertThat(data.used(user)).isEqualTo(1);
        var hit = json(call(HttpMethod.POST, "/api/translate", request, learner()));
        assertThat(hit.path("cached").booleanValue()).isTrue();
        assertThat(hit.path("consumption").isNull()).isTrue();
        assertThat(data.used(user)).isEqualTo(1);
        long lexeme = data.lexeme("译" + new String(Character.toChars(0x25000 + Math.floorMod(UUID.randomUUID().hashCode(), 16000))), "yì");
        assertThat(call(HttpMethod.GET, "/api/public/dictionary/lexemes/" + lexeme, null, learner()).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.POST, "/api/notes", Map.of("content", "Ghi từ popup", "lexemeId", lexeme, "sourceType", "TRANSLATION"), learner()).getStatusCode().value()).isEqualTo(201);
        stub.enqueueFailure(AiUnavailableException.Reason.TIMEOUT); queued++;
        var failed = call(HttpMethod.POST, "/api/translate", data.uniqueRequest(), learner());
        assertThat(failed.getStatusCode().value()).isEqualTo(503);
        assertThat(json(failed).path("message").textValue()).isEqualTo(TranslationService.UNAVAILABLE_MESSAGE);
        assertThat(data.used(user)).isEqualTo(1);
    }

    @Test
    void AT25_insufficientCreditsNeverInvokesTheProvider() throws Exception {
        data.usage(user, 10);
        var response = call(HttpMethod.POST, "/api/translate", data.uniqueRequest(), learner());
        assertThat(response.getStatusCode().value()).isEqualTo(402);
        assertThat(json(response).path("error_code").textValue()).isEqualTo("INSUFFICIENT_CREDITS");
        assertThat(data.used(user)).isEqualTo(10);
    }

    @Test
    void AT26_notesPreservePlainTextAndSupportTrashRestore() throws Exception {
        String content = "Ghi gia đình <script>alert('tự soạn')</script> <b>văn bản</b>";
        var created = call(HttpMethod.POST, "/api/notes", Map.of("content", content, "tags", List.of("tusoan")), learner());
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long id = json(created).path("id").longValue();
        var list = json(call(HttpMethod.GET, "/api/notes?tag=tusoan&page=0&size=20", null, learner()));
        assertThat(list.path("items").get(0).path("snippet").textValue()).contains("<script>", "<b>");
        assertThat(call(HttpMethod.PUT, "/api/notes/" + id, Map.of("content", content + " đã sửa", "tags", List.of("tusoan")), learner()).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.DELETE, "/api/notes/" + id, null, learner()).getStatusCode().value()).isEqualTo(204);
        assertThat(call(HttpMethod.DELETE, "/api/notes/" + id, null, learner()).getStatusCode().value()).isEqualTo(204);
        var trash = json(call(HttpMethod.GET, "/api/notes/trash", null, learner()));
        assertThat(trash.get(0).path("deletedAt").isTextual()).isTrue();
        assertThat(trash.get(0).path("restorableUntil").isTextual()).isTrue();
        assertThat(call(HttpMethod.POST, "/api/notes/" + id + "/restore", null, learner()).getStatusCode().value()).isEqualTo(200);
        assertThat(json(call(HttpMethod.GET, "/api/notes/" + id, null, learner())).path("content").textValue()).isEqualTo(content + " đã sửa");
    }

    @Test
    void AT27_AT28_topicDeckCardsAndFourRatingsKeepMasteryAndAttempts() throws Exception {
        long topic = jdbc.queryForObject("INSERT INTO learning.topics(code,name,hsk_level) VALUES (?,'Chủ đề nối thật tự soạn',1) RETURNING id", Long.class, "T15-" + UUID.randomUUID());
        topics.add(topic);
        for (int index = 0; index < 2; index++) {
            long lexeme = data.lexeme("自作" + UUID.randomUUID(), "zì zuò");
            jdbc.update("INSERT INTO learning.topic_items(topic_id,lexeme_id,order_index) VALUES (?,?,?)", topic, lexeme, index);
        }
        var created = call(HttpMethod.POST, "/api/flashcard-decks", Map.of("name", "Bộ từ chủ đề", "topicId", topic), learner());
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        var deck = json(created);
        assertThat(deck.path("cards").size()).isEqualTo(2);
        long id = deck.path("id").longValue();
        String base = "/api/flashcard-decks/" + id;
        var summary = json(call(HttpMethod.PATCH, base, Map.of("name", "Tên đã sửa"), learner()));
        assertThat(summary.has("cards")).isFalse();
        assertThat(summary.path("cardCount").intValue()).isEqualTo(2);
        var added = call(HttpMethod.POST, base + "/cards", Map.of("front", "Mặt trước tự soạn", "back", "Mặt sau tự soạn"), learner());
        assertThat(added.getStatusCode().value()).isEqualTo(201);
        long manual = json(added).path("id").longValue();
        assertThat(call(HttpMethod.PATCH, base + "/cards/" + manual, Map.of("back", "Chỉ sửa mặt sau"), learner()).getStatusCode().value()).isEqualTo(200);
        assertThat(json(call(HttpMethod.GET, base + "/due?ahead=false", null, learner())).size()).isEqualTo(3);
        var mastery = jdbc.queryForList("SELECT user_id,target_type,target_id,mastery FROM learning.user_progress ORDER BY user_id,target_type,target_id");
        long attempts = jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items", Long.class);
        long card = deck.path("cards").get(0).path("id").longValue();
        for (String rating : List.of("GOOD", "AGAIN", "HARD", "EASY")) {
            var reviewed = call(HttpMethod.POST, "/api/flashcards/" + card + "/review", Map.of("rating", rating), learner());
            assertThat(reviewed.getStatusCode().value()).isEqualTo(200);
            assertThat(json(reviewed).path("cardId").longValue()).isEqualTo(card);
            if (rating.equals("GOOD")) assertThat(json(reviewed).path("intervalDays").intValue()).isEqualTo(3);
        }
        assertThat(jdbc.queryForObject("SELECT lapse_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT user_id,target_type,target_id,mastery FROM learning.user_progress ORDER BY user_id,target_type,target_id")).containsAll(mastery);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? AND mastery<>0", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items", Long.class)).isEqualTo(attempts);
        assertThat(call(HttpMethod.DELETE, base + "/cards/" + manual, null, learner()).getStatusCode().value()).isEqualTo(204);
        long otherCard = deck.path("cards").get(1).path("id").longValue();
        assertThat(call(HttpMethod.POST, "/api/flashcards/" + otherCard + "/review", Map.of("rating", "GOOD"), learner()).getStatusCode().value()).isEqualTo(200);
        assertThat(json(call(HttpMethod.GET, base + "/due?ahead=false", null, learner())).size()).isZero();
        assertThat(json(call(HttpMethod.GET, base + "/due?ahead=true", null, learner())).size()).isEqualTo(2);
        assertThat(call(HttpMethod.DELETE, base, null, learner()).getStatusCode().value()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.collection_items WHERE collection_id=?", Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD'", Integer.class, user)).isEqualTo(2);
    }

    @Test
    void securityUsesRealAuthenticationOwnershipAndCookieCsrf() throws Exception {
        assertThat(call(HttpMethod.GET, "/api/notes", null, null).getStatusCode().value()).isEqualTo(401);
        HttpHeaders administrator = new HttpHeaders();
        administrator.setBearerAuth(jwt.issueAccessToken(user, List.of("ADMIN"), false, UUID.randomUUID()));
        assertThat(call(HttpMethod.GET, "/api/notes", null, administrator).getStatusCode().value()).isEqualTo(403);
        long deck = createDeck();
        HttpHeaders stranger = new HttpHeaders();
        stranger.setBearerAuth(jwt.issueAccessToken(user(), List.of("LEARNER"), false, UUID.randomUUID()));
        assertThat(call(HttpMethod.GET, "/api/flashcard-decks/" + deck, null, stranger).getStatusCode().value()).isEqualTo(403);
        HttpHeaders cookie = new HttpHeaders();
        cookie.add(HttpHeaders.COOKIE, "access_token=" + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID()));
        assertThat(call(HttpMethod.POST, "/api/notes", Map.of("content", "Thử CSRF"), cookie).getStatusCode().value()).isEqualTo(403);
        cookie.add(HttpHeaders.COOKIE, "XSRF-TOKEN=t15-csrf");
        cookie.set("X-XSRF-TOKEN", "t15-csrf");
        assertThat(call(HttpMethod.POST, "/api/notes", Map.of("content", "CSRF hợp lệ"), cookie).getStatusCode().value()).isEqualTo(201);
    }

    private long user() { long id = data.user(0, 0); users.add(id); return id; }
    private HttpHeaders learner() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID()));
        return headers;
    }
    private long createDeck() throws Exception {
        var response = call(HttpMethod.POST, "/api/flashcard-decks", Map.of("name", "Bộ tự soạn"), learner());
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        return json(response).path("id").longValue();
    }
    private ResponseEntity<String> call(HttpMethod method, String path, Object body, HttpHeaders headers) throws Exception {
        HttpHeaders request = headers == null ? new HttpHeaders() : new HttpHeaders(headers);
        request.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body == null ? null : mapper.writeValueAsString(body), request), String.class);
    }
    private JsonNode json(ResponseEntity<String> response) throws Exception { return mapper.readTree(response.getBody()); }
}
