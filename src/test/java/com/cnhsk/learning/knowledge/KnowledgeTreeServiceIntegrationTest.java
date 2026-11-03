package com.cnhsk.learning.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
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
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
@Import(KnowledgeTreeServiceIntegrationTest.SqlConfiguration.class)
class KnowledgeTreeServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private KnowledgeTreeService service;
    @Autowired private KnowledgePointRepository points;
    @Autowired private QuestionRepository questions;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private TopicRepository topics;
    @Autowired private LexemeRepository lexemes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private SqlCapture capture;

    @Test
    void FR015_FR024_AT15_readsTreeAndBothEdgeTypesInOneQuery() {
        KnowledgePoint prerequisite = node("NA1-901");
        KnowledgePoint support = node("TV1-901");
        KnowledgePoint current = node("NP2-901");
        current.setPrereqIds(new Long[] { prerequisite.getId() });
        current.setSupportIds(new Long[] { support.getId() });
        points.saveAndFlush(current);
        entityManager.clear();
        capture.start();
        try {
            var tree = service.nodes();
            assertThat(tree).extracting(KnowledgeTreeService.KnowledgeNode::kid)
                    .containsExactly("NA1-901", "TV1-901", "NP2-901");
            assertThat(tree.get(2).prereqKids()).containsExactly("NA1-901");
            assertThat(tree.get(2).supportKids()).containsExactly("TV1-901");
            assertThat(capture.statements()).hasSize(1);
        } finally {
            capture.stop();
        }
        assertThat(service.node("NP2-901").orElseThrow().id()).isEqualTo(current.getId());
    }

    @Test
    void FR024_AT24_fiftyQuestionsUseExactlyOneQueryIncludingOptionsAndMissingIds() {
        KnowledgePoint primary = node("NP1-901");
        KnowledgePoint secondary = node("TV1-901");
        KnowledgePoint otherSecondary = node("CH1-901");
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            Question question = question("BATCH_" + index, QuestionStatus.APPROVED, 1, primary);
            question.setOptions(List.of(new QuestionOption("A", "一", true, null, null),
                    new QuestionOption("B", "二", false, secondary.getCode(), "Lỗi tự soạn")));
            questions.saveAndFlush(question);
            labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), secondary.getId(), KnowledgeLabelRole.SECONDARY));
            labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), otherSecondary.getId(), KnowledgeLabelRole.SECONDARY));
            ids.add(question.getId());
        }
        Question unlabeled = question("NO_LABEL", QuestionStatus.PENDING_REVIEW, 1, null);
        Set<Long> requested = ids.stream().collect(Collectors.toSet());
        requested.add(unlabeled.getId());
        requested.add(Long.MAX_VALUE);
        entityManager.clear();
        capture.start();
        try {
            var result = service.kidsOfQuestions(requested);
            assertThat(result).hasSize(50).doesNotContainKeys(unlabeled.getId(), Long.MAX_VALUE);
            result.values().forEach(kids -> {
                assertThat(kids.primaryKid()).isEqualTo("NP1-901");
                assertThat(kids.secondaryKids()).containsExactly("CH1-901", "TV1-901");
                assertThat(kids.errorsByOption()).containsOnlyKeys("B");
                assertThat(kids.errorsByOption().get("B").errorKid()).isEqualTo("TV1-901");
                assertThat(kids.errorsByOption().get("B").errorNote()).isEqualTo("Lỗi tự soạn");
            });
            assertThat(capture.statements()).hasSize(1);
            System.out.println("KNOWLEDGE_LABEL_SQL_EVIDENCE questions=50 statements=" + capture.statements().size());
        } finally {
            capture.stop();
        }
        capture.start();
        try {
            assertThat(service.kidsOfQuestions(Set.of())).isEmpty();
            assertThat(capture.statements()).isEmpty();
        } finally {
            capture.stop();
        }
    }

    @Test
    void FR024_AT24_readsContentLinksAndReturnsEmptyForUnlinkedOrMissingContent() {
        KnowledgePoint point = node("TV1-901");
        Topic linked = topics.saveAndFlush(new Topic("T10_LINK", "Chủ đề tự soạn", (short) 1));
        Topic unlinked = topics.saveAndFlush(new Topic("T10_NONE", "Chưa gắn nút", (short) 1));
        Lexeme linkedWord = lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "水杯", "Cốc nước tự soạn"));
        Lexeme unlinkedWord = lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "茶杯", "Cốc trà tự soạn"));
        jdbc.update("UPDATE learning.topics SET knowledge_point_id=? WHERE id=?", point.getId(), linked.getId());
        jdbc.update("UPDATE learning.lexemes SET knowledge_point_id=? WHERE id=?", point.getId(), linkedWord.getId());
        assertThat(service.kidOfTopic(linked.getId())).contains("TV1-901");
        assertThat(service.kidOfLexeme(linkedWord.getId())).contains("TV1-901");
        assertThat(service.kidOfTopic(unlinked.getId())).isEmpty();
        assertThat(service.kidOfLexeme(unlinkedWord.getId())).isEmpty();
        assertThat(service.kidOfTopic(Long.MAX_VALUE)).isEmpty();
        assertThat(service.kidOfLexeme(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void FR025_AT25_onlyApprovedPrimaryMatchesRespectLevelOrderAndLimit() {
        KnowledgePoint target = node("NP1-901");
        KnowledgePoint other = node("TV1-901");
        Question first = question("APPROVED_1", QuestionStatus.APPROVED, 1, target);
        Question second = question("APPROVED_2", QuestionStatus.APPROVED, 9, target);
        for (QuestionStatus status : List.of(QuestionStatus.ARCHIVED, QuestionStatus.PENDING_REVIEW, QuestionStatus.REJECTED)) {
            question(status.name(), status, 1, target);
        }
        Question secondaryOnly = question("OTHER_PRIMARY", QuestionStatus.APPROVED, 1, other);
        labels.saveAndFlush(new QuestionKnowledgePoint(secondaryOnly.getId(), target.getId(), KnowledgeLabelRole.SECONDARY));
        entityManager.clear();
        assertThat(service.approvedQuestionIds(Set.of(target.getCode()), null, 200))
                .containsExactly(first.getId(), second.getId());
        assertThat(service.approvedQuestionIds(Set.of(target.getCode()), 9, 200)).containsExactly(second.getId());
        assertThat(service.approvedQuestionIds(Set.of(target.getCode()), 2, 200)).isEmpty();
        assertThat(service.approvedQuestionIds(Set.of(target.getCode()), null, 1)).containsExactly(first.getId());
        assertThat(service.approvedQuestionIds(Set.of(target.getCode(), other.getCode()), null, 200))
                .containsExactly(first.getId(), second.getId(), secondaryOnly.getId());
        assertThat(service.approvedQuestionIds(Set.of("NP1-999"), null, 200)).isEmpty();
    }

    @Test
    void FR016_AT16_detailUsesFourQueriesAndCountsApprovedAcrossBothRoles() {
        KnowledgePoint point = node("TV1-901");
        KnowledgePoint dependent = node("NP2-901");
        dependent.setPrereqIds(new Long[] { point.getId() });
        points.saveAndFlush(dependent);
        KnowledgePoint supportOnly = node("NL2-901");
        supportOnly.setSupportIds(new Long[] { point.getId() });
        points.saveAndFlush(supportOnly);
        question("DETAIL_PRIMARY", QuestionStatus.APPROVED, 1, point);
        Question secondary = question("DETAIL_SECONDARY", QuestionStatus.APPROVED, 1, dependent);
        labels.saveAndFlush(new QuestionKnowledgePoint(secondary.getId(), point.getId(), KnowledgeLabelRole.SECONDARY));
        question("DETAIL_ARCHIVED", QuestionStatus.ARCHIVED, 1, point);
        entityManager.clear();
        capture.start();
        try {
            var detail = service.detail(point.getCode());
            assertThat(detail.dependentKids()).containsExactly("NP2-901");
            assertThat(detail.approvedQuestionCount()).isEqualTo(2);
            assertThat(detail.topics()).isEmpty();
            assertThat(detail.lexemeCount()).isZero();
            assertThat(capture.statements()).hasSize(4);
        } finally {
            capture.stop();
        }
    }

    private KnowledgePoint node(String kid) {
        return points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn " + kid,
                KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例", (short) (kid.charAt(2) - '0')));
    }

    private Question question(String code, QuestionStatus status, int level, KnowledgePoint primary) {
        Question question = new Question("T10_" + code, QuestionType.MULTIPLE_CHOICE, (short) level, "Câu hỏi tự soạn.");
        question.setStatus(status);
        if (status == QuestionStatus.REJECTED) question.setReviewReason("Lý do từ chối tự soạn cho kiểm thử.");
        question.setOptions(List.of(new QuestionOption("A", "一", true, null, null),
                new QuestionOption("B", "二", false, null, null)));
        questions.saveAndFlush(question);
        if (primary != null) labels.saveAndFlush(new QuestionKnowledgePoint(question.getId(), primary.getId(), KnowledgeLabelRole.PRIMARY));
        return question;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean SqlCapture knowledgeSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer knowledgeStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        List<String> statements() { return List.copyOf(active.get()); }
        void stop() { active.remove(); }
        @Override
        public String inspect(String sql) {
            List<String> statements = active.get();
            if (statements != null) statements.add(sql.toLowerCase(Locale.ROOT).strip());
            return sql;
        }
    }
}
