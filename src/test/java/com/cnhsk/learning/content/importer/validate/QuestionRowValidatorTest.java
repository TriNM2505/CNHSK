package com.cnhsk.learning.content.importer.validate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.entity.Question;
import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import com.cnhsk.learning.knowledge.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class QuestionRowValidatorTest {
    private KnowledgePointRepository points;
    private QuestionRepository questions;
    private QuestionRowValidator validator;

    @BeforeEach
    void setup() {
        points = mock(KnowledgePointRepository.class); questions = mock(QuestionRepository.class);
        validator = new QuestionRowValidator(points, questions);
        when(points.findByCodeIn(any())).thenReturn(List.of(node("TV1-001", 1), node("TV1-002", 2), node("NP2-003", 3)));
    }

    @ParameterizedTest
    @EnumSource(value = QuestionType.class, names = "ESSAY", mode = EnumSource.Mode.EXCLUDE)
    void AT09_allSixTypesAcceptSelfAuthoredRows(QuestionType type) {
        assertThat(validator.dataType()).isEqualTo(ImportDataType.QUESTION);
        assertThat(validate(values(type)).errors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "qtype", "hsk_level", "stem", "correct", "explanation", "kid_primary"})
    void AT09_AT10_requiredCellsMustBePresent(String field) {
        Map<String, String> values = values(QuestionType.MULTIPLE_CHOICE); values.put(field, "  ");
        assertError(validate(values), field, "REQUIRED_MISSING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "lower_case", "Q SPACE", "Q!X"})
    void AT09_questionCodeHasTheApprovedUppercaseNaturalKeyFormat(String code) {
        assertError(change("code", code), "code", "INVALID_VALUE");
    }

    @Test
    void AT09_rangesLengthsAndUnknownTypeKeepTheirContractCodes() {
        assertError(change("code", "Q".repeat(51)), "code", "TOO_LONG");
        assertError(change("hsk_level", "10"), "hsk_level", "INVALID_HSK_LEVEL");
        assertError(change("hsk_level", "abc"), "hsk_level", "INVALID_HSK_LEVEL");
        assertError(change("difficulty", "4"), "difficulty", "INVALID_VALUE");
        assertError(change("stem", "文".repeat(2001)), "stem", "TOO_LONG");
        assertError(change("explanation", "文".repeat(3001)), "explanation", "TOO_LONG");
        assertError(change("qtype", "OTHER"), "qtype", "INVALID_VALUE");
        assertError(change("qtype", "ESSAY"), "qtype", "INVALID_VALUE");
        assertError(change("audio_url", "relative.mp3"), "audio_url", "INVALID_URL");
        assertError(change("correct", "F"), "correct", "INVALID_ANSWER");
        assertThat(change("hsk_level", "9").errors()).isEmpty();
    }

    @Test
    void AT09_eachTypeSpecificRejectionUsesTheCorrectCsvField() {
        var truth = values(QuestionType.TRUE_FALSE); truth.put("option_a", "Không được nhập");
        assertError(validate(truth), "option_a", "INVALID_VALUE");
        var fill = values(QuestionType.FILL_BLANK); fill.put("stem", "没有空白");
        assertError(validate(fill), "stem", "INVALID_VALUE");
        fill = values(QuestionType.FILL_BLANK); fill.put("correct", "文".repeat(51));
        assertError(validate(fill), "correct", "INVALID_ANSWER");
        var order = values(QuestionType.SENTENCE_ORDER); order.put("correct", "AB");
        assertError(validate(order), "correct", "INVALID_ANSWER");
        order = values(QuestionType.SENTENCE_ORDER); order.put("option_c", "我");
        assertError(validate(order), "option_a", "INVALID_VALUE");
        var image = values(QuestionType.IMAGE_MATCH); image.put("image_url", "");
        assertError(validate(image), "image_url", "REQUIRED_MISSING");
    }

    @Test
    void AT10_normalizesLabelsAndRejectsMissingDuplicateOrTooManyKids() {
        assertThat(change("kid_primary", " tv1-001 ").validRows()).singleElement()
                .satisfies(row -> assertThat(row.values()).containsEntry("kid_primary", "TV1-001"));
        assertError(change("kid_primary", "MISSING"), "kid_primary", "REFERENCE_NOT_FOUND");
        assertError(change("kid_secondary", "MISSING"), "kid_secondary", "REFERENCE_NOT_FOUND");
        assertError(change("kid_secondary", "TV1-002|NP2-003|TV1-001"), "kid_secondary", "TOO_MANY_SECONDARY_KIDS");
        assertError(change("kid_secondary", "tv1-001"), "kid_secondary", "DUPLICATE_KID");
        assertError(change("kid_secondary", "TV1-002|tv1-002"), "kid_secondary", "DUPLICATE_KID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"A:TV1-001", "G:TV1-001", "B:", "B:TV1-001|B:TV1-002", "B:TV1-001||C:TV1-002",
            "B:MISSING", "wrong", "B:TV1-001|B:TV1-002|B:TV1-001|B:TV1-002|B:TV1-001|B:TV1-002|B:TV1-001"})
    void AT10_malformedCorrectOrUnknownDistractorsAreInvalidValues(String errors) {
        assertError(change("distractor_errors", errors), "distractor_errors", "INVALID_VALUE");
    }

    @Test
    void AT10_distractorNotesMayContainColonsButHaveAtMostTwoHundredCodePoints() {
        assertThat(change("distractor_errors", "B:tv1-002:Ghi chú: tự soạn").errors()).isEmpty();
        assertError(change("distractor_errors", "B:TV1-002:" + "文".repeat(201)), "distractor_errors", "INVALID_VALUE");
        var truth = values(QuestionType.TRUE_FALSE); truth.put("distractor_errors", "B:TV1-002");
        assertError(validate(truth), "distractor_errors", "INVALID_VALUE");
    }

    @Test
    void AT12_existingOptionalScalarsAreMergedBeforeValidationAndGradingIsChecked() {
        Question previous = existing("Q_IMPORT", 9);
        when(questions.findByCodeIn(any())).thenReturn(List.of(previous));
        when(questions.usedQuestionIds(any())).thenReturn(Set.of(9L));
        assertError(change("correct", "B"), "correct", "GRADING_CHANGE_NOT_ALLOWED");
        assertThat(change("explanation", "Lời giải mới").errors()).isEmpty();
        previous.setQtype(QuestionType.IMAGE_MATCH); previous.setImageUrl("https://example.test/kept.png"); previous.setDifficulty((short) 3);
        var image = values(QuestionType.IMAGE_MATCH); image.put("image_url", ""); image.put("difficulty", "");
        assertThat(validate(image).errors()).isEmpty();
        assertThat(validate(image).validRows()).singleElement().satisfies(row -> assertThat(row.values()).containsEntry("difficulty", ""));
    }

    @Test
    void AT09_firstValidNaturalKeyWinsAndEarlierInvalidRowsDoNotReserveIt() {
        var invalid = values(QuestionType.MULTIPLE_CHOICE); invalid.put("correct", "F");
        var input = new CsvReader.ReadResult(3, List.of(new CsvReader.Row(9, values(QuestionType.MULTIPLE_CHOICE)),
                new CsvReader.Row(2, invalid), new CsvReader.Row(6, values(QuestionType.MULTIPLE_CHOICE))), List.of());
        var result = validator.validate(input);
        assertThat(result.validRows()).singleElement().extracting(RowValidationResult.ValidatedRow::line).isEqualTo(6);
        assertThat(result.errors()).extracting(CsvReader.RowError::code).containsExactly("INVALID_ANSWER", "DUPLICATE_IN_FILE");
        assertThat(result.errors().get(1).message()).contains("6");
    }

    @Test
    void AT10_kidsUseOneQueryAndExistingQuestionsAndUsageUseFiveHundredRowBatches() {
        List<CsvReader.Row> rows = IntStream.range(0, 1201).mapToObj(index -> {
            var cells = values(QuestionType.MULTIPLE_CHOICE); cells.put("code", "Q_BATCH_" + index);
            return new CsvReader.Row(index + 2, cells);
        }).toList();
        when(questions.findByCodeIn(any())).thenAnswer(call -> {
            List<String> codes = List.copyOf(call.getArgument(0));
            assertThat(codes).hasSizeLessThanOrEqualTo(500);
            return codes.stream().map(code -> existing(code, Math.abs((long) code.hashCode()) + 1)).toList();
        });
        var result = validator.validate(new CsvReader.ReadResult(1201, rows, List.of()));
        assertThat(result.errors()).isEmpty(); assertThat(result.validRows()).hasSize(1201);
        verify(points, times(1)).findByCodeIn(Set.of("TV1-001"));
        verify(questions, times(3)).findByCodeIn(any()); verify(questions, times(3)).usedQuestionIds(any());
        verify(questions, never()).findByCode(any()); verify(questions, never()).saveAndFlush(any());
    }

    @Test
    void metadataKeepsElevenTemplatesAndTheApprovedQuestionColumnLayout() {
        assertThat(ImportTemplate.all()).hasSize(13);
        var template = ImportTemplate.forType(ImportDataType.QUESTION);
        assertThat(template.naturalKey()).containsExactly("code");
        assertThat(template.dependsOn()).containsExactly(ImportDataType.KNOWLEDGE_NODE);
        assertThat(template.columns()).extracting(column -> column.name()).containsExactly("code", "qtype", "hsk_level", "difficulty", "stem",
                "option_a", "option_b", "option_c", "option_d", "option_e", "option_f", "correct", "explanation", "audio_url", "image_url",
                "kid_primary", "kid_secondary", "distractor_errors");
        template.columns().stream().filter(column -> column.name().startsWith("option_") || Set.of("kid_secondary", "distractor_errors").contains(column.name()))
                .forEach(column -> assertThat(column.description()).contains("để trống", "xoá"));
        template.columns().stream().filter(column -> Set.of("difficulty", "audio_url", "image_url").contains(column.name()))
                .forEach(column -> assertThat(column.description()).contains("để trống giữ giá trị cũ"));
    }

    public static Map<String, String> values(QuestionType type) {
        Map<String, String> values = new LinkedHashMap<>(Map.of("code", "Q_IMPORT", "qtype", type.name(), "hsk_level", "1",
                "stem", "Chọn đại từ trong câu tự soạn。", "explanation", "Lời giải tự soạn", "correct", "A", "kid_primary", "TV1-001"));
        if (type == QuestionType.TRUE_FALSE) values.put("correct", "TRUE");
        else if (type == QuestionType.FILL_BLANK) { values.put("stem", "我___学习。"); values.put("correct", "在"); }
        else {
            values.put("option_a", "我"); values.put("option_b", "你");
            if (type == QuestionType.SENTENCE_ORDER) { values.put("option_b", "今天"); values.put("option_c", "学习"); values.put("correct", "CAB"); }
            if (type == QuestionType.IMAGE_MATCH) values.put("image_url", "https://example.test/image.png");
        }
        return values;
    }
    public static Question existing(String code, long id) {
        Question question = new Question(code, QuestionType.MULTIPLE_CHOICE, (short) 1, "Chọn đại từ trong câu tự soạn。");
        question.setOptions(List.of(new QuestionOption("A", "我", true, null, null), new QuestionOption("B", "你", false, null, null)));
        question.setExplanation("Lời giải tự soạn"); ReflectionTestUtils.setField(question, "id", id); return question;
    }
    public static KnowledgePoint node(String kid, long id) {
        KnowledgePoint point = new KnowledgePoint(kid, "Nút tự soạn", KnowledgeDomain.valueOf(kid.substring(0, 2)), "示例", (short) (kid.charAt(2) - '0'));
        ReflectionTestUtils.setField(point, "id", id); return point;
    }
    private RowValidationResult change(String field, String value) {
        var values = values(QuestionType.MULTIPLE_CHOICE); values.put(field, value); return validate(values);
    }
    private RowValidationResult validate(Map<String, String> values) {
        return validator.validate(new CsvReader.ReadResult(1, List.of(new CsvReader.Row(2, values)), List.of()));
    }
    private static void assertError(RowValidationResult result, String field, String code) {
        assertThat(result.errors()).anySatisfy(error -> { assertThat(error.column()).isEqualTo(field); assertThat(error.code()).isEqualTo(code); });
    }
}
