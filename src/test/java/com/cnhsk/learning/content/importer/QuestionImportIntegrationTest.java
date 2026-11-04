package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.QuestionRowValidator;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.importer.write.QuestionRowWriter;
import com.cnhsk.learning.exam.QuestionItemFactory;
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
import com.cnhsk.shared.audit.AuditLogRepository;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(QuestionImportIntegrationTest.SqlConfiguration.class)
class QuestionImportIntegrationTest extends AbstractIntegrationTest {
    private static final String ROOT = "/api/admin/imports";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private QuestionRepository questions;
    @Autowired private QuestionKnowledgePointRepository labels;
    @Autowired private KnowledgePointRepository points;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CsvReader reader;
    @Autowired private QuestionRowValidator validator;
    @Autowired private ImportChangePlanner planner;
    @Autowired private SqlCapture capture;
    @MockitoSpyBean private QuestionRowWriter writer;
    private QuestionRowWriter writerTarget;
    private User user;
    private Set<Long> questionBaseline;
    private Set<Long> pointBaseline;

    @BeforeEach
    void setup() {
        questionBaseline = questions.findAll().stream().map(Question::getId).collect(Collectors.toSet());
        pointBaseline = points.findAll().stream().map(KnowledgePoint::getId).collect(Collectors.toSet());
        user = users.saveAndFlush(new User("question-import-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Người nhập câu"));
        for (String kid : List.of("TV1-801", "TV1-802", "NP2-803", "CH3-804")) {
            points.saveAndFlush(new KnowledgePoint(kid, "Nút tự soạn " + kid, KnowledgeDomain.valueOf(kid.substring(0, 2)),
                    "示例", (short) (kid.charAt(2) - '0')));
        }
        writerTarget = AopTestUtils.getUltimateTargetObject(writer);
    }

    @AfterEach
    void cleanup() {
        reset(writerTarget);
        jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN (SELECT id FROM learning.attempts WHERE user_id=?)", user.getId());
        jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", user.getId());
        jdbc.update("DELETE FROM learning.exam_questions WHERE exam_id IN (SELECT id FROM learning.exams WHERE created_by=?)", user.getId());
        jdbc.update("DELETE FROM learning.exams WHERE created_by=?", user.getId());
        Set<Long> created = questions.findAll().stream().map(Question::getId).filter(id -> !questionBaseline.contains(id)).collect(Collectors.toSet());
        if (!created.isEmpty()) jdbc.update("DELETE FROM learning.question_knowledge_points WHERE question_id=ANY(?)", (Object) created.toArray(Long[]::new));
        questions.deleteAllByIdInBatch(created);
        points.deleteAllByIdInBatch(points.findAll().stream().map(KnowledgePoint::getId).filter(id -> !pointBaseline.contains(id)).toList());
        auditLogs.deleteAllInBatch(auditLogs.findAll().stream().filter(log -> user.getId().equals(log.getActorId())).toList());
        requests.deleteAllInBatch(requests.findAll().stream().filter(run -> user.getId().equals(run.getUserId())).toList());
        users.deleteById(user.getId());
    }

    @Test
    void AT09_AT11_previewWritesNoContentThenSixTypesAreStoredWithCanonicalOptionsAndLabels() throws Exception {
        JsonNode beforeQuestions = questionSnapshot(); JsonNode beforeLabels = labelSnapshot();
        JsonNode preview = upload(fixture("questions-valid.csv"));
        assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(6);
        assertThat(preview.path("totals").path("create").asInt()).isEqualTo(6);
        assertThat(questionSnapshot()).isEqualTo(beforeQuestions); assertThat(labelSnapshot()).isEqualTo(beforeLabels);
        JsonNode result = confirm(preview);
        assertThat(result.path("result").path("created").asInt()).isEqualTo(6);
        assertThat(result.path("result").path("failed").asInt()).isZero();
        for (Question question : questions.findAll()) {
            if (questionBaseline.contains(question.getId())) continue;
            assertThat(question.getStatus()).isEqualTo(QuestionStatus.APPROVED);
            assertThat(question.getSource()).isEqualTo(com.cnhsk.learning.knowledge.entity.QuestionSource.IMPORT);
            assertThat(question.isReusable()).isTrue(); assertThat(question.getDifficulty()).isEqualTo((short) 2);
            assertThat(labels.findByQuestionIdIn(List.of(question.getId()))).filteredOn(label -> label.getRole() == KnowledgeLabelRole.PRIMARY).hasSize(1);
        }
        assertThat(question("T08_TRUE").getOptions()).extracting(QuestionOption::key).containsExactly("A", "B");
        assertThat(question("T08_TRUE").getOptions()).extracting(QuestionOption::text).containsExactly("Đúng", "Sai");
        assertThat(question("T08_FILL").getOptions()).isEmpty(); assertThat(question("T08_FILL").getCorrectKey()).isEqualTo("在");
        assertThat(question("T08_ORDER").getOptions()).noneMatch(QuestionOption::isCorrect);
        assertThat(question("T08_ORDER").getCorrectKey()).isEqualTo("CAB");
        JsonNode stored = mapper.readTree(jdbc.queryForObject("SELECT options::text FROM learning.questions WHERE code=?", String.class, "T08_MCQ"));
        assertThat(stored.get(1).path("error_kid").asText()).isEqualTo("TV1-802");
        assertThat(stored.get(1).path("error_note").asText()).contains("Nhầm đại từ: ví dụ tự soạn");
        assertThat(stored.get(0).has("is_correct")).isTrue(); assertThat(stored.get(0).has("isCorrect")).isFalse();
    }

    @Test
    void AT09_AT10_invalidFixtureKeepsAllContractErrorsAndDoesNotWriteContent() throws Exception {
        JsonNode before = questionSnapshot(); JsonNode beforeLabels = labelSnapshot();
        JsonNode preview = upload(fixture("questions-invalid.csv"));
        assertThat(preview.path("totals").path("valid").asInt()).isZero();
        assertThat(preview.path("totals").path("invalid").asInt()).isEqualTo(12);
        assertThat(codes(preview.path("errors"))).containsExactly("INVALID_ANSWER", "INVALID_VALUE", "INVALID_VALUE",
                "INVALID_ANSWER", "REQUIRED_MISSING", "INVALID_VALUE", "REQUIRED_MISSING", "TOO_MANY_SECONDARY_KIDS",
                "DUPLICATE_KID", "INVALID_VALUE", "INVALID_VALUE", "REFERENCE_NOT_FOUND");
        assertThat(questionSnapshot()).isEqualTo(before); assertThat(labelSnapshot()).isEqualTo(beforeLabels);
    }

    @Test
    void AT11_questionItemFactoryFiltersThePersistedQuestionsWithoutRestrictingImportTypes() throws Exception {
        confirm(upload(fixture("questions-valid.csv")));
        QuestionItemFactory factory = new QuestionItemFactory();
        for (Question question : questions.findAll()) {
            if (questionBaseline.contains(question.getId())) continue;
            boolean expected = question.getQtype() == QuestionType.MULTIPLE_CHOICE || question.getQtype() == QuestionType.TRUE_FALSE;
            assertThat(factory.isUsable(question)).as(question.getCode()).isEqualTo(expected);
            assertThat(factory.create(question, 1).isPresent()).isEqualTo(expected);
        }
        Question mcq = question("T08_MCQ"); assertThat(factory.create(mcq, 1).orElseThrow().answer()).isEqualTo("A");
        mcq.setAudioUrl("https://example.test/audio.mp3"); questions.saveAndFlush(mcq); assertThat(factory.isUsable(question("T08_MCQ"))).isFalse();
        Question truth = question("T08_TRUE"); truth.setStatus(QuestionStatus.ARCHIVED); questions.saveAndFlush(truth);
        assertThat(factory.isUsable(question("T08_TRUE"))).isFalse();
    }

    @Test
    void AT12_newUsageAfterPreviewRejectsOnlyTheGradingRowAndPreservesArchivedState() throws Exception {
        confirm(upload(fixture("questions-valid.csv")));
        Question archived = question("T08_TRUE"); archived.setStatus(QuestionStatus.ARCHIVED); questions.saveAndFlush(archived);
        JsonNode preview = upload(fixture("questions-update.csv"));
        assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(3);
        use(question("T08_MCQ").getId());
        Question original = question("T08_MCQ"); JsonNode before = mapper.valueToTree(original);
        JsonNode result = confirm(preview);
        assertThat(result.path("result").path("failed").asInt()).isEqualTo(1);
        assertThat(result.path("result").path("updated").asInt()).isEqualTo(2);
        assertThat(mapper.<JsonNode>valueToTree(question("T08_MCQ"))).isEqualTo(before);
        assertThat(question("T08_TRUE").getStatus()).isEqualTo(QuestionStatus.ARCHIVED);
        assertThat(question("T08_TRUE").getExplanation()).isEqualTo("Lời giải mới cho câu đã lưu trữ");
        assertThat(codes(errors(preview).path("items"))).containsExactly("GRADING_CHANGE_NOT_ALLOWED");
    }

    @Test
    void AT12_primaryAndSecondaryCanSwapWithoutViolatingTheUniquePrimaryConstraint() throws Exception {
        confirm(upload(fixture("questions-valid.csv")));
        Map<String, String> cells = example("T08_MCQ"); cells.put("kid_primary", "TV1-802"); cells.put("kid_secondary", "TV1-801");
        JsonNode preview = upload(csv(List.of(cells))); assertThat(confirm(preview).path("result").path("updated").asInt()).isEqualTo(1);
        Map<Long, KnowledgeLabelRole> roles = labels.findByQuestionIdIn(List.of(question("T08_MCQ").getId())).stream()
                .collect(Collectors.toMap(QuestionKnowledgePoint::getKnowledgePointId, QuestionKnowledgePoint::getRole));
        assertThat(roles).containsExactlyInAnyOrderEntriesOf(Map.of(point("TV1-802").getId(), KnowledgeLabelRole.PRIMARY,
                point("TV1-801").getId(), KnowledgeLabelRole.SECONDARY));
    }

    @Test
    void AT11_labelConstraintFailureRollsBackTheQuestionAndLabelsThenContinuesTheNextRow() throws Exception {
        JsonNode preview = upload(csv(List.of(example("Q_LABEL_FAIL"), example("Q_NEXT_OK"))));
        AtomicLong failedId = new AtomicLong();
        doAnswer(call -> {
            var action = call.callRealMethod();
            ValidatedRow row = call.getArgument(0);
            if (row.line() == 2) {
                long id = question("Q_LABEL_FAIL").getId(); failedId.set(id);
                labels.saveAndFlush(new QuestionKnowledgePoint(id, point("TV1-802").getId(), KnowledgeLabelRole.PRIMARY));
            }
            return action;
        }).when(writerTarget).write(any());
        JsonNode completed = confirm(preview);
        assertThat(completed.path("result").path("failed").asInt()).isEqualTo(1);
        assertThat(completed.path("result").path("created").asInt()).isEqualTo(1);
        assertThat(questions.findByCode("Q_LABEL_FAIL")).isEmpty(); assertThat(labels.findByQuestionIdIn(List.of(failedId.get()))).isEmpty();
        assertThat(labels.findByQuestionIdIn(List.of(question("Q_NEXT_OK").getId()))).hasSize(1);
        assertThat(codes(errors(preview).path("items"))).containsExactly("ROW_WRITE_FAILED");
        assertThat(errors(preview).toString()).doesNotContain("uq_qkp_primary", "SQL", "ConstraintViolationException");
    }

    @Test
    void AT12_blankReplacementDropsOldStructuresWhileOptionalScalarsRemain() throws Exception {
        confirm(upload(fixture("questions-valid.csv")));
        Question previous = question("T08_MCQ"); previous.setDifficulty((short) 3);
        previous.setAudioUrl("https://example.test/kept.mp3"); previous.setImageUrl("https://example.test/kept.png");
        var options = new ArrayList<>(previous.getOptions()); options.add(new QuestionOption("C", "他", false, "NP2-803", "Nhãn cũ"));
        previous.setOptions(options); questions.saveAndFlush(previous);
        var cells = example("T08_MCQ"); cells.put("difficulty", ""); cells.put("audio_url", ""); cells.put("image_url", "");
        cells.put("kid_secondary", ""); cells.put("distractor_errors", "");
        JsonNode preview = upload(csv(List.of(cells))); assertThat(preview.path("totals").path("update").asInt()).isEqualTo(1);
        assertThat(question("T08_MCQ").getOptions()).hasSize(3);
        assertThat(confirm(preview).path("result").path("updated").asInt()).isEqualTo(1);
        Question updated = question("T08_MCQ");
        assertThat(updated.getOptions()).hasSize(2).allSatisfy(option -> { assertThat(option.errorKid()).isNull(); assertThat(option.errorNote()).isNull(); });
        assertThat(updated.getDifficulty()).isEqualTo((short) 3); assertThat(updated.getAudioUrl()).endsWith("kept.mp3"); assertThat(updated.getImageUrl()).endsWith("kept.png");
        assertThat(labels.findByQuestionIdIn(List.of(updated.getId()))).singleElement().extracting(QuestionKnowledgePoint::getRole).isEqualTo(KnowledgeLabelRole.PRIMARY);
    }

    @Test
    void AT12_knownUsageIsRejectedAtPreviewButExamMembershipAloneDoesNotLockGrading() throws Exception {
        confirm(upload(fixture("questions-valid.csv")));
        use(question("T08_MCQ").getId());
        JsonNode rejected = upload(fixture("questions-update.csv"));
        assertThat(codes(rejected.path("errors"))).containsExactly("GRADING_CHANGE_NOT_ALLOWED");
        Question other = question("T08_SENTENCE");
        long exam = jdbc.queryForObject("INSERT INTO learning.exams(code,title,hsk_level,created_by) VALUES (?,'Đề tự soạn',1,?) RETURNING id",
                Long.class, "T08_EXAM_" + UUID.randomUUID(), user.getId());
        jdbc.update("INSERT INTO learning.exam_questions(exam_id,question_id) VALUES (?,?)", exam, other.getId());
        var cells = example("T08_SENTENCE"); cells.put("correct", "B");
        JsonNode preview = upload(csv(List.of(cells))); assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(1);
        assertThat(confirm(preview).path("result").path("updated").asInt()).isEqualTo(1);
        assertThat(question("T08_SENTENCE").getOptions()).filteredOn(QuestionOption::isCorrect).singleElement().extracting(QuestionOption::key).isEqualTo("B");
    }

    @Test
    void AT12_reimportIsUnchangedAndOptionalImageSurvivesAnEmptyCell() throws Exception {
        confirm(upload(fixture("questions-valid.csv")));
        JsonNode before = questionSnapshot(); JsonNode beforeLabels = labelSnapshot();
        JsonNode repeat = upload(fixture("questions-valid.csv"));
        assertThat(repeat.path("totals").path("unchanged").asInt()).isEqualTo(6);
        assertThat(confirm(repeat).path("result").path("unchanged").asInt()).isEqualTo(6);
        assertThat(questionSnapshot()).isEqualTo(before); assertThat(labelSnapshot()).isEqualTo(beforeLabels);
        var image = example("T08_IMAGE"); image.put("qtype", "IMAGE_MATCH"); image.put("image_url", ""); image.put("explanation", "Lời giải ảnh mới");
        JsonNode preview = upload(csv(List.of(image))); assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(1);
        assertThat(confirm(preview).path("result").path("updated").asInt()).isEqualTo(1);
        assertThat(question("T08_IMAGE").getImageUrl()).endsWith("self-authored-image.png");
    }

    @Test
    void AT11_databaseStillRejectsASecondPrimary() throws Exception {
        confirm(upload(csv(List.of(example("Q_ONE_PRIMARY")))));
        long id = question("Q_ONE_PRIMARY").getId();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO learning.question_knowledge_points(question_id,knowledge_point_id,role) VALUES (?,?,'PRIMARY')",
                id, point("TV1-802").getId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(labels.findByQuestionIdIn(List.of(id))).hasSize(1);
    }

    @Test
    void NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries() throws Exception {
        List<Map<String, String>> values = java.util.stream.IntStream.range(0, 5000).mapToObj(index -> example("Q_PERF_" + index)).toList();
        byte[] input = csv(values); assertThat(input.length).isLessThanOrEqualTo(2 * 1024 * 1024);
        long[] timings = new long[5];
        for (int index = 0; index < timings.length; index++) {
            JsonNode preview = upload(input); assertThat(preview.path("totals").path("valid").asInt()).isEqualTo(5000);
            long started = System.nanoTime(); JsonNode completed = confirm(preview);
            timings[index] = (System.nanoTime() - started) / 1_000_000;
            assertThat(completed.path("result").path("failed").asInt()).isZero();
            assertThat(completed.path("result").path(index == 0 ? "created" : "unchanged").asInt()).isEqualTo(5000);
        }
        long p95 = Arrays.stream(timings).max().orElseThrow();
        System.out.println("T08_QUESTION_CONFIRM_BENCHMARK rows=5000 samples_ms=" + Arrays.toString(timings) + " local_p95_ms=" + p95);
        assertThat(p95).isLessThan(30_000);
        var parsed = reader.read(ImportDataType.QUESTION, "synthetic.csv", input);
        capture.start();
        try {
            var checked = validator.validate(parsed); assertThat(checked.validRows()).hasSize(5000); assertThat(checked.errors()).isEmpty();
            assertThat(capture.statements()).hasSize(21).allSatisfy(sql -> assertThat(sql).startsWith("select"));
            capture.start();
            assertThat(planner.plan(ImportDataType.QUESTION, checked.validRows())).hasSize(5000)
                    .allSatisfy(row -> assertThat(row.action()).isEqualTo(ImportPreview.RowAction.UNCHANGED));
            assertThat(capture.statements()).hasSize(20).allSatisfy(sql -> assertThat(sql).startsWith("select"));
        } finally { capture.stop(); }
    }

    private Question question(String code) { return questions.findByCode(code).orElseThrow(); }
    private KnowledgePoint point(String kid) { return points.findByCode(kid).orElseThrow(); }
    private JsonNode questionSnapshot() { return mapper.valueToTree(questions.findAll().stream().sorted(Comparator.comparing(Question::getId)).toList()); }
    private JsonNode labelSnapshot() { return mapper.valueToTree(labels.findAll().stream().sorted(Comparator.comparing(QuestionKnowledgePoint::getQuestionId)
            .thenComparing(QuestionKnowledgePoint::getKnowledgePointId)).toList()); }
    private long use(long questionId) {
        long attempt = jdbc.queryForObject("INSERT INTO learning.attempts(user_id,kind,ref_type) VALUES (?,'PRACTICE','TOPIC_TEST') RETURNING id", Long.class, user.getId());
        jdbc.update("INSERT INTO learning.attempt_items(attempt_id,question_id,item_type,skill,order_index) VALUES (?,?,'QUESTION_CHOICE','READING',1)", attempt, questionId);
        return attempt;
    }
    private JsonNode upload(byte[] csv) throws Exception {
        return mapper.readTree(mvc.perform(multipart(ROOT).file(new MockMultipartFile("file", "synthetic.csv", "text/csv", csv))
                .param("dataType", "QUESTION").header("Authorization", token())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode confirm(JsonNode preview) throws Exception {
        return mapper.readTree(mvc.perform(post(ROOT + "/" + preview.path("runId").asLong() + "/confirm").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"acknowledgeRerun\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode errors(JsonNode preview) throws Exception {
        return mapper.readTree(mvc.perform(get(ROOT + "/" + preview.path("runId").asLong() + "/errors").header("Authorization", token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private String token() { return "Bearer " + jwt.issueAccessToken(user.getId(), List.of("CONTENT_EDITOR"), true, UUID.randomUUID()); }
    private static byte[] fixture(String name) throws Exception { return new ClassPathResource("import/knowledge/" + name).getContentAsByteArray(); }
    private static List<String> codes(JsonNode errors) { List<String> result = new ArrayList<>(); errors.forEach(error -> result.add(error.path("code").asText())); return result; }
    private static Map<String, String> example(String code) {
        return new java.util.LinkedHashMap<>(Map.of("code", code, "qtype", "MULTIPLE_CHOICE", "hsk_level", "1", "stem", "Câu hỏi từ ví dụ tự soạn。",
                "option_a", "我", "option_b", "你", "correct", "A", "explanation", "Lời giải từ ví dụ tự soạn", "kid_primary", "TV1-801"));
    }
    private static byte[] csv(List<Map<String, String>> rows) {
        List<String> fields = ImportTemplate.forType(ImportDataType.QUESTION).columns().stream().map(column -> column.name()).toList();
        StringBuilder result = new StringBuilder(String.join(",", fields)).append('\n');
        rows.forEach(row -> result.append(fields.stream().map(field -> "\"" + row.getOrDefault(field, "").replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(","))).append('\n'));
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {
        @Bean SqlCapture questionSqlCapture() { return new SqlCapture(); }
        @Bean HibernatePropertiesCustomizer questionStatementInspector(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }
    static class SqlCapture implements StatementInspector {
        private final ThreadLocal<List<String>> active = new ThreadLocal<>();
        void start() { active.set(new ArrayList<>()); }
        List<String> statements() { return List.copyOf(active.get()); }
        void stop() { active.remove(); }
        @Override public String inspect(String sql) { if (active.get() != null) active.get().add(sql.toLowerCase(Locale.ROOT).strip()); return sql; }
    }
}
