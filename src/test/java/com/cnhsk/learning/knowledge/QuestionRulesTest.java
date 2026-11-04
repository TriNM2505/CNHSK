package com.cnhsk.learning.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.knowledge.QuestionRules.Draft;
import com.cnhsk.learning.knowledge.QuestionRules.Option;
import com.cnhsk.learning.knowledge.entity.QuestionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionRulesTest {
    private static final Set<String> KIDS = Set.of("TV1-001", "TV1-002", "NP2-003", "CH3-004");

    @ParameterizedTest
    @EnumSource(value = QuestionType.class, names = "ESSAY", mode = EnumSource.Mode.EXCLUDE)
    void AT09_sixTypesAreValidInTheSharedCanonicalRepresentation(QuestionType type) {
        var result = QuestionRules.validate(draft(type), KIDS);
        assertThat(result.errors()).isEmpty();
        assertThat(result.value().qtype()).isEqualTo(type);
        if (type == QuestionType.TRUE_FALSE) assertThat(result.value().options()).extracting(Option::key).containsExactly("A", "B");
    }

    @Test
    void AT09_essayAndUnknownTypesAreRejected() {
        assertCode(draft(QuestionType.ESSAY), "INVALID_VALUE");
        assertCode(draft(null), "INVALID_VALUE");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7})
    void AT09_choiceOptionCountIsBetweenTwoAndSix(int count) {
        var options = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new Option(Character.toString('A' + index), "Phương án " + index, index == 0, null, null)).toList();
        assertCode(options(draft(QuestionType.MULTIPLE_CHOICE), options, null), "INVALID_VALUE");
    }

    @Test
    void AT09_choiceKeysMustBeContiguousAndTheAnswerMustBeUnique() {
        var base = draft(QuestionType.MULTIPLE_CHOICE);
        assertCode(options(base, List.of(option("A", "我", true), option("C", "你", false)), null), "INVALID_VALUE");
        assertCode(options(base, List.of(option("A", "我", false), option("B", "你", false)), null), "INVALID_ANSWER");
        assertCode(options(base, List.of(option("A", "我", true), option("B", "你", true)), null), "INVALID_ANSWER");
        assertCode(options(base, List.of(option("A", "", true), option("B", "你", false)), null), "REQUIRED_MISSING");
        assertCode(options(base, List.of(option("A", "文".repeat(301), true), option("B", "你", false)), null), "TOO_LONG");
    }

    @ParameterizedTest
    @ValueSource(strings = {"我  学习", "我\t学习", "我\u00a0学习"})
    void AT09_duplicateTextsAreComparedAfterWhitespaceNormalization(String text) {
        assertCode(options(draft(QuestionType.MULTIPLE_CHOICE), List.of(option("A", text, true),
                option("B", "我 学习", false)), null), "INVALID_VALUE");
    }

    @Test
    void AT09_trueFalseHasExactlyTheFixedPairAndOneFlaggedAnswer() {
        var base = draft(QuestionType.TRUE_FALSE);
        assertCode(options(base, List.of(option("A", "Đúng", true), option("C", "Sai", false)), null), "INVALID_VALUE");
        assertCode(options(base, List.of(option("A", "TRUE", true), option("B", "FALSE", false)), null), "INVALID_VALUE");
        assertCode(options(base, List.of(option("A", "Đúng", false), option("B", "Sai", false)), null), "INVALID_ANSWER");
    }

    @Test
    void T12_decomposedUnicodeDoesNotHideDuplicateOptionContent() {
        assertCode(options(draft(QuestionType.MULTIPLE_CHOICE),
                List.of(option("A", "café", true), option("B", "cafe\u0301", false)), null), "INVALID_VALUE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "CAB", "BCA"})
    void AT09_sentenceOrderUsesEveryFragmentKeyOnce(String answer) {
        var base = draft(QuestionType.SENTENCE_ORDER);
        assertThat(QuestionRules.validate(options(base, base.options(), answer), KIDS).errors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "AB", "ABCD", "AAC", "abc"})
    void AT09_incompleteRepeatedOrUnknownFragmentKeysAreInvalidAnswers(String answer) {
        var base = draft(QuestionType.SENTENCE_ORDER);
        assertCode(options(base, base.options(), answer), "INVALID_ANSWER");
    }

    @Test
    void AT09_identicalFragmentsAreRejectedAndNoFragmentCanBeMarkedCorrect() {
        var base = draft(QuestionType.SENTENCE_ORDER);
        assertCode(options(base, List.of(option("A", "我", false), option("B", "今天", false), option("C", " 我 ", false)), "ABC"), "INVALID_VALUE");
        assertCode(options(base, List.of(option("A", "我", true), option("B", "今天", false), option("C", "学习", false)), "ABC"), "INVALID_ANSWER");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 51})
    void AT09_fillAnswerMustHaveOneToFiftyCodePoints(int length) {
        assertCode(options(draft(QuestionType.FILL_BLANK), List.of(), "文".repeat(length)), "INVALID_ANSWER");
    }

    @Test
    void AT09_fillRequiresAMarkerAndNoOptionsAndImageMatchRequiresAnImage() {
        var fill = draft(QuestionType.FILL_BLANK);
        assertCode(new Draft(fill.qtype(), 1, 2, "没有空白", List.of(), "在", "Lời giải", null, null, "TV1-001", List.of()), "INVALID_VALUE");
        assertCode(options(fill, List.of(option("A", "我", false)), "在"), "INVALID_VALUE");
        var image = draft(QuestionType.IMAGE_MATCH);
        assertCode(new Draft(image.qtype(), 1, 2, image.stem(), image.options(), null, "Lời giải", null, null, "TV1-001", List.of()), "REQUIRED_MISSING");
    }

    @Test
    void AT10_labelsRequireOneExistingPrimaryAndAtMostTwoDistinctSecondaries() {
        var base = draft(QuestionType.MULTIPLE_CHOICE);
        assertCode(labels(base, "", List.of()), "REQUIRED_MISSING");
        assertCode(labels(base, "MISSING", List.of()), "REFERENCE_NOT_FOUND");
        assertCode(labels(base, "TV1-001", List.of("MISSING")), "REFERENCE_NOT_FOUND");
        assertCode(labels(base, "TV1-001", List.of("TV1-002", "NP2-003", "CH3-004")), "TOO_MANY_SECONDARY_KIDS");
        assertCode(labels(base, "TV1-001", List.of("tv1-001")), "DUPLICATE_KID");
        assertCode(labels(base, "TV1-001", List.of("TV1-002", "tv1-002")), "DUPLICATE_KID");
        assertThat(QuestionRules.validate(labels(base, " tv1-001 ", List.of("tv1-002")), KIDS).errors()).isEmpty();
    }

    @Test
    void AT10_errorLabelsOnlyBelongToWrongChoiceOptionsAndNotesNeedAnExistingKid() {
        var base = draft(QuestionType.MULTIPLE_CHOICE);
        assertCode(options(base, List.of(new Option("A", "我", true, "TV1-001", null), option("B", "你", false)), null), "INVALID_VALUE");
        assertCode(options(base, List.of(option("A", "我", true), new Option("B", "你", false, "MISSING", null)), null), "INVALID_VALUE");
        assertCode(options(base, List.of(option("A", "我", true), new Option("B", "你", false, null, "Ghi chú")), null), "INVALID_VALUE");
        assertCode(options(base, List.of(option("A", "我", true), new Option("B", "你", false, "TV1-001", "文".repeat(201))), null), "INVALID_VALUE");
        assertThat(QuestionRules.validate(options(base, List.of(option("A", "我", true),
                new Option("B", "你", false, "tv1-001", "Ghi chú: tự soạn")), null), KIDS).errors()).isEmpty();
        assertCode(options(draft(QuestionType.TRUE_FALSE), List.of(option("A", "Đúng", true),
                new Option("B", "Sai", false, "TV1-001", null)), null), "INVALID_VALUE");
    }

    @Test
    void AT12_signatureProtectsOnlyGradingAndIsOrderIndependentForKeys() {
        Draft base = draft(QuestionType.MULTIPLE_CHOICE);
        Draft editorial = new Draft(base.qtype(), 3, 1, "Nội dung mới", List.of(option("A", "Nội dung A mới", true),
                new Option("B", "Nội dung B mới", false, "NP2-003", "Ghi chú mới")), null,
                "Lời giải mới", "https://example.test/audio", "https://example.test/image", "NP2-003", List.of("TV1-002"));
        assertThat(QuestionRules.gradingSignature(base)).isEqualTo(QuestionRules.gradingSignature(editorial));
        assertThat(QuestionRules.gradingSignature(options(base, List.of(option("A", "我", false), option("B", "你", true)), null)))
                .isNotEqualTo(QuestionRules.gradingSignature(base));
        assertThat(QuestionRules.gradingSignature(options(base, List.of(option("A", "我", true), option("B", "你", false), option("C", "他", false)), null)))
                .isNotEqualTo(QuestionRules.gradingSignature(base));
        assertThat(QuestionRules.gradingSignature(draft(QuestionType.TRUE_FALSE))).isNotEqualTo(QuestionRules.gradingSignature(base));
        assertThat(QuestionRules.gradingSignature(options(draft(QuestionType.FILL_BLANK), List.of(), "是")))
                .isNotEqualTo(QuestionRules.gradingSignature(draft(QuestionType.FILL_BLANK)));
    }

    @Test
    void T12_invalidDtoValuesProduceViolationsAndNormalizationDoesNotMutateInput() {
        var options = new ArrayList<Option>(); options.add(null);
        var value = new Draft(QuestionType.MULTIPLE_CHOICE, 10, 4, "", options, null, "", "bad url", null, "", List.of());
        options.clear();
        assertThat(value.options()).hasSize(1);
        assertThat(QuestionRules.validate(value, Set.of()).errors()).extracting(QuestionRules.Violation::code)
                .contains("REQUIRED_MISSING", "INVALID_HSK_LEVEL", "INVALID_VALUE", "INVALID_URL");
        assertThat(QuestionRules.validate(draft(QuestionType.MULTIPLE_CHOICE), KIDS).value().correctKey()).isNull();
    }

    private static void assertCode(Draft draft, String code) {
        assertThat(QuestionRules.validate(draft, KIDS).errors()).extracting(QuestionRules.Violation::code).contains(code);
    }
    private static Option option(String key, String text, boolean correct) { return new Option(key, text, correct, null, null); }
    private static Draft options(Draft base, List<Option> options, String answer) {
        return new Draft(base.qtype(), base.hskLevel(), base.difficulty(), base.stem(), options, answer, base.explanation(),
                base.audioUrl(), base.imageUrl(), base.primaryKid(), base.secondaryKids());
    }
    private static Draft labels(Draft base, String primary, List<String> secondary) {
        return new Draft(base.qtype(), base.hskLevel(), base.difficulty(), base.stem(), base.options(), base.correctKey(), base.explanation(),
                base.audioUrl(), base.imageUrl(), primary, secondary);
    }
    private static Draft draft(QuestionType type) {
        List<Option> options = type == QuestionType.FILL_BLANK ? List.of()
                : type == QuestionType.TRUE_FALSE ? List.of(option("A", "Đúng", true), option("B", "Sai", false))
                : type == QuestionType.SENTENCE_ORDER ? List.of(option("A", "我", false), option("B", "今天", false), option("C", "学习", false))
                : List.of(option("A", "我", true), option("B", "你", false));
        return new Draft(type, 1, 2, type == QuestionType.FILL_BLANK ? "我___学习。" : "Câu hỏi tự soạn。", options,
                type == QuestionType.FILL_BLANK ? "在" : type == QuestionType.SENTENCE_ORDER ? "CAB" : null,
                "Lời giải tự soạn", null, type == QuestionType.IMAGE_MATCH ? "https://example.test/image.png" : null, "TV1-001", List.of());
    }
}
