package com.cnhsk.learning.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePoint;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@Import(KnowledgeTreeServiceIntegrationTest.SqlConfiguration.class)
class KnowledgeQuestionCountIntegrationTest extends AbstractIntegrationTest {

    private static final String PATH = "/api/admin/knowledge-tree/question-counts";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private KnowledgePointRepository points;
    @Autowired private QuestionRepository questions;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private KnowledgeTreeService tree;
    @Autowired private KnowledgeTreeServiceIntegrationTest.SqlCapture sql;

    @Test
    void FR029_AT31_countsPrimaryLabelsPerNodeIncludingEmptyNodes() throws Exception {
        KnowledgePoint withQuestions = node("NP2-931");
        KnowledgePoint secondaryOnly = node("TV1-931");
        node("CH1-931");
        question("T16_A", QuestionStatus.APPROVED, withQuestions, null);
        question("T16_B", QuestionStatus.APPROVED, withQuestions, secondaryOnly);
        question("T16_C", QuestionStatus.PENDING_REVIEW, withQuestions, null);
        question("T16_D", QuestionStatus.REJECTED, withQuestions, null);
        question("T16_E", QuestionStatus.ARCHIVED, withQuestions, null);

        JsonNode counts = mapper.readTree(mvc.perform(get(PATH).header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("counts");

        assertThat(counts).extracting(row -> row.path("kid").asText()).containsExactly("CH1-931", "NP2-931", "TV1-931");
        assertThat(count(counts, "NP2-931")).isEqualTo(new long[] {4, 2});
        // A secondary label never moves a question to another node.
        assertThat(count(counts, "TV1-931")).isEqualTo(new long[] {0, 0});
        assertThat(count(counts, "CH1-931")).isEqualTo(new long[] {0, 0});
        assertThat(counts.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("kid", "total", "approved");
    }

    @Test
    void FR029_AT31_readsAllNodesInOneStatement() {
        KnowledgePoint first = node("NP1-932");
        node("NP1-933");
        node("NP1-934");
        question("T16_SQL", QuestionStatus.APPROVED, first, null);
        sql.start();
        try {
            assertThat(tree.questionCounts()).hasSize(3);
            assertThat(sql.statements()).hasSize(1);
        } finally {
            sql.stop();
        }
    }

    @Test
    void FR029_AT31_emptyTreeReturnsEmptyList() throws Exception {
        mvc.perform(get(PATH).header("Authorization", bearer("CONTENT_EDITOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.counts").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "LEARNER", "ADMIN", "MODERATOR", "ACCOUNTANT"})
    void FR029_AT31_onlyContentEditorsReadCounts(String role) throws Exception {
        mvc.perform(get(PATH).header("Authorization", bearer(role)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void FR029_AT31_noTokenReturns401() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    private static long[] count(JsonNode counts, String kid) {
        for (JsonNode row : counts) {
            if (row.path("kid").asText().equals(kid)) {
                return new long[] {row.path("total").asLong(), row.path("approved").asLong()};
            }
        }
        throw new AssertionError("missing " + kid);
    }

    private void question(String code, QuestionStatus status, KnowledgePoint primary, KnowledgePoint secondary) {
        Question question = new Question(code, QuestionType.MULTIPLE_CHOICE, (short) 1, "Câu tự soạn " + code);
        question.setStatus(status);
        if (status == QuestionStatus.REJECTED) question.setReviewReason("Lý do tự soạn cho test.");
        question.setOptions(List.of(new QuestionOption("A", "一", true, null, null),
                new QuestionOption("B", "二", false, null, null)));
        questions.saveAndFlush(question);
        labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), primary.getId(), KnowledgeLabelRole.PRIMARY));
        if (secondary != null) {
            labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), secondary.getId(), KnowledgeLabelRole.SECONDARY));
        }
    }

    private KnowledgePoint node(String kid) {
        return points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn " + kid,
                KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例", (short) (kid.charAt(2) - '0')));
    }

    private String bearer(String role) {
        return "Bearer " + jwt.issueAccessToken(931L, List.of(role), false, UUID.randomUUID());
    }
}
