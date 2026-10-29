package com.cnhsk.learning.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ContentRepositoriesIntegrationTest extends AbstractIntegrationTest {

    @Autowired private LexemeRepository lexemes;
    @Autowired private TopicRepository topics;
    @Autowired private TopicItemRepository topicItems;
    @Autowired private PronStageRepository pronStages;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void FR011_batchLexemeLookupKeepsKindInTheNaturalKey() {
        Lexeme character = lexemes.saveAndFlush(lexeme(LexemeKind.CHAR, "我"));
        Lexeme secondCharacter = lexemes.saveAndFlush(lexeme(LexemeKind.CHAR, "你"));
        Lexeme word = lexemes.saveAndFlush(lexeme(LexemeKind.WORD, "我"));
        entityManager.clear();

        assertThat(lexemes.findByKindAndTextIn(LexemeKind.CHAR, List.of("我", "你", "缺")))
                .extracting(Lexeme::getId).containsExactlyInAnyOrder(character.getId(), secondCharacter.getId());
        assertThat(lexemes.findByKindAndTextIn(LexemeKind.WORD, List.of("我")))
                .extracting(Lexeme::getId).containsExactly(word.getId());
        assertThat(lexemes.findByKindAndText(LexemeKind.CHAR, "我")).get()
                .extracting(Lexeme::getKind).isEqualTo(LexemeKind.CHAR);
        assertThat(lexemes.findByKindAndText(LexemeKind.CHAR, "缺")).isEmpty();
        assertThat(lexemes.findByKindAndTextIn(LexemeKind.CHAR, List.of())).isEmpty();
        assertThat(lexemes.findByKindAndTextIn(LexemeKind.CHAR, List.of("' OR 1=1 --"))).isEmpty();
    }

    @Test
    void FR013_topicsAndStagesSupportBatchNaturalKeysAndEmptyBatches() {
        Topic first = topics.saveAndFlush(new Topic("BATCH_FIRST", "Chủ đề tự tạo", (short) 1));
        topics.saveAndFlush(new Topic("BATCH_SECOND", "Chủ đề tự tạo khác", (short) 9));
        PronStage stage = pronStages.saveAndFlush(new PronStage((short) 1, "Âm đầu"));
        pronStages.saveAndFlush(new PronStage((short) 8, "Ôn tập"));
        entityManager.clear();

        assertThat(topics.findByCodeIn(List.of("BATCH_FIRST", "MISSING")))
                .extracting(Topic::getId).containsExactly(first.getId());
        assertThat(topics.findByCode("BATCH_SECOND")).get().extracting(Topic::getHskLevel).isEqualTo((short) 9);
        assertThat(topics.findByCode("MISSING")).isEmpty();
        assertThat(topics.findByCodeIn(List.of())).isEmpty();
        assertThat(pronStages.findByStageIndexIn(List.of((short) 1, (short) 3)))
                .extracting(PronStage::getId).containsExactly(stage.getId());
        assertThat(pronStages.findByStageIndex((short) 8)).isPresent();
        assertThat(pronStages.findByStageIndex((short) 3)).isEmpty();
        assertThat(pronStages.findByStageIndexIn(List.of())).isEmpty();
    }

    @Test
    void FR011_AT037_topicItemPersistsALexemeWithoutAKnowledgePointAndSupportsBatchLookup() {
        Topic first = topics.saveAndFlush(new Topic("ITEM_FIRST", "Nhóm thứ nhất", (short) 1));
        Topic second = topics.saveAndFlush(new Topic("ITEM_SECOND", "Nhóm thứ hai", (short) 1));
        Lexeme lexeme = lexemes.saveAndFlush(lexeme(LexemeKind.WORD, "我们"));
        TopicItem item = new TopicItem(first.getId(), lexeme.getId());
        item.setOrderIndex(7);
        topicItems.saveAndFlush(item);
        topicItems.saveAndFlush(new TopicItem(second.getId(), lexeme.getId()));
        entityManager.clear();

        TopicItem loaded = topicItems.findByTopicIdAndLexemeId(first.getId(), lexeme.getId()).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(item.getId());
        assertThat(loaded.getTopicId()).isEqualTo(first.getId());
        assertThat(loaded.getLexemeId()).isEqualTo(lexeme.getId());
        assertThat(loaded.getKnowledgePointId()).isNull();
        assertThat(loaded.getOrderIndex()).isEqualTo(7);
        assertThat(topicItems.findByTopicIdInAndLexemeIdIn(List.of(first.getId()), List.of(lexeme.getId())))
                .extracting(TopicItem::getId).containsExactly(item.getId());
        assertThat(topicItems.findByTopicIdAndLexemeId(first.getId(), -1L)).isEmpty();
        assertThat(topicItems.findByTopicIdInAndLexemeIdIn(List.of(), List.of(lexeme.getId()))).isEmpty();
        assertThat(topicItems.findByTopicIdInAndLexemeIdIn(List.of(first.getId()), List.of())).isEmpty();
    }

    @Test
    void FR020_AT038_grammarExamplesAndExercisesRoundTripAsTypedJsonWithoutLosingOtherFields() {
        LexemeExtra extra = new LexemeExtra(
                List.of(new LexemeExtra.Example("我是学生", "wǒ shì xuésheng", "Tôi là học sinh")),
                List.of("tự tạo"), "A + 是 + B", "Giới thiệu một người", "Chú ý thứ tự từ",
                List.of(new LexemeExtra.Exercise(1, "我___学生", List.of(
                        new LexemeExtra.Option("A", "是"), new LexemeExtra.Option("B", "不")), "A", "Dùng 是")));
        Lexeme grammar = lexeme(LexemeKind.GRAMMAR, "是 + danh từ");
        grammar.setExtra(extra);
        grammar.setMeaningVi("Cấu trúc giới thiệu");
        grammar.setMeaningNorm("cau truc gioi thieu");
        lexemes.saveAndFlush(grammar);
        entityManager.clear();

        Lexeme loaded = lexemes.findByKindAndText(LexemeKind.GRAMMAR, grammar.getText()).orElseThrow();
        assertThat(loaded.getExtra()).isEqualTo(extra);
        assertThat(loaded.getMeaningVi()).isEqualTo("Cấu trúc giới thiệu");
        assertThat(loaded.getMeaningNorm()).isEqualTo("cau truc gioi thieu");
        loaded.setMeaningVi("Cấu trúc giới thiệu đã sửa");
        lexemes.saveAndFlush(loaded);
        entityManager.clear();
        assertThat(lexemes.findById(grammar.getId()).orElseThrow().getExtra()).isEqualTo(extra);
    }

    @Test
    void FR013_lexemeMetadataRetainsSmallintsTextAndSearchColumns() {
        Lexeme character = lexeme(LexemeKind.CHAR, "你");
        character.setPinyin("nǐ");
        character.setPinyinNorm("ni");
        character.setSinoViet("nhĩ");
        character.setMeaningVi("bạn");
        character.setMeaningNorm("ban");
        character.setHskLevel((short) 9);
        character.setAudioUrl("https://example.test/ni.mp3");
        character.setRadical("亻");
        character.setStrokeCount((short) 7);
        character.setFrequency(123);
        lexemes.saveAndFlush(character);
        entityManager.clear();

        Lexeme loaded = lexemes.findById(character.getId()).orElseThrow();
        assertThat(loaded.getPinyin()).isEqualTo("nǐ");
        assertThat(loaded.getPinyinNorm()).isEqualTo("ni");
        assertThat(loaded.getSinoViet()).isEqualTo("nhĩ");
        assertThat(loaded.getMeaningVi()).isEqualTo("bạn");
        assertThat(loaded.getMeaningNorm()).isEqualTo("ban");
        assertThat(loaded.getHskLevel()).isEqualTo((short) 9);
        assertThat(loaded.getAudioUrl()).isEqualTo("https://example.test/ni.mp3");
        assertThat(loaded.getRadical()).isEqualTo("亻");
        assertThat(loaded.getStrokeCount()).isEqualTo((short) 7);
        assertThat(loaded.getFrequency()).isEqualTo(123);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void FR034_AT040_pronunciationUnitsAndTopicPrerequisitesRoundTripWithoutSchemaChanges() {
        Topic prerequisite = topics.saveAndFlush(new Topic("PREREQ_FIRST", "Chủ đề trước", (short) 1));
        Topic topic = new Topic("PREREQ_NEXT", "Chủ đề tiếp theo", (short) 2);
        topic.setOrderIndex(12);
        topic.setPrereqIds(new Long[] { prerequisite.getId() });
        topics.saveAndFlush(topic);

        PronUnit unit = new PronUnit("Z01", "https://example.test/z.mp3", "Chọn âm nghe được",
                List.of(new PronUnit.Option("A", "z"), new PronUnit.Option("B", "zh")), "A", "Âm z");
        PronStage stage = new PronStage((short) 1, "Phân biệt z và zh");
        stage.setDescription("Bài luyện tự tạo");
        stage.setTargetSounds(List.of("z", "zh"));
        stage.setPassPercent(new BigDecimal("88.25"));
        stage.setUnits(List.of(unit));
        pronStages.saveAndFlush(stage);
        entityManager.clear();

        Topic loadedTopic = topics.findById(topic.getId()).orElseThrow();
        assertThat(loadedTopic.getPrereqIds()).containsExactly(prerequisite.getId());
        assertThat(loadedTopic.getOrderIndex()).isEqualTo(12);
        assertThat(loadedTopic.getName()).isEqualTo("Chủ đề tiếp theo");
        assertThat(loadedTopic.getCreatedAt()).isNotNull();
        PronStage loaded = pronStages.findById(stage.getId()).orElseThrow();
        assertThat(loaded.getUnits()).containsExactly(unit);
        assertThat(loaded.getStageIndex()).isEqualTo((short) 1);
        assertThat(loaded.getDescription()).isEqualTo("Bài luyện tự tạo");
        assertThat(loaded.getTargetSounds()).containsExactly("z", "zh");
        assertThat(loaded.getPassPercent()).isEqualByComparingTo("88.25");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        loaded.setName("Tên tầng đã sửa");
        pronStages.saveAndFlush(loaded);
        entityManager.clear();
        PronStage reloaded = pronStages.findById(stage.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Tên tầng đã sửa");
        assertThat(reloaded.getUnits()).containsExactly(unit);
    }

    @Test
    void FR034_emptyJsonAndArrayDefaultsMatchTheMigratedDatabase() {
        Lexeme lexeme = lexemes.saveAndFlush(lexeme(LexemeKind.CHAR, "我"));
        Topic topic = topics.saveAndFlush(new Topic("EMPTY_DEFAULTS", "Mặc định", (short) 1));
        PronStage stage = pronStages.saveAndFlush(new PronStage((short) 2, "Mặc định"));
        entityManager.clear();

        assertThat(lexemes.findById(lexeme.getId()).orElseThrow().getExtra()).isEqualTo(LexemeExtra.empty());
        assertThat(lexemes.findById(lexeme.getId()).orElseThrow().getFrequency()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT extra::text FROM learning.lexemes WHERE id=?",
                String.class, lexeme.getId())).isEqualTo("{}");
        assertThat(topics.findById(topic.getId()).orElseThrow().getPrereqIds()).isEmpty();
        PronStage loaded = pronStages.findById(stage.getId()).orElseThrow();
        assertThat(loaded.getTargetSounds()).isEmpty();
        assertThat(loaded.getUnits()).isEmpty();
        assertThat(loaded.getPassPercent()).isEqualByComparingTo("80.00");
    }

    @Test
    void FR012_FR021_importRequestJsonAndUtcTimesRoundTrip() throws Exception {
        long userId = registeredUser();
        ServiceRequest request = new ServiceRequest(userId, ServiceRequestKind.IMPORT);
        request.setPayload(Map.of("dataType", "LEXEME", "fileName", "self-created.csv", "totalRows", 3));
        request.setFileHash("a".repeat(64));
        requests.saveAndFlush(request);
        entityManager.clear();

        ServiceRequest loaded = requests.findByIdAndKind(request.getId(), ServiceRequestKind.IMPORT).orElseThrow();
        assertThat(loaded.getUserId()).isEqualTo(userId);
        assertThat(loaded.getKind()).isEqualTo(ServiceRequestKind.IMPORT);
        assertThat(loaded.getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        assertThat(loaded.getRetryCount()).isZero();
        assertThat(loaded.getPayload()).isEqualTo(request.getPayload());
        assertThat(loaded.getResult()).isNull();
        assertThat(loaded.getErrorRows()).isNull();
        assertThat(loaded.getCompletedAt()).isNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getFileHash()).isEqualTo("a".repeat(64));

        Instant completedAt = Instant.parse("2026-10-29T08:15:30Z");
        loaded.setStatus(ServiceRequestStatus.COMPLETED);
        loaded.setResult(Map.of("created", 2, "failed", 1));
        loaded.setErrorRows(List.of(Map.of("line", 3, "column", "text", "code", "BROKEN_TEXT")));
        loaded.setRetryCount((short) 1);
        loaded.setErrorMessage("ROW_WRITE_FAILED");
        loaded.setCompletedAt(completedAt);
        requests.saveAndFlush(loaded);
        entityManager.clear();

        ServiceRequest completed = requests.findById(request.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ServiceRequestStatus.COMPLETED);
        assertThat(completed.getPayload()).isEqualTo(request.getPayload());
        assertThat(completed.getResult()).containsEntry("created", 2).containsEntry("failed", 1);
        assertThat(completed.getErrorRows()).containsExactly(
                Map.of("line", 3, "column", "text", "code", "BROKEN_TEXT"));
        assertThat(completed.getRetryCount()).isEqualTo((short) 1);
        assertThat(completed.getErrorMessage()).isEqualTo("ROW_WRITE_FAILED");
        assertThat(completed.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void FR012_sharedServiceRequestMapsOptionalAssignmentFieldsUsingSqlTypes() throws Exception {
        long userId = registeredUser();
        long assigneeId = registeredUser();
        Instant assignedAt = Instant.parse("2026-10-29T08:00:00Z");
        Instant deadlineAt = Instant.parse("2026-10-30T08:00:00Z");
        ServiceRequest request = new ServiceRequest(userId, ServiceRequestKind.GRADING);
        request.setStatus(ServiceRequestStatus.ASSIGNED);
        request.setAssigneeId(assigneeId);
        request.setAssignedAt(assignedAt);
        request.setDeadlineAt(deadlineAt);
        request.setScore((short) 87);
        request.setFeedback("Nhận xét tự tạo");
        request.setPayout(12);
        requests.saveAndFlush(request);
        entityManager.clear();

        ServiceRequest loaded = requests.findById(request.getId()).orElseThrow();
        assertThat(loaded.getAssigneeId()).isEqualTo(assigneeId);
        assertThat(loaded.getAssignedAt()).isEqualTo(assignedAt);
        assertThat(loaded.getDeadlineAt()).isEqualTo(deadlineAt);
        assertThat(loaded.getScore()).isEqualTo((short) 87);
        assertThat(loaded.getFeedback()).isEqualTo("Nhận xét tự tạo");
        assertThat(loaded.getPayout()).isEqualTo(12);
        assertThat(loaded.getPayload()).isEmpty();
    }

    @Test
    void FR029_requestHistoryFiltersKindAndSortsBeforePagination() throws Exception {
        long userId = registeredUser();
        ServiceRequest oldest = requests.saveAndFlush(new ServiceRequest(userId, ServiceRequestKind.IMPORT));
        requests.saveAndFlush(new ServiceRequest(userId, ServiceRequestKind.TRANSLATE));
        ServiceRequest completed = new ServiceRequest(userId, ServiceRequestKind.IMPORT);
        completed.setStatus(ServiceRequestStatus.COMPLETED);
        completed.setFileHash("b".repeat(64));
        requests.saveAndFlush(completed);
        ServiceRequest newest = requests.saveAndFlush(new ServiceRequest(userId, ServiceRequestKind.IMPORT));
        entityManager.clear();

        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Page<ServiceRequest> firstPage = requests.findByKind(ServiceRequestKind.IMPORT,
                PageRequest.of(0, 2, newestFirst));
        Page<ServiceRequest> secondPage = requests.findByKind(ServiceRequestKind.IMPORT,
                PageRequest.of(1, 2, newestFirst));
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(ServiceRequest::getId)
                .containsExactly(newest.getId(), completed.getId());
        assertThat(secondPage.getContent()).extracting(ServiceRequest::getId).containsExactly(oldest.getId());
        assertThat(requests.findByIdAndKind(oldest.getId(), ServiceRequestKind.TRANSLATE)).isEmpty();
        assertThat(requests.findByKindAndStatus(ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING))
                .extracting(ServiceRequest::getId).containsExactlyInAnyOrder(oldest.getId(), newest.getId());
        assertThat(requests.existsByKindAndStatusAndFileHash(
                ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, "b".repeat(64))).isTrue();
        assertThat(requests.existsByKindAndStatusAndFileHash(
                ServiceRequestKind.IMPORT, ServiceRequestStatus.RUNNING, "b".repeat(64))).isFalse();
    }

    @Test
    void FR017_runningImportUniquenessIsEnforcedWhenSavingEntities() throws Exception {
        long firstUser = registeredUser();
        long secondUser = registeredUser();
        ServiceRequest first = new ServiceRequest(firstUser, ServiceRequestKind.IMPORT);
        first.setStatus(ServiceRequestStatus.RUNNING);
        requests.saveAndFlush(first);
        ServiceRequest second = new ServiceRequest(secondUser, ServiceRequestKind.IMPORT);
        second.setStatus(ServiceRequestStatus.RUNNING);

        assertThatThrownBy(() -> requests.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_import_running");
    }

    @Test
    void FR013_lexemeNaturalKeyRejectsDuplicateKindAndText() {
        lexemes.saveAndFlush(lexeme(LexemeKind.CHAR, "我"));

        assertThatThrownBy(() -> lexemes.saveAndFlush(lexeme(LexemeKind.CHAR, "我")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_lexeme");
    }

    private Lexeme lexeme(LexemeKind kind, String text) {
        Lexeme lexeme = new Lexeme(kind, text, "Nghĩa tự tạo");
        lexeme.setHskLevel((short) 1);
        if (kind != LexemeKind.GRAMMAR) {
            lexeme.setPinyin("wǒ");
            lexeme.setPinyinNorm("wo");
        }
        return lexeme;
    }

    private long registeredUser() throws Exception {
        String password = UUID.randomUUID() + "Aa1";
        String body = objectMapper.writeValueAsString(Map.of(
                "email", "content-repository-" + UUID.randomUUID() + "@example.test",
                "password", password, "confirmPassword", password,
                "displayName", "Người dùng kiểm thử", "acceptTerms", true));
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("userId").asLong();
    }
}
