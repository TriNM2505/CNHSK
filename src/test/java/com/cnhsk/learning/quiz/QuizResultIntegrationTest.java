package com.cnhsk.learning.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.leaderboard.LeaderboardService;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

class QuizResultIntegrationTest extends QuizIntegrationFixture {
    @Autowired QuizResultService results;
    @Autowired ObjectProvider<LeaderboardService> leaderboards;
    @Autowired EntityManagerFactory factory;

    @Test
    void noLeaderboardBeanStillReturnsCompleteResultAndFirstSummaryWithNullRank() throws Exception {
        assertThat(leaderboards.getIfAvailable()).isNull();
        Topic topic = topic(10, 0);
        long id = sessions.start(user, topic.id()).session().sessionId();
        advance(20);
        practice.finish(user, id, answers(8));
        JsonNode result = mapper.readTree(mvc.perform(get(path(id)).header("Authorization", bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(result.fieldNames()).toIterable().containsExactlyInAnyOrder("sessionId", "topicId", "topicName",
                "score", "correctCount", "wrongCount", "durationMs", "rankingEligible", "suspicious", "items", "firstAttempt");
        assertThat(result.path("score").asLong()).isEqualTo(80);
        assertThat(result.path("correctCount").asInt()).isEqualTo(8);
        assertThat(result.path("wrongCount").asInt()).isEqualTo(2);
        assertThat(result.path("durationMs").asInt()).isEqualTo(20000);
        assertThat(result.path("rankingEligible").asBoolean()).isTrue();
        assertThat(result.path("suspicious").asBoolean()).isFalse();
        assertThat(result.path("topicId").asLong()).isEqualTo(topic.id());
        assertThat(result.path("firstAttempt").path("score").asLong()).isEqualTo(80);
        assertThat(result.path("firstAttempt").path("durationMs").asInt()).isEqualTo(20000);
        assertThat(result.path("firstAttempt").get("rank").isNull()).isTrue();
        assertThat(result.path("firstAttempt").get("total").isNull()).isTrue();
        JsonNode item = result.path("items").get(0);
        assertThat(item.fieldNames()).toIterable().containsExactlyInAnyOrder("itemId", "prompt", "options", "chosen", "correct", "explanation");
        assertThat(item.path("itemId").asInt()).isEqualTo(1);
        assertThat(item.path("correct").asText()).isEqualTo("A");
        assertThat(item.path("chosen").asText()).isEqualTo("A");
        assertThat(item.get("explanation").isNull()).isTrue();
        assertThat(item.path("options").get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("key", "text");
        assertThat(jdbc.queryForObject("SELECT duration_ms FROM learning.attempts WHERE id=?", Integer.class, id)).isEqualTo(20000);
    }

    @Test
    void resultUsesSnapshotAfterQuestionsAreChangedAndArchivedAndConfigChanges() throws Exception {
        Topic topic = topic(10, 0);
        var session = sessions.start(user, topic.id()).session();
        advance(20);
        practice.finish(user, session.sessionId(), answers(8));
        jdbc.update("UPDATE learning.questions SET stem='Đề đã đổi', explanation='Giải thích đã đổi', "
                + "options='[{\"key\":\"A\",\"text\":\"đổi\",\"is_correct\":false},"
                + "{\"key\":\"B\",\"text\":\"mới\",\"is_correct\":true}]'::jsonb,correct_key='B',status='ARCHIVED' "
                + "WHERE id IN (SELECT question_id FROM learning.question_knowledge_points WHERE knowledge_point_id=?)", topic.kid());
        config.setQuestionCount(30);
        var result = results.result(user, session.sessionId());
        assertThat(result.score()).isEqualTo(80);
        assertThat(result.correctCount()).isEqualTo(8);
        assertThat(result.wrongCount()).isEqualTo(2);
        assertThat(result.items()).hasSize(10).allSatisfy(item -> {
            assertThat(item.prompt()).isEqualTo("Chọn nghĩa của 水.");
            assertThat(item.correct()).isEqualTo("A");
            assertThat(item.explanation()).isNull();
            assertThat(item.options()).extracting(QuizSessionService.QuizOption::text).containsExactly("nước", "lửa");
        });
    }

    @Test
    void skippedAndUnansweredItemsHaveNullChoiceAndCountAsWrong() {
        Topic topic = topic(10, 0);
        long id = sessions.start(user, topic.id()).session().sessionId();
        var answers = new ArrayList<>(answers(8));
        answers.set(8, new Answer(9, "B", 0, 0, true, false, null));
        answers.remove(9);
        practice.finish(user, id, answers);
        var result = results.result(user, id);
        assertThat(result.correctCount()).isEqualTo(8);
        assertThat(result.wrongCount()).isEqualTo(2);
        assertThat(result.items().get(8).chosen()).isNull();
        assertThat(result.items().get(9).chosen()).isNull();
    }

    @Test
    void retryShowsCurrentScoreButKeepsSuspiciousFirstScoreAndDuration() {
        Topic topic = topic(10, 0);
        long first = sessions.start(user, topic.id()).session().sessionId();
        advance(12);
        practice.finish(user, first, answers(8));
        long retry = sessions.start(user, topic.id()).session().sessionId();
        advance(32);
        practice.finish(user, retry, answers(10));
        var result = results.result(user, retry);
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.suspicious()).isFalse();
        assertThat(result.rankingEligible()).isFalse();
        assertThat(result.firstAttempt()).isEqualTo(new QuizResultService.FirstAttemptRank(80, 12000, null, null));
        var original = results.result(user, first);
        assertThat(original.rankingEligible()).isTrue();
        assertThat(original.suspicious()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"IN_PROGRESS", "ABANDONED", "EXPIRED"})
    void quizNotSubmittedReturns422WithNoAnswerFields(String state) throws Exception {
        Topic topic = topic(10, 0);
        long id = sessions.start(user, topic.id()).session().sessionId();
        if (state.equals("ABANDONED")) practice.abandon(user, id);
        if (state.equals("EXPIRED")) {
            advance(1801);
            practice.view(user, id);
        }
        String response = mvc.perform(get(path(id)).header("Authorization", bearer()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("\"items\"", "\"correct\"", "\"explanation\"");
    }

    @Test
    void missingAndForeignAttemptsReturn403BeforeNonQuiz404() throws Exception {
        Topic topic = topic(10, 0);
        long own = practice.start(new StartPracticeCommand(user, PracticeSource.TOPIC_PRACTICE, topic.id(), null,
                picker.pick(topic.id(), 10), List.of(), null, Map.of())).sessionId();
        mvc.perform(get(path(own)).header("Authorization", bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("QUIZ_ATTEMPT_NOT_FOUND"));
        // The same stored attempt is invisible to a different caller, regardless of its source/status.
        String other = "Bearer " + jwt.issueAccessToken(user+1, List.of("LEARNER"), false, java.util.UUID.randomUUID());
        mvc.perform(get(path(own)).header("Authorization", other))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"));
        mvc.perform(get(path(Long.MAX_VALUE)).header("Authorization", bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("ATTEMPT_NOT_OWNED"));
        mvc.perform(get("/api/quiz-attempts/abc/result").header("Authorization", bearer()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
    }

    @Test
    void readOnlyResultDoesNotWriteProgressEventsScoresOrSnapshotsAndQueriesStayBounded() {
        for (int count : List.of(5, 30)) {
            config.setQuestionCount(count);
            Topic topic = topic(count, 0);
            long id = sessions.start(user, topic.id()).session().sessionId();
            advance(60);
            practice.finish(user, id, answers(count));
            var stored = jdbc.queryForMap("SELECT score,duration_ms,status,served_items::text FROM learning.attempts WHERE id=?", id);
            long events = jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id=?", Long.class, user);
            var statistics = factory.unwrap(SessionFactory.class).getStatistics();
            boolean enabled = statistics.isStatisticsEnabled();
            try {
                statistics.setStatisticsEnabled(true);
                statistics.clear();
                var result = results.result(user, id);
                assertThat(result.items()).hasSize(count);
                // Attempt + batch evidence through Hibernate; JDBC topic/first projections add no item-dependent query.
                assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
            } finally {
                statistics.setStatisticsEnabled(enabled);
            }
            assertThat(jdbc.queryForMap("SELECT score,duration_ms,status,served_items::text FROM learning.attempts WHERE id=?", id)).isEqualTo(stored);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id=?", Long.class, user)).isEqualTo(events);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.user_progress WHERE user_id=?", Integer.class, user)).isZero();
        }
    }

    @Test
    void convertedLosingSubmissionIsShownAsRetryWithTheRealFirstSummary() {
        Topic topic = topic(10, 0);
        long first = directQuiz(topic);
        long second = directQuiz(topic);
        advance(20);
        practice.finish(user, first, answers(8));
        practice.finish(user, second, answers(10));
        var result = results.result(user, second);
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.rankingEligible()).isFalse();
        assertThat(result.firstAttempt().score()).isEqualTo(80);
    }

    private String path(long id) { return "/api/quiz-attempts/"+id+"/result"; }
}
