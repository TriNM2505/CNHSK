package com.cnhsk.learning.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import com.cnhsk.learning.knowledge.entity.QuestionKnowledgePointId;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionSource;
import com.cnhsk.learning.knowledge.entity.QuestionStatus;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionKnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class KnowledgeRepositoriesIntegrationTest extends AbstractIntegrationTest {

    @Autowired private KnowledgePointRepository nodes;
    @Autowired private QuestionRepository questions;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private TopicRepository topics;
    @Autowired private LexemeRepository lexemes;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Environment environment;

    @Test
    void AT30_oldAndNewEntitiesStartAgainstFlywayWithValidate() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(entityManager.getMetamodel().getEntities()).extracting(entity -> entity.getJavaType().getName())
                .contains(KnowledgePoint.class.getName(), Question.class.getName(),
                        QuestionKnowledgePoint.class.getName(), Topic.class.getName(), Lexeme.class.getName());
    }

    @Test
    void AT30_nodeArraysNullableSkillAndUtcTimestampsRoundTrip() {
        KnowledgePoint prerequisite = nodes.saveAndFlush(node("NP1-901", KnowledgeDomain.NP, (short) 1));
        KnowledgePoint support = nodes.saveAndFlush(node("TV1-901", KnowledgeDomain.TV, (short) 1));
        KnowledgePoint current = node("NP2-901", KnowledgeDomain.NP, (short) 2);
        current.setTitle("Câu vị trí tự soạn");
        current.setNameZh("位置示例");
        current.setSkillType(KnowledgeSkillType.GRAMMAR);
        current.setCanDo("Nhận biết vị trí trong câu tự soạn.");
        current.setPrereqIds(new Long[] { prerequisite.getId() });
        current.setSupportIds(new Long[] { support.getId() });
        nodes.saveAndFlush(current);
        entityManager.clear();

        KnowledgePoint loaded = nodes.findByCode("NP2-901").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(current.getId());
        assertThat(loaded.getCode()).isEqualTo("NP2-901");
        assertThat(loaded.getDomain()).isEqualTo(KnowledgeDomain.NP);
        assertThat(loaded.getHskLevel()).isEqualTo((short) 2);
        assertThat(loaded.getTitle()).isEqualTo("Câu vị trí tự soạn");
        assertThat(loaded.getNameZh()).isEqualTo("位置示例");
        assertThat(loaded.getSkillType()).isEqualTo(KnowledgeSkillType.GRAMMAR);
        assertThat(loaded.getCanDo()).isEqualTo(current.getCanDo());
        assertThat(loaded.getLexemeId()).isNull();
        assertThat(loaded.getPrereqIds()).containsExactly(prerequisite.getId());
        assertThat(loaded.getSupportIds()).containsExactly(support.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(nodes.findById(prerequisite.getId()).orElseThrow().getSkillType()).isNull();
        assertThat(nodes.findById(prerequisite.getId()).orElseThrow().getPrereqIds()).isEmpty();
        assertThat(nodes.findById(prerequisite.getId()).orElseThrow().getSupportIds()).isEmpty();
        assertThat(nodes.findByCodeIn(List.of("NP2-901", "MISSING"))).extracting(KnowledgePoint::getId)
                .containsExactly(current.getId());
        assertThat(nodes.findByCode("MISSING")).isEmpty();
        assertThat(nodes.findByCodeIn(List.of())).isEmpty();
    }

    @Test
    void AT30_questionJsonUsesStorageKeysAndPreservesReviewMetadata() throws Exception {
        long reviewerId = jdbc.queryForObject(
                "INSERT INTO auth.users (email, password_hash, display_name) VALUES (?, ?, ?) RETURNING id",
                Long.class, "knowledge-" + UUID.randomUUID() + "@example.test",
                new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString()), "Người kiểm thử");
        long requestId = jdbc.queryForObject("INSERT INTO learning.service_requests (user_id, kind, status) "
                + "VALUES (?, 'AI_GENERATE', 'COMPLETED') RETURNING id", Long.class, reviewerId);
        List<QuestionOption> options = List.of(
                new QuestionOption("A", "水", true, null, null),
                new QuestionOption("B", "书", false, "TV1-901", "Nhầm từ chỉ đồ vật."));
        Question question = new Question("AT30_ENTITY", QuestionType.MULTIPLE_CHOICE, (short) 2, "Chọn từ chỉ nước.");
        question.setOptions(options);
        question.setExplanation("水 nghĩa là nước.");
        question.setAudioUrl("https://example.test/sample.mp3");
        question.setImageUrl("https://example.test/sample.svg");
        question.setSource(QuestionSource.AI);
        question.setStatus(QuestionStatus.APPROVED);
        question.setReviewedBy(reviewerId);
        question.setReviewedAt(Instant.parse("2026-11-04T00:00:00Z"));
        question.setReviewReason("Nội dung kiểm thử tự soạn.");
        question.setReusable(false);
        question.setCreatedByRequestId(requestId);
        question.setDifficulty((short) 3);
        questions.saveAndFlush(question);
        entityManager.clear();

        Question loaded = questions.findByCode("AT30_ENTITY").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(question.getId());
        assertThat(loaded.getQtype()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(loaded.getHskLevel()).isEqualTo((short) 2);
        assertThat(loaded.getStem()).isEqualTo(question.getStem());
        assertThat(loaded.getOptions()).isEqualTo(options);
        assertThat(loaded.getCorrectKey()).isNull();
        assertThat(loaded.getExplanation()).isEqualTo(question.getExplanation());
        assertThat(loaded.getAudioUrl()).isEqualTo(question.getAudioUrl());
        assertThat(loaded.getImageUrl()).isEqualTo(question.getImageUrl());
        assertThat(loaded.getSource()).isEqualTo(QuestionSource.AI);
        assertThat(loaded.getStatus()).isEqualTo(QuestionStatus.APPROVED);
        assertThat(loaded.getReviewedBy()).isEqualTo(reviewerId);
        assertThat(loaded.getReviewedAt()).isEqualTo(question.getReviewedAt());
        assertThat(loaded.getReviewReason()).isEqualTo(question.getReviewReason());
        assertThat(loaded.isReusable()).isFalse();
        assertThat(loaded.getCreatedByRequestId()).isEqualTo(requestId);
        assertThat(loaded.getDifficulty()).isEqualTo((short) 3);
        assertThat(loaded.getCreatedAt()).isNotNull();
        JsonNode stored = objectMapper.readTree(jdbc.queryForObject(
                "SELECT options::text FROM learning.questions WHERE id=?", String.class, question.getId()));
        assertThat(stored.get(0).get("is_correct").booleanValue()).isTrue();
        assertThat(stored.get(1).get("error_kid").asText()).isEqualTo("TV1-901");
        assertThat(stored.get(1).get("error_note").asText()).isEqualTo("Nhầm từ chỉ đồ vật.");
        assertThat(stored.get(1).has("isCorrect")).isFalse();
        assertThat(stored.get(1).has("errorKid")).isFalse();
        loaded.setExplanation("Lời giải đã sửa.");
        questions.saveAndFlush(loaded);
        entityManager.clear();
        assertThat(questions.findById(question.getId()).orElseThrow().getOptions()).isEqualTo(options);
    }

    @Test
    void AT30_questionDefaultsAndFiftyCharacterCorrectKeyMatchV1AndV5() {
        Question question = new Question("AT30_FILL", QuestionType.FILL_BLANK, (short) 1, "Điền ___ tự soạn.");
        question.setCorrectKey("a".repeat(50));
        questions.saveAndFlush(question);
        entityManager.clear();
        Question loaded = questions.findById(question.getId()).orElseThrow();
        assertThat(loaded.getCorrectKey()).hasSize(50);
        assertThat(loaded.getOptions()).isEmpty();
        assertThat(loaded.getSource()).isEqualTo(QuestionSource.IMPORT);
        assertThat(loaded.getStatus()).isEqualTo(QuestionStatus.PENDING_REVIEW);
        assertThat(loaded.isReusable()).isTrue();
        assertThat(loaded.getDifficulty()).isEqualTo((short) 2);
        assertThat(questions.findByCodeIn(List.of("AT30_FILL", "MISSING"))).extracting(Question::getId)
                .containsExactly(question.getId());
        assertThat(questions.findByCode("MISSING")).isEmpty();
        assertThat(questions.findByCodeIn(List.of())).isEmpty();
    }

    @Test
    void AT30_questionLabelsRoundTripWithCompositeIdsAndBatchLookup() {
        KnowledgePoint primary = nodes.saveAndFlush(node("NP1-901", KnowledgeDomain.NP, (short) 1));
        KnowledgePoint secondary = nodes.saveAndFlush(node("TV1-901", KnowledgeDomain.TV, (short) 1));
        Question first = questions.saveAndFlush(new Question("AT30_LABEL_1", QuestionType.FILL_BLANK, (short) 1, "Câu ___."));
        Question second = questions.saveAndFlush(new Question("AT30_LABEL_2", QuestionType.FILL_BLANK, (short) 1, "Câu khác ___."));
        labels.saveAndFlush(new QuestionKnowledgePoint(first.getId(), primary.getId(), KnowledgeLabelRole.PRIMARY));
        labels.saveAndFlush(new QuestionKnowledgePoint(first.getId(), secondary.getId(), KnowledgeLabelRole.SECONDARY));
        labels.saveAndFlush(new QuestionKnowledgePoint(second.getId(), primary.getId(), KnowledgeLabelRole.PRIMARY));
        entityManager.clear();
        QuestionKnowledgePoint loaded = labels.findById(new QuestionKnowledgePointId(first.getId(), primary.getId())).orElseThrow();
        assertThat(loaded.getQuestionId()).isEqualTo(first.getId());
        assertThat(loaded.getKnowledgePointId()).isEqualTo(primary.getId());
        assertThat(loaded.getRole()).isEqualTo(KnowledgeLabelRole.PRIMARY);
        assertThat(labels.findByQuestionIdIn(List.of(first.getId()))).hasSize(2)
                .extracting(QuestionKnowledgePoint::getKnowledgePointId).containsExactlyInAnyOrder(primary.getId(), secondary.getId());
        assertThat(labels.findByQuestionIdIn(List.of())).isEmpty();
    }

    @Test
    void AT30_unchangedTopicAndLexemeEntitiesPreserveNullableUnmappedLinks() {
        KnowledgePoint point = nodes.saveAndFlush(node("TV1-901", KnowledgeDomain.TV, (short) 1));
        Topic topic = topics.saveAndFlush(new Topic("AT30_CONTENT", "Chủ đề tự soạn", (short) 1));
        Lexeme lexeme = lexemes.saveAndFlush(new Lexeme(LexemeKind.CHAR, "水", "nước tự soạn"));
        assertThat(jdbc.queryForObject("SELECT knowledge_point_id FROM learning.topics WHERE id=?", Long.class, topic.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT knowledge_point_id FROM learning.lexemes WHERE id=?", Long.class, lexeme.getId())).isNull();
        jdbc.update("UPDATE learning.topics SET knowledge_point_id=? WHERE id=?", point.getId(), topic.getId());
        jdbc.update("UPDATE learning.lexemes SET knowledge_point_id=? WHERE id=?", point.getId(), lexeme.getId());
        entityManager.clear();
        Topic loadedTopic = topics.findById(topic.getId()).orElseThrow();
        Lexeme loadedLexeme = lexemes.findById(lexeme.getId()).orElseThrow();
        loadedTopic.setName("Tên chủ đề đã sửa");
        loadedLexeme.setMeaningVi("Nghĩa đã sửa");
        topics.saveAndFlush(loadedTopic);
        lexemes.saveAndFlush(loadedLexeme);
        entityManager.clear();
        assertThat(topics.findById(topic.getId()).orElseThrow().getName()).isEqualTo("Tên chủ đề đã sửa");
        assertThat(lexemes.findById(lexeme.getId()).orElseThrow().getMeaningVi()).isEqualTo("Nghĩa đã sửa");
        assertThat(jdbc.queryForObject("SELECT knowledge_point_id FROM learning.topics WHERE id=?", Long.class, topic.getId())).isEqualTo(point.getId());
        assertThat(jdbc.queryForObject("SELECT knowledge_point_id FROM learning.lexemes WHERE id=?", Long.class, lexeme.getId())).isEqualTo(point.getId());
    }

    private KnowledgePoint node(String code, KnowledgeDomain domain, short level) {
        return new KnowledgePoint(code, "Nút tự soạn", domain, "示例", level);
    }
}
