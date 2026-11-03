package com.cnhsk.learning.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeLabelRole;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@Import(KnowledgeTreeServiceIntegrationTest.SqlConfiguration.class)
class KnowledgeTreeControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private KnowledgePointRepository points;
    @Autowired private QuestionRepository questions;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private TopicRepository topics;
    @Autowired private LexemeRepository lexemes;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "CONTENT_EDITOR", "TEACHER"})
    void FR001_AT01_allowedRolesReadBothEndpointsWithBearerAndCookie(String role) throws Exception {
        node("NP1-901");
        for (String path : List.of("/api/knowledge-tree", "/api/knowledge-tree/nodes/NP1-901")) {
            mvc.perform(get(path).header("Authorization", bearer(role))).andExpect(status().isOk());
            mvc.perform(get(path).cookie(new Cookie("access_token", token(role)))).andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MODERATOR", "ACCOUNTANT"})
    void FR001_AT01_otherRolesHaveNoInheritedTreeAccess(String role) throws Exception {
        for (String path : List.of("/api/knowledge-tree", "/api/knowledge-tree/nodes/abc")) {
            mvc.perform(get(path).param("domain", "XX").header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.request_id").isNotEmpty());
        }
    }

    @Test
    void FR001_AT01_noTokenReturns401ForBothEndpoints() throws Exception {
        for (String path : List.of("/api/knowledge-tree", "/api/knowledge-tree/nodes/NP1-901")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.request_id").isNotEmpty());
        }
    }

    @Test
    void FR001_AT01_getMatcherDoesNotGrantWriteAccess() throws Exception {
        mvc.perform(post("/api/knowledge-tree").header("Authorization", bearer("TEACHER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
    }

    @Test
    void FR015_AT15_sortsSixDomainsLevelsAndKidsAndPreservesEdgesOutsideFilters() throws Exception {
        KnowledgePoint prerequisite = node("NA1-903");
        node("NA7-905");
        KnowledgePoint support = node("CH1-901");
        node("TV1-901");
        node("NP2-902");
        KnowledgePoint current = node("NP2-901");
        current.setPrereqIds(new Long[] { prerequisite.getId() });
        current.setSupportIds(new Long[] { support.getId() });
        current.setCanDo("Nhận biết cấu trúc tự soạn.");
        current.setSkillType(KnowledgeSkillType.GRAMMAR);
        points.saveAndFlush(current);
        node("NL1-901");
        node("VB1-901");
        JsonNode all = response("/api/knowledge-tree");
        assertThat(all.path("nodes")).extracting(row -> row.path("kid").asText()).containsExactly(
                "NA1-903", "NA7-905", "CH1-901", "TV1-901", "NP2-901", "NP2-902", "NL1-901", "VB1-901");
        JsonNode filtered = response("/api/knowledge-tree?domain=NP&hskLevel=2").path("nodes");
        assertThat(filtered).hasSize(2);
        assertThat(filtered.get(0).path("prereqKids")).extracting(JsonNode::asText).containsExactly("NA1-903");
        assertThat(filtered.get(0).path("supportKids")).extracting(JsonNode::asText).containsExactly("CH1-901");
        assertThat(filtered.get(0).path("canDo").asText()).isEqualTo(current.getCanDo());
        assertThat(filtered.get(0).path("skillType").asText()).isEqualTo("GRAMMAR");
        assertThat(filtered.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder(
                "kid", "domain", "hskLevel", "nameVi", "nameZh", "canDo", "skillType", "prereqKids", "supportKids");
        assertThat(all.path("nodes").get(0).path("canDo").isNull()).isTrue();
        assertThat(all.path("nodes").get(0).path("skillType").isNull()).isTrue();
        assertThat(response("/api/knowledge-tree?hskLevel=7").path("nodes")).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "3", "4", "5", "6", "7"})
    void FR015_AT15_acceptsEveryContractLevelIncludingBeyondDemoScope(String level) throws Exception {
        mvc.perform(get("/api/knowledge-tree").param("hskLevel", level).header("Authorization", bearer("LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nodes").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NA", "CH", "TV", "NP", "NL", "VB"})
    void FR015_AT15_acceptsAllSixDomains(String domain) throws Exception {
        mvc.perform(get("/api/knowledge-tree").param("domain", domain).header("Authorization", bearer("LEARNER")))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "8", "9", "-1", "abc", "1.5", "", "999999999999999999999"})
    void FR015_AT15_invalidLevelReturns422WithStandardError(String level) throws Exception {
        mvc.perform(get("/api/knowledge-tree").param("hskLevel", level)
                        .header("Authorization", bearer("LEARNER")).header("X-Request-Id", "t10-validation"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("hskLevel"))
                .andExpect(jsonPath("$.request_id").value("t10-validation"))
                .andExpect(header().string("X-Request-Id", "t10-validation"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"XX", "np", "", "NP,TV", "NP' OR 1=1"})
    void FR015_AT15_invalidDomainReturns422(String domain) throws Exception {
        mvc.perform(get("/api/knowledge-tree").param("domain", domain).header("Authorization", bearer("LEARNER")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("domain"));
    }

    @Test
    void FR015_AT15_emptyTreeReturnsEmptyNodes() throws Exception {
        assertThat(response("/api/knowledge-tree").path("nodes")).isEmpty();
    }

    @Test
    void FR016_AT16_detailReturnsDependentsLinkedContentAndApprovedDistinctCounts() throws Exception {
        KnowledgePoint current = node("TV1-901");
        KnowledgePoint first = node("NP2-901");
        first.setPrereqIds(new Long[] { current.getId() });
        points.saveAndFlush(first);
        KnowledgePoint second = node("NL2-901");
        second.setPrereqIds(new Long[] { current.getId() });
        points.saveAndFlush(second);
        Topic topic = topics.saveAndFlush(new Topic("T10_DETAIL", "Chủ đề mẫu tự soạn", (short) 1));
        jdbc.update("UPDATE learning.topics SET knowledge_point_id=? WHERE id=?", current.getId(), topic.getId());
        for (String text : List.of("水杯", "茶杯")) {
            Lexeme lexeme = lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, text, "Từ tự soạn"));
            jdbc.update("UPDATE learning.lexemes SET knowledge_point_id=? WHERE id=?", current.getId(), lexeme.getId());
        }
        for (int index = 0; index < 4; index++) {
            Question question = new Question("T10_HTTP_" + index, QuestionType.MULTIPLE_CHOICE, (short) 1, "Nội dung riêng kho câu.");
            question.setStatus(index == 3 ? QuestionStatus.ARCHIVED : QuestionStatus.APPROVED);
            question.setExplanation("Lời giải không xuất qua API cây.");
            question.setOptions(List.of(new QuestionOption("A", "一", true, null, null),
                    new QuestionOption("B", "二", false, null, null)));
            questions.saveAndFlush(question);
            labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), index == 2 ? first.getId() : current.getId(),
                    KnowledgeLabelRole.PRIMARY));
            if (index == 2) labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), current.getId(), KnowledgeLabelRole.SECONDARY));
        }
        JsonNode detail = response("/api/knowledge-tree/nodes/TV1-901");
        assertThat(detail.path("dependentKids")).extracting(JsonNode::asText).containsExactly("NL2-901", "NP2-901");
        assertThat(detail.path("approvedQuestionCount").asLong()).isEqualTo(3);
        assertThat(detail.path("lexemeCount").asLong()).isEqualTo(2);
        assertThat(detail.path("topics")).hasSize(1);
        assertThat(detail.path("topics").get(0).path("id").asLong()).isEqualTo(topic.getId());
        assertThat(detail.path("topics").get(0).path("code").asText()).isEqualTo("T10_DETAIL");
        assertThat(detail.path("topics").get(0).path("name").asText()).isEqualTo(topic.getName());
        assertThat(detail.fieldNames()).toIterable().containsExactlyInAnyOrder("kid", "domain", "hskLevel", "nameVi",
                "nameZh", "canDo", "skillType", "prereqKids", "supportKids", "dependentKids", "topics", "lexemeCount", "approvedQuestionCount");
        assertThat(detail.toString()).doesNotContain("Nội dung riêng kho câu", "Lời giải", "isCorrect", "correctKey", "options");
        assertThat(response("/api/knowledge-tree").toString()).doesNotContain("stem", "options", "correctKey", "explanation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NP1-999", "abc", "np1-901", "NP8-001", "NP10-001", "XX1-001", "NP3-04", "NP3-0001"})
    void FR016_AT16_unknownAndMalformedKidsReturn404(String kid) throws Exception {
        mvc.perform(get("/api/knowledge-tree/nodes/{kid}", kid).header("Authorization", bearer("LEARNER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value("KNOWLEDGE_NODE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy nút năng lực."))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    private KnowledgePoint node(String kid) {
        return points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn " + kid,
                KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例", (short) (kid.charAt(2) - '0')));
    }

    private JsonNode response(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path).header("Authorization", bearer("LEARNER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private String bearer(String role) { return "Bearer " + token(role); }

    private String token(String role) { return jwt.issueAccessToken(901L, List.of(role), false, UUID.randomUUID()); }
}
