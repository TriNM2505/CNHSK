package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@Import(FlashcardReviewIntegrationTest.TimeConfiguration.class)
class FlashcardReviewIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private StringRedisTemplate redis;
    @Autowired private TestClock clock;
    @Autowired private ApplicationEvents events;
    private long user;
    private long deck;

    @BeforeEach
    void caller() {
        clock.now = Instant.parse("2026-11-06T12:30:00.123456Z");
        user = user();
        deck = deck(user, "FLASHCARD_DECK");
    }

    @Test
    void AT16_120DueCardsReturn100OldestFirstWithStableTies() throws Exception {
        jdbc.update("INSERT INTO learning.collection_items(collection_id,title,content,order_index) SELECT ?,'Thẻ '||n,'Mặt sau',120-n FROM generate_series(1,120) n", deck);
        jdbc.update("""
                INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at,review_count,lapse_count)
                SELECT ?,'FLASHCARD',id,?::timestamptz-order_index*interval '1 minute',5,2
                FROM learning.collection_items WHERE collection_id=?
                """, user, Timestamp.from(clock.now), deck);
        var expected = jdbc.queryForList("SELECT id FROM learning.collection_items WHERE collection_id=? ORDER BY order_index DESC,id LIMIT 100", Long.class, deck);
        JsonNode result = due(null);
        assertThat(result).hasSize(100);
        for (int index = 0; index < 100; index++) assertThat(result.get(index).path("id").asLong()).isEqualTo(expected.get(index));
        assertThat(result.get(0).path("reviewCount").asInt()).isEqualTo(5);
        assertThat(result.get(0).path("lapseCount").asInt()).isEqualTo(2);
    }

    @Test
    void AT16_dueThenNewAndAheadDoesNotTopUpRegularResults() throws Exception {
        long newer = card(deck, 4);
        long older = card(deck, 8);
        long firstNew = card(deck, 1);
        long secondNew = card(deck, 1);
        long future = card(deck, 0);
        progress(user, newer, clock.now.minusSeconds(60));
        progress(user, older, clock.now.minusSeconds(120));
        progress(user, future, clock.now.plusSeconds(60));
        assertIds(due(null), older, newer, firstNew, secondNew);
        assertIds(due("true"), older, newer, firstNew, secondNew);
    }

    @Test
    void AT16_exactDeadlineAndMicrosecondBoundaries() throws Exception {
        long before = card(deck, 1);
        long exact = card(deck, 2);
        long after = card(deck, 0);
        progress(user, before, clock.now.minusNanos(1000));
        progress(user, exact, clock.now);
        progress(user, after, clock.now.plusNanos(1000));
        assertIds(due("false"), before, exact);
        assertIds(due("true"), before, exact);
    }

    @Test
    void AT16_aheadFallbackUsesEarliestFutureAndNeverCreatesProgress() throws Exception {
        long late = card(deck, 0);
        long early = card(deck, 2);
        long tied = card(deck, 3);
        progress(user, late, clock.now.plusSeconds(300));
        progress(user, early, clock.now.plusSeconds(100));
        progress(user, tied, clock.now.plusSeconds(100));
        assertThat(due(null)).isEmpty();
        assertIds(due("true"), early, tied, late);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Integer.class, user)).isEqualTo(3);
        long empty = deck(user, "FLASHCARD_DECK");
        mvc.perform(get("/api/flashcard-decks/{id}/due", empty).param("ahead", "true").header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void AT16_deletedCardsAndDecksStayExcludedEvenWhenProgressRemains() throws Exception {
        long deleted = card(deck, 0);
        progress(user, deleted, clock.now.minusSeconds(30));
        jdbc.update("UPDATE learning.collection_items SET deleted_at=now() WHERE id=?", deleted);
        long fresh = card(deck, 1);
        progress(user(), fresh, clock.now.plusSeconds(3600));
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at) VALUES (?,'LEXEME',?,?)", user, fresh, Timestamp.from(clock.now.plusSeconds(3600)));
        assertIds(due("true"), fresh);
        jdbc.update("UPDATE learning.collections SET deleted_at=now() WHERE id=?", deck);
        mvc.perform(get("/api/flashcard-decks/{id}/due", deck).header("Authorization", auth()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD'", Integer.class, user)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"AGAIN,1", "HARD,1", "GOOD,3", "EASY,16"})
    void AT17_firstUsesSchedulerAndStartsWithoutLapses(String rating, int interval) throws Exception {
        long card = card(deck, 0);
        JsonNode result = review(card, rating);
        assertThat(result.path("cardId").asLong()).isEqualTo(card);
        assertThat(result.path("intervalDays").asInt()).isEqualTo(interval);
        assertThat(Instant.parse(result.path("nextReviewAt").asText())).isEqualTo(clock.now.plus(Duration.ofDays(interval)));
        assertThat(jdbc.queryForObject("SELECT review_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT lapse_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_seen_at FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Timestamp.class, user, card).toInstant()).isEqualTo(clock.now);
    }

    @Test
    void AT17_againAfterFirstIncrementsLapsesAndNoMasteryAttemptsOrEventsChange() throws Exception {
        long card = card(deck, 0);
        long existing = card(deck, 1);
        progress(user, existing, clock.now.minusSeconds(10));
        jdbc.update("UPDATE learning.user_progress SET mastery=0.678,evidence_item_ids=ARRAY[11,22]::bigint[] WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", user, existing);
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,mastery) VALUES (?,'LEXEME',?,0.456)", user, card);
        jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,mastery,mp_state,evidence_item_ids) VALUES (?,'KNOWLEDGE_POINT',?,0.321,'review',ARRAY[33]::bigint[])", user, card);
        var mastery = mastery();
        long attempts = jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items", Long.class);
        String untouched = jdbc.queryForObject("""
                SELECT (to_jsonb(p)-ARRAY['stability','difficulty','next_review_at','review_count','lapse_count','last_seen_at','updated_at'])::text
                FROM learning.user_progress p WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?
                """, String.class, user, existing);
        review(card, "GOOD");
        clock.now = clock.now.plus(Duration.ofDays(3));
        review(card, "AGAIN");
        review(existing, "GOOD");
        assertThat(jdbc.queryForObject("SELECT review_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT lapse_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(1);
        var after = mastery();
        mastery.forEach((key, value) -> assertThat(after.get(key)).isEqualByComparingTo(value));
        assertThat(after.get(user + ":FLASHCARD:" + card)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(after).hasSize(mastery.size() + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items", Long.class)).isEqualTo(attempts);
        assertThat(jdbc.queryForObject("""
                SELECT (to_jsonb(p)-ARRAY['stability','difficulty','next_review_at','review_count','lapse_count','last_seen_at','updated_at'])::text
                FROM learning.user_progress p WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?
                """, String.class, user, existing)).isEqualTo(untouched);
        assertThat(events.stream().map(event -> event instanceof PayloadApplicationEvent<?> payload ? payload.getPayload() : event)
                .filter(event -> event.getClass().getPackageName().startsWith("com.cnhsk.learning")).toList()).isEmpty();
    }

    @Test
    void T12_sameDayEasySequencePersists36500WithoutDatabaseOverflow() throws Exception {
        long card = card(deck, 0);
        JsonNode last = null;
        for (int index = 0; index < 30; index++) last = review(card, "EASY");
        assertThat(jdbc.queryForObject("SELECT stability FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", BigDecimal.class, user, card)).isEqualByComparingTo("36500");
        assertThat(last.path("intervalDays").asInt()).isEqualTo(365);
        assertThat(jdbc.queryForObject("SELECT review_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(30);
    }

    @Test
    void AT17_invalidBodiesDoNotCountButOwnership403CountsOnce() throws Exception {
        long card = card(deck, 0);
        for (String body : List.of("", " ", "{", "null", "[]", "\"GOOD\"", "{}", "{\"rating\":null}",
                "{\"rating\":1}", "{\"rating\":\"PERFECT\"}", "{\"rating\":\"good\"}", "{\"rating\":\"GOOD\"} {}")) {
            mvc.perform(post("/api/flashcards/{id}/review", card).header("Authorization", auth())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        }
        assertThat(redis.opsForValue().get(rateKey())).isNull();
        mvc.perform(post("/api/flashcards/bad/review").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        assertThat(redis.opsForValue().get(rateKey())).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Integer.class, user)).isZero();
    }

    @Test
    void AT17_review500Succeeds501ReturnsExisting429FormatAndNextHourResets() throws Exception {
        long card = card(deck, 0);
        redis.opsForValue().set(rateKey(), "499", Duration.ofMinutes(30));
        review(card, "GOOD");
        mvc.perform(post("/api/flashcards/{id}/review", card).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error_code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(1800)).andExpect(header().doesNotExist("Retry-After"));
        assertThat(jdbc.queryForObject("SELECT review_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(1);
        clock.now = Instant.parse("2026-11-06T13:00:00Z");
        review(card, "GOOD");
        assertThat(redis.opsForValue().get(rateKey())).isEqualTo("1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad", "0", "-1", "9223372036854775808", "9223372036854775807"})
    void privateIdsAre403ForDueAndReview(String id) throws Exception {
        mvc.perform(get("/api/flashcard-decks/{id}/due", id).header("Authorization", auth())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        mvc.perform(post("/api/flashcards/{id}/review", id).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "1", "TRUE", "bad", ""})
    void invalidAheadUsesRepositoryQueryValidation422(String ahead) throws Exception {
        mvc.perform(get("/api/flashcard-decks/{id}/due", deck).param("ahead", ahead).header("Authorization", auth()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void foreignWrongKindAndDeletedResourcesAre403WithoutProgressCreation() throws Exception {
        long foreign = deck(user(), "FLASHCARD_DECK");
        long notebook = deck(user, "NOTEBOOK");
        long deletedDeck = deck(user, "FLASHCARD_DECK");
        jdbc.update("UPDATE learning.collections SET deleted_at=now() WHERE id=?", deletedDeck);
        for (long invalidDeck : new long[] {foreign, notebook, deletedDeck}) {
            long card = card(invalidDeck, 0);
            mvc.perform(get("/api/flashcard-decks/{id}/due", invalidDeck).header("Authorization", auth())).andExpect(status().isForbidden());
            mvc.perform(post("/api/flashcards/{id}/review", card).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("COLLECTION_NOT_OWNED"));
        }
        long deletedCard = card(deck, 0);
        jdbc.update("UPDATE learning.collection_items SET deleted_at=now() WHERE id=?", deletedCard);
        mvc.perform(post("/api/flashcards/{id}/review", deletedCard).header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Integer.class, user)).isZero();
    }

    private JsonNode due(String ahead) throws Exception {
        var request = get("/api/flashcard-decks/{id}/due", deck).header("Authorization", auth());
        if (ahead != null) request.param("ahead", ahead);
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode review(long card, String rating) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/flashcards/{id}/review", card).header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("rating", rating, "mastery", 1, "intervalDays", 999))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private void assertIds(JsonNode result, long... ids) { assertThat(result).hasSize(ids.length); for (int i = 0; i < ids.length; i++) assertThat(result.get(i).path("id").asLong()).isEqualTo(ids[i]); }
    private long user() { return jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử ôn thẻ') RETURNING id", Long.class, UUID.randomUUID() + "@example.test"); }
    private long deck(long owner, String kind) { return jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,?,'Bộ ôn tự soạn') RETURNING id", Long.class, owner, kind); }
    private long card(long parent, int order) { return jdbc.queryForObject("INSERT INTO learning.collection_items(collection_id,title,content,order_index) VALUES (?,'Mặt trước tự soạn','Mặt sau tự soạn',?) RETURNING id", Long.class, parent, order); }
    private void progress(long owner, long card, Instant due) { jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,next_review_at,last_seen_at,review_count,stability,difficulty) VALUES (?,'FLASHCARD',?,?,?,3,10,5)", owner, card, Timestamp.from(due), Timestamp.from(clock.now.minus(Duration.ofDays(3)))); }
    private String auth() { return "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID()); }
    private String rateKey() { return "library:flashcards:review:user:" + user + ":" + DateTimeFormatter.ofPattern("yyyyMMddHH").withZone(ZoneOffset.UTC).format(clock.now); }
    private Map<String, BigDecimal> mastery() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("SELECT user_id,target_type,target_id,mastery FROM learning.user_progress", rs -> {
            result.put(rs.getLong("user_id") + ":" + rs.getString("target_type") + ":" + rs.getLong("target_id"), rs.getBigDecimal("mastery"));
        });
        return result;
    }

    @TestConfiguration
    static class TimeConfiguration { @Bean @Primary TestClock testClock() { return new TestClock(); } }
    static class TestClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-06T12:30:00.123456Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
