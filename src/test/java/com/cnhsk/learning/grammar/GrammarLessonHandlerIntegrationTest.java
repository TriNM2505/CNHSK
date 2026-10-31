package com.cnhsk.learning.grammar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItem;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrammarLessonHandlerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private LexemeRepository lexemes;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository attemptItems;
    @MockitoBean private Clock clock;
    private long other;

    @BeforeAll
    void identitiesThroughAuthApi() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        other = createUser();
    }

    @BeforeEach
    void fixedTime() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void FR016_AT12_fourOfFiveCorrectReportsPercentAndFirstTimeLearnedOnlyOnce() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(5);
        long first = start(learner, point).path("sessionId").asLong();
        for (int no = 2; no <= 6; no++) answer(learner, first, no, no <= 5 ? correctKey(first, no) : wrongKey(first, no));
        JsonNode result = finish(learner, first);
        assertThat(result.path("score").asLong()).isEqualTo(4);
        assertThat(result.path("maxScore").asLong()).isEqualTo(5);
        assertExtra(result, 5, 5, 4, 80, false, true);
        assertThat(result.path("extra").path("percent")).isEqualTo(result.path("percent"));

        long second = start(learner, point).path("sessionId").asLong();
        assertThat(second).isNotEqualTo(first);
        for (int no = 2; no <= 6; no++) answer(learner, second, no, no <= 3 ? correctKey(second, no) : wrongKey(second, no));
        JsonNode again = finish(learner, second);
        assertExtra(again, 5, 5, 2, 40, false, false);
        assertThat(again.path("extra").path("percent")).isEqualTo(again.path("percent"));
        JsonNode row = find(list(learner), point.getId());
        assertThat(row.path("learned").asBoolean()).isTrue();
        assertThat(row.path("bestPercent").asInt()).isEqualTo(80);
    }

    @Test
    void FR015_AT11_answeringOneOfFiveThenFinishingRecordsOnlyThatAnswer() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(5);
        long id = start(learner, point).path("sessionId").asLong();
        answer(learner, id, 2, correctKey(id, 2));
        JsonNode result = finish(learner, id);
        assertThat(result.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(result.path("maxScore").asLong()).isEqualTo(1);
        assertExtra(result, 5, 1, 1, 100, true, true);
        assertThat(result.path("extra").path("percent")).isEqualTo(result.path("percent"));
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(id)).extracting(AttemptItem::getOrderIndex)
                .containsExactly(1, 2);
        Attempt attempt = attempts.findById(id).orElseThrow();
        assertThat(attempt.getStatus()).isEqualTo("SUBMITTED");
        assertThat(attempt.getMaxScore()).isEqualTo(1L);
        assertThat(find(list(learner), point.getId()).path("learned").asBoolean()).isTrue();
    }

    @Test
    void FR015_finishingWithoutAnsweringStillMarksLearnedWithoutPercent() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(3);
        long id = start(learner, point).path("sessionId").asLong();
        JsonNode result = finish(learner, id);
        assertThat(result.path("percent").isNull()).isTrue();
        assertExtra(result, 3, 0, 0, null, true, true);
        assertThat(attempts.findById(id).orElseThrow().getMaxScore()).isZero();
        JsonNode row = find(list(learner), point.getId());
        assertThat(row.path("learned").asBoolean()).isTrue();
        assertThat(row.path("bestPercent").isNull()).isTrue();
    }

    @Test
    void FR015_FR016_skippedChoiceIsRecordedButNotCountedAsAnswered() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(2);
        long id = start(learner, point).path("sessionId").asLong();
        JsonNode skipped = answer(learner, id, skip(2), 200);
        assertThat(skipped.path("counted").asBoolean()).isFalse();
        answer(learner, id, 3, correctKey(id, 3));
        JsonNode result = finish(learner, id);
        assertExtra(result, 2, 1, 1, 100, true, true);
        assertThat(result.path("extra").path("percent")).isEqualTo(result.path("percent"));
        AttemptItem recorded = attemptItems.findByAttemptIdOrderByOrderIndexAsc(id).get(1);
        assertThat(recorded.getOrderIndex()).isEqualTo(2);
        assertThat(recorded.getCorrect()).isNull();
        assertThat(recorded.getMeta()).containsEntry("skipped", true);
    }

    @Test
    void FR013_FR016_lessonWithoutExercisesReportsEmptySummaryAndFirstTimeOnlyOnce() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(0);
        JsonNode finish = start(learner, point).path("finish");
        assertExtra(finish, 0, 0, 0, null, false, true);
        assertThat(start(learner, point).path("finish").path("extra").path("firstTimeLearned").asBoolean()).isFalse();
    }

    @Test
    void FR016_firstTimeLearnedIgnoresUnfinishedOtherUsersAndOtherPoints() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(0);
        Lexeme otherPoint = grammar(0);
        attempt(learner, point, "ABANDONED");
        attempt(learner, point, "EXPIRED");
        attempt(other, point, "SUBMITTED");
        attempt(learner, otherPoint, "SUBMITTED");
        assertThat(start(learner, point).path("finish").path("extra").path("firstTimeLearned").asBoolean()).isTrue();
    }

    @Test
    void FR010_lessonReadCannotBeAnsweredAgainAndClientMetaIsNeverStored() throws Exception {
        long learner = createUser();
        Lexeme point = grammar(1);
        long id = start(learner, point, 2_000).path("sessionId").asLong();
        JsonNode rejected = answer(learner, id, body(1, "UNDERSTOOD", 9_000)
                .set("clientMeta", mapper.createObjectNode().put("readTooFast", false)), 409);
        assertThat(rejected.path("error_code").asText()).isEqualTo("ANSWER_ALREADY_SUBMITTED");
        AttemptItem read = attemptItems.findByAttemptIdOrderByOrderIndexAsc(id).get(0);
        assertThat(read.getDurationMs()).isEqualTo(2_000);
        assertThat(read.getMeta()).containsEntry("readTooFast", true);

        answer(learner, id, body(2, correctKey(id, 2), 1_000)
                .set("clientMeta", mapper.createObjectNode().put("readTooFast", true)), 200);
        AttemptItem choice = attemptItems.findByAttemptIdOrderByOrderIndexAsc(id).get(1);
        assertThat(choice.getMeta()).doesNotContainKey("readTooFast");
        assertThat(attemptItems.findByAttemptIdOrderByOrderIndexAsc(id).get(0).getMeta()).isEqualTo(read.getMeta());
    }

    private void assertExtra(JsonNode result, int total, int answered, int correct, Integer percent,
            boolean skipped, boolean firstTime) {
        JsonNode extra = result.path("extra");
        assertThat(extra.fieldNames()).toIterable().containsExactly("exercisesTotal", "answered", "correct",
                "percent", "skippedExercises", "firstTimeLearned");
        assertThat(extra.path("exercisesTotal").asInt()).isEqualTo(total);
        assertThat(extra.path("answered").asInt()).isEqualTo(answered);
        assertThat(extra.path("correct").asInt()).isEqualTo(correct);
        if (percent == null) {
            assertThat(extra.path("percent").isNull()).isTrue();
        } else {
            assertThat(extra.path("percent").asInt()).isEqualTo(percent);
        }
        assertThat(extra.path("skippedExercises").asBoolean()).isEqualTo(skipped);
        assertThat(extra.path("firstTimeLearned").asBoolean()).isEqualTo(firstTime);
    }

    private long createUser() throws Exception {
        String password = "Aa1" + UUID.randomUUID();
        return mapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password, "displayName", "Handler test user",
                                "acceptTerms", true)))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    private Lexeme grammar(int exerciseCount) {
        var exercises = new ArrayList<LexemeExtra.Exercise>();
        for (int i = 1; i <= exerciseCount; i++) {
            exercises.add(new LexemeExtra.Exercise(i, "Câu " + i + " ___", List.of(
                    new LexemeExtra.Option("A", "sai-a-" + i), new LexemeExtra.Option("B", "đúng-" + i),
                    new LexemeExtra.Option("C", "sai-c-" + i)), "B", "Lời giải " + i));
        }
        Lexeme point = new Lexeme(LexemeKind.GRAMMAR, "把字句" + UUID.randomUUID(), "Câu chữ 把");
        point.setHskLevel((short) 3);
        point.setExtra(new LexemeExtra(List.of(), null, "S + 把 + O + V", "语".repeat(500), null, exercises));
        return lexemes.saveAndFlush(point);
    }

    private void attempt(long userId, Lexeme point, String status) {
        Attempt attempt = new Attempt(userId, "PRACTICE", "GRAMMAR_LESSON", point.getId(),
                NOW.minusSeconds(9000), NOW.minusSeconds(1800), mapper.createObjectNode());
        attempt.setStatus(status);
        if ("SUBMITTED".equals(status)) attempt.setSubmittedAt(NOW.minusSeconds(3600));
        attempts.saveAndFlush(attempt);
    }

    private JsonNode start(long user, Lexeme point) throws Exception {
        return start(user, point, 8_000);
    }

    private JsonNode start(long user, Lexeme point, int readDurationMs) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/grammar/{id}/lessons", point.getId())
                        .header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"readDurationMs\":" + readDurationMs + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String correctKey(long id, int no) {
        return servedItem(id, no).path("answer").asText();
    }

    private String wrongKey(long id, int no) {
        JsonNode item = servedItem(id, no);
        for (JsonNode option : item.path("options")) {
            if (!option.path("key").asText().equals(item.path("answer").asText())) return option.path("key").asText();
        }
        throw new AssertionError("No wrong option for item " + no);
    }

    private JsonNode servedItem(long id, int no) {
        for (JsonNode item : attempts.findById(id).orElseThrow().getServedItems().path("items")) {
            if (item.path("no").asInt() == no) return item;
        }
        throw new AssertionError("Item missing from snapshot: " + no);
    }

    private ObjectNode body(int no, String value, int durationMs) {
        return mapper.createObjectNode().put("no", no).put("answer", value).put("durationMs", durationMs)
                .put("skipped", false).put("audioFailed", false).put("replayCount", 0);
    }

    private ObjectNode skip(int no) {
        return mapper.createObjectNode().put("no", no).put("durationMs", 1_000).put("skipped", true)
                .put("audioFailed", false).put("replayCount", 0);
    }

    private void answer(long user, long id, int no, String value) throws Exception {
        answer(user, id, body(no, value, 1_000), 200);
    }

    private JsonNode answer(long user, long id, JsonNode body, int expectedStatus) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/answers", id)
                        .header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString())).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode finish(long user, long id) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/practice/sessions/{id}/finish", id)
                        .header("Authorization", bearer(user))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode list(long user) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/grammar").header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode find(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("id").asLong() == id) return row;
        throw new AssertionError("Grammar point missing from response: " + id);
    }

    private String bearer(long user) {
        return "Bearer " + jwt.issueAccessToken(user, List.of("LEARNER"), false, UUID.randomUUID());
    }
}
