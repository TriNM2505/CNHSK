package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.cnhsk.learning.exam.MistakePracticeService;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** "Luyện lỗi của tôi" on PostgreSQL: own mistakes only, seven Vietnamese days, unseen questions first. */
class MistakePracticeIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-20T05:00:00Z");
    @Autowired MistakePracticeService mistakes;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean Clock clock;
    WeakKnowledgeSelectorIntegrationTest.Fixtures fixtures;
    final List<Long> sessions = new ArrayList<>();

    @BeforeEach
    void prepare() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.millis()).thenReturn(NOW.toEpochMilli());
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        fixtures = new WeakKnowledgeSelectorIntegrationTest.Fixtures(jdbc);
    }

    @AfterEach
    void cleanup() {
        for (long session : sessions) {
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id=?", session);
            jdbc.update("DELETE FROM learning.attempts WHERE id=?", session);
        }
        if (fixtures != null) {
            for (long user : fixtures.users) {
                jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", user);
            }
            fixtures.close();
        }
    }

    @Test
    void ownRecentMistakesDecideTheNodesAndUnseenQuestionsComeFirst() throws Exception {
        long learner = learner();
        long other = learner();
        long nodeA = fixtures.node(), nodeB = fixtures.node(), nodeC = fixtures.node();
        long wrongA = fixtures.question(nodeA, "IMPORT", "APPROVED", null);
        long unseenA = fixtures.question(nodeA, "IMPORT", "APPROVED", null);
        long wrongB = fixtures.question(nodeB, "IMPORT", "APPROVED", null);
        long oldC = fixtures.question(nodeC, "IMPORT", "APPROVED", null);
        fixtures.question(nodeC, "AI", "PENDING_REVIEW", null);
        fixtures.items(learner, wrongA, "B", false, NOW.minus(Duration.ofDays(1)), 3);
        fixtures.items(learner, wrongB, "B", false, NOW.minus(Duration.ofDays(2)), 1);
        fixtures.items(learner, oldC, "B", false, NOW.minus(Duration.ofDays(10)), 2);
        fixtures.items(other, oldC, "B", false, NOW, 5);

        assertThat(mistakes.recentMistakes(learner)).extracting(MistakePracticeService.MistakeNode::kid,
                MistakePracticeService.MistakeNode::wrongCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(fixtures.kid(nodeA), 3L),
                        org.assertj.core.groups.Tuple.tuple(fixtures.kid(nodeB), 1L));

        var result = mistakes.start(learner);

        assertThat(result.reason()).isNull();
        var session = result.session();
        sessions.add(session.sessionId());
        assertThat(session.source()).isEqualTo(PracticeSource.TARGETED_PRACTICE);
        assertThat(session.meta()).containsEntry("mode", "MISTAKES");
        assertThat(served(session.sessionId())).containsExactly(unseenA, wrongB, wrongA);
    }

    @Test
    void wrongWordsFromVocabularyAndGrammarSectionsCountForTheNodeOfTheWord() throws Exception {
        long learner = learner();
        long node = fixtures.node();
        long practice = fixtures.question(node, "IMPORT", "APPROVED", null);
        long attempt = fixtures.attempt(learner);
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,lexeme_id,item_type,skill,is_correct,order_index,answered_at) "
                + "SELECT ?,id,'MEANING_CHOICE','VOCAB',false,1,? FROM learning.lexemes WHERE knowledge_point_id=?",
                attempt, java.sql.Timestamp.from(NOW.minus(Duration.ofDays(1))), node);

        assertThat(mistakes.recentMistakes(learner)).extracting(MistakePracticeService.MistakeNode::kid,
                MistakePracticeService.MistakeNode::wrongCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(fixtures.kid(node), 1L));
        var result = mistakes.start(learner);
        sessions.add(result.session().sessionId());
        assertThat(served(result.session().sessionId())).containsExactly(practice);
    }

    @Test
    void approvedAiQuestionsAreListedAsNewItemsSoTheLearnerSeesThemMarked() throws Exception {
        long learner = learner();
        long node = fixtures.node();
        long wrong = fixtures.question(node, "IMPORT", "APPROVED", null);
        long drafted = fixtures.question(node, "AI", "PENDING_REVIEW", null);
        jdbc.update("UPDATE learning.questions SET status='APPROVED', reviewed_by=?, reviewed_at=now() WHERE id=?",
                fixtures.user(), drafted);
        fixtures.items(learner, wrong, "B", false, NOW.minus(Duration.ofDays(1)), 3);

        var session = mistakes.start(learner).session();
        sessions.add(session.sessionId());

        List<Long> served = served(session.sessionId());
        assertThat(served).containsExactlyInAnyOrder(wrong, drafted);
        int draftedNo = served.indexOf(drafted) + 1;
        assertThat(session.meta()).containsEntry("newItems", List.of(draftedNo));
    }

    @Test
    void learnerWithoutMistakesInTheLastSevenDaysGetsNoSession() {
        long learner = learner();
        long node = fixtures.node();
        fixtures.items(learner, fixtures.question(node, "IMPORT", "APPROVED", null), "B", false,
                NOW.minus(Duration.ofDays(8)), 4);
        var result = mistakes.start(learner);
        assertThat(result.session()).isNull();
        assertThat(result.reason()).isEqualTo("NO_RECENT_MISTAKES");
    }

    private long learner() {
        long user = fixtures.user();
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'LEARNER') ON CONFLICT DO NOTHING", user);
        return user;
    }

    private List<Long> served(long session) throws Exception {
        JsonNode items = mapper.readTree(jdbc.queryForObject(
                "SELECT served_items::text FROM learning.attempts WHERE id=?", String.class, session)).path("items");
        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.path("questionId").asLong()));
        return ids;
    }
}
