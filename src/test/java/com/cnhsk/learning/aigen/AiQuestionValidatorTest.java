package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.cnhsk.learning.knowledge.entity.QuestionOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiQuestionValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WeakKnowledgeQueryRepository repository = mock(WeakKnowledgeQueryRepository.class);
    private final AiPromptBuilder.Prompt prompt = new AiPromptBuilder.Prompt(17, "NP1-001", 1, List.of(), List.of());
    private AiQuestionValidator validator;

    @BeforeEach
    void prepare() {
        validator = new AiQuestionValidator(repository, mapper);
        when(repository.existingStems(17)).thenReturn(List.of());
        when(repository.existingKidCodes(anySet())).thenReturn(Set.of("NP1-002"));
        when(repository.allowedCharacters(anySet(), anyInt())).thenAnswer(call -> call.getArgument(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "{\"questions\":[", "```json\n{\"questions\":[]}\n```",
            "{\"questions\":[]} {}", "null", "[]", "42", "{}", "{\"questions\":null}",
            "{\"questions\":{}}", "{\"questions\":\"private-content\"}"})
    void rejectsTheWholeInvalidDocumentWithoutParserDetails(String raw) {
        assertThatThrownBy(() -> validator.validate(raw, prompt))
                .isInstanceOf(AiQuestionValidator.InvalidOutputException.class)
                .hasMessage("INVALID_OUTPUT").hasNoCause();
        // Stubbing does not count as a repository invocation.
        verifyNoInteractions(repository);
    }

    @Test
    void emptyArrayNeedsNoReferenceReads() {
        var result = validator.validate("{\"questions\":[]}", prompt);
        assertThat(result.accepted()).isEmpty();
        assertThat(result.dropped()).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void malformedRowsKeepTheirOriginalIndexesAndValidSibling() {
        ObjectNode absent = question("missing"); absent.remove("stem");
        ObjectNode wrong = question("wrong"); wrong.put("explanation", 12);
        ArrayNode rows = mapper.createArrayNode().addNull().add(42).add(absent).add(wrong).add(question("valid"));
        var result = validate(rows);
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem).containsExactly("valid");
        assertThat(result.dropped()).extracting(AiQuestionValidator.Dropped::index).containsExactly(0, 1, 2, 3);
        assertThat(result.dropped()).allSatisfy(drop -> assertThat(drop.reason()).matches("[A-Z_]+"));
    }

    @Test
    void usesNormalizedCodePointLengthsForAllTextLimits() {
        for (String field : List.of("stem", "explanation", "text")) {
            int limit = switch (field) { case "stem" -> 500; case "explanation" -> 1000; default -> 200; };
            for (int size : new int[] {0, 1, limit, limit + 1}) {
                ObjectNode row = question("length case");
                ObjectNode target = field.equals("text") ? (ObjectNode) row.withArray("options").get(0) : row;
                target.put(field, "😀".repeat(size));
                var result = validate(mapper.createArrayNode().add(row));
                assertThat(result.accepted()).as("%s code points %s", field, size).hasSize(size > 0 && size <= limit ? 1 : 0);
            }
            ObjectNode blank = question("blank");
            (field.equals("text") ? (ObjectNode) blank.withArray("options").get(0) : blank).put(field, " \t\u2003\n");
            assertThat(validate(mapper.createArrayNode().add(blank)).accepted()).isEmpty();
        }
        ObjectNode normalized = question("\u2003 e\u0301 \u00a0");
        assertThat(validate(mapper.createArrayNode().add(normalized)).accepted()).singleElement()
                .satisfies(value -> assertThat(value.stem()).isEqualTo("é"));
    }

    @Test
    void requiresExactlyFourDistinctUppercaseKeysAndDistinctNormalizedTexts() {
        for (int count : new int[] {3, 5}) {
            ObjectNode row = question("count");
            ArrayNode options = row.withArray("options");
            if (count == 3) options.remove(3); else options.add(option("E", "fifth", false));
            assertDropped(row, "INVALID_OPTIONS");
        }
        for (String key : List.of("A", "b", "", "E")) {
            ObjectNode row = question("keys"); ((ObjectNode) row.withArray("options").get(1)).put("key", key);
            assertDropped(row, "INVALID_OPTIONS");
        }
        ObjectNode duplicate = question("texts");
        ((ObjectNode) duplicate.withArray("options").get(0)).put("text", "same\u00a0 text");
        ((ObjectNode) duplicate.withArray("options").get(1)).put("text", "same \ntext");
        assertDropped(duplicate, "INVALID_OPTIONS");
    }

    @Test
    void rejectsMissingOrCoercedCorrectnessAndRequiresExactlyOneAnswer() {
        for (String kind : List.of("missing", "null", "string", "none", "two")) {
            ObjectNode row = question("correct " + kind);
            ObjectNode first = (ObjectNode) row.withArray("options").get(0);
            switch (kind) {
                case "missing" -> first.remove("isCorrect");
                case "null" -> first.putNull("isCorrect");
                case "string" -> first.put("isCorrect", "true");
                case "none" -> first.put("isCorrect", false);
                default -> ((ObjectNode) row.withArray("options").get(1)).put("isCorrect", true);
            }
            assertThat(validate(mapper.createArrayNode().add(row)).accepted()).as(kind).isEmpty();
        }
    }

    @Test
    void acceptsOnlyIntegerDifficultyWithinOneAndThree() {
        for (int value : new int[] {0, 1, 3, 4}) {
            ObjectNode row = question("difficulty"); row.put("difficulty", value);
            assertThat(validate(mapper.createArrayNode().add(row)).accepted()).hasSize(value == 1 || value == 3 ? 1 : 0);
        }
        ObjectNode decimal = question("decimal"); decimal.put("difficulty", 1.0);
        ObjectNode text = question("text"); text.put("difficulty", "1");
        ObjectNode missing = question("missing"); missing.remove("difficulty");
        ObjectNode nil = question("null"); nil.putNull("difficulty");
        for (ObjectNode row : List.of(decimal, text, missing, nil)) assertDropped(row, "INVALID_DIFFICULTY");
    }

    @Test
    void validatesErrorMetadataAndNormalizesExistingKidWithoutChangingOptionBinding() throws Exception {
        ObjectNode row = question("mapping");
        ObjectNode wrong = (ObjectNode) row.withArray("options").get(1);
        wrong.put("errorKid", " np1-002 ").put("errorNote", " note ");
        row.put("correctKey", "D").put("correct_key", "C");
        ArrayNode original = row.withArray("options");
        row.set("options", mapper.createArrayNode().add(original.get(3)).add(original.get(1)).add(original.get(0)).add(original.get(2)));
        var value = validate(mapper.createArrayNode().add(row)).accepted().get(0);
        assertThat(value.correctKey()).isEqualTo("A");
        assertThat(value.options()).filteredOn(option -> option.key().equals("B")).singleElement().satisfies(option -> {
            assertThat(option.text()).isEqualTo("second"); assertThat(option.isCorrect()).isFalse();
            assertThat(option.errorKid()).isEqualTo("NP1-002"); assertThat(option.errorNote()).isEqualTo("note");
        });
        String stored = mapper.writeValueAsString(value.options());
        assertThat(stored).contains("\"is_correct\"", "\"error_kid\"", "\"error_note\"")
                .doesNotContain("isCorrect", "errorKid", "errorNote");
        List<QuestionOption> restored = mapper.readValue(stored,
                mapper.getTypeFactory().constructCollectionType(List.class, QuestionOption.class));
        assertThat(restored).isEqualTo(value.options());

        ObjectNode unknown = question("unknown"); ((ObjectNode) unknown.withArray("options").get(1)).put("errorKid", "NP1-999");
        assertDropped(unknown, "UNKNOWN_ERROR_KID");
        ObjectNode correct = question("correct metadata"); ((ObjectNode) correct.withArray("options").get(0)).put("errorKid", "NP1-002");
        assertDropped(correct, "INVALID_ERROR_METADATA");
        ObjectNode noKid = question("note alone"); ((ObjectNode) noKid.withArray("options").get(1)).put("errorNote", "note");
        assertDropped(noKid, "INVALID_ERROR_METADATA");
        for (int size : new int[] {200, 201}) {
            ObjectNode note = question("note boundary");
            ((ObjectNode) note.withArray("options").get(1)).put("errorKid", "NP1-002").put("errorNote", "😀".repeat(size));
            assertThat(validate(mapper.createArrayNode().add(note)).accepted()).hasSize(size == 200 ? 1 : 0);
        }
        ObjectNode typed = question("typed metadata"); ((ObjectNode) typed.withArray("options").get(1)).put("errorKid", 1);
        assertThat(validate(mapper.createArrayNode().add(typed)).accepted()).isEmpty();
    }

    @Test
    void firstValidNormalizedStemWinsWithoutCaseFoldingOrInvalidRowReservation() {
        ObjectNode invalid = question("e\u0301 \u2003 text"); invalid.put("difficulty", 0);
        ArrayNode rows = mapper.createArrayNode().add(invalid).add(question("é text")).add(question(" é\ntext "))
                .add(question("É text")).add(question("other"));
        when(repository.existingStems(17)).thenReturn(List.of(new WeakKnowledgeQueryRepository.ExistingStem(" other\u00a0 ", options("first"))));
        var result = validate(rows);
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem).containsExactly("é text", "É text");
        assertThat(result.dropped()).extracting(AiQuestionValidator.Dropped::index).containsExactly(0, 2, 4);
        assertThat(result.dropped().get(1).reason()).isEqualTo("DUPLICATE_STEM");
        assertThat(result.dropped().get(2).reason()).isEqualTo("DUPLICATE_STEM");
    }

    @Test
    void stemsDifferingOnlyInPunctuationOrSpacesAreNearDuplicatesOfExistingAndEarlierRows() {
        when(repository.existingStems(17)).thenReturn(List.of(
                new WeakKnowledgeQueryRepository.ExistingStem("我每天七点起床。", options("right"))));
        ArrayNode rows = mapper.createArrayNode().add(question("我 每天 七点 起床"))
                .add(question("Self-authored, sentence one!")).add(question("Self-authored sentence one"));
        var result = validate(rows);
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem)
                .containsExactly("Self-authored, sentence one!");
        assertThat(result.dropped()).containsExactly(new AiQuestionValidator.Dropped(0, "NEAR_DUPLICATE_STEM"),
                new AiQuestionValidator.Dropped(2, "NEAR_DUPLICATE_STEM"));
    }

    @Test
    void similarLongStemsAreDuplicatesOnlyWithTheSameCorrectAnswer() {
        when(repository.existingStems(17)).thenReturn(List.of(
                new WeakKnowledgeQueryRepository.ExistingStem("我们明天下午去学校图书馆看书", options("first"))));
        // Only the last character differs: 12 of 14 bigrams are shared (0.857), so the correct answer decides.
        ObjectNode sameAnswer = question("我们明天下午去学校图书馆看报");
        ObjectNode otherAnswer = question("我们明天下午去学校图书馆看画");
        ((ObjectNode) otherAnswer.withArray("options").get(0)).put("text", "another answer");
        var result = validate(mapper.createArrayNode().add(sameAnswer).add(otherAnswer));
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem)
                .containsExactly("我们明天下午去学校图书馆看画");
        assertThat(result.dropped()).containsExactly(new AiQuestionValidator.Dropped(0, "NEAR_DUPLICATE_STEM"));
    }

    @Test
    void shortDifferentOrDifferentlyNumberedStemsWithTheSameAnswerStayAccepted() {
        when(repository.existingStems(17)).thenReturn(List.of(
                new WeakKnowledgeQueryRepository.ExistingStem("我有一本书", options("first")),
                new WeakKnowledgeQueryRepository.ExistingStem("他昨天晚上在家里看电视", options("first"))));
        var result = validate(mapper.createArrayNode().add(question("我有一本笔")).add(question("妈妈周末带我们去公园散步"))
                .add(question("Practice item number 1")).add(question("Practice item number 11")));
        assertThat(result.accepted()).hasSize(4);
        assertThat(result.dropped()).isEmpty();
    }

    @Test
    void storedOptionsWithoutAReadableCorrectAnswerNeverMatchBySimilarity() {
        String stem = "我们明天下午去学校图书馆看书";
        when(repository.existingStems(17)).thenReturn(List.of(
                new WeakKnowledgeQueryRepository.ExistingStem(stem, "[]"),
                new WeakKnowledgeQueryRepository.ExistingStem(stem, "{not json"),
                new WeakKnowledgeQueryRepository.ExistingStem(stem, null)));
        var result = validate(mapper.createArrayNode().add(question("我们明天下午去学校图书馆看报")));
        assertThat(result.accepted()).hasSize(1);
        assertThat(result.dropped()).isEmpty();
    }

    @Test
    void similarityIsJaccardOverCodePointBigrams() {
        assertThat(AiQuestionValidator.similarity("abcd", "abcd")).isEqualTo(1.0);
        assertThat(AiQuestionValidator.similarity("abcd", "abce")).isEqualTo(0.5);
        assertThat(AiQuestionValidator.similarity("ab", "cd")).isZero();
        assertThat(AiQuestionValidator.similarity("𠀀𠀁𠀂", "𠀀𠀁𠀂")).isEqualTo(1.0);
    }

    @Test
    void referenceFailureDoesNotReserveAStemBeforeAValidSibling() {
        ObjectNode unknownReference = question("shared stem");
        ((ObjectNode) unknownReference.withArray("options").get(1)).put("errorKid", "NP1-999");
        var result = validate(mapper.createArrayNode().add(unknownReference).add(question("shared stem")));
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem).containsExactly("shared stem");
        assertThat(result.dropped()).containsExactly(new AiQuestionValidator.Dropped(0, "UNKNOWN_ERROR_KID"));
    }

    @Test
    void scansBmpSupplementaryAndCompatibilityHanInStemAndOptionsButNotExplanation() {
        when(repository.allowedCharacters(anySet(), anyInt())).thenReturn(Set.of("你", "𠀀", "豈"));
        ObjectNode allowed = question("你𠀀豈，😀!"); allowed.put("explanation", "龍 need not be level checked");
        ObjectNode forbiddenStem = question("龍");
        ObjectNode forbiddenOption = question("option Han"); ((ObjectNode) forbiddenOption.withArray("options").get(3)).put("text", "龍");
        var result = validate(mapper.createArrayNode().add(allowed).add(forbiddenStem).add(forbiddenOption));
        assertThat(result.accepted()).hasSize(1);
        assertThat(result.dropped()).extracting(AiQuestionValidator.Dropped::reason).containsExactly("HANZI_ABOVE_LEVEL", "HANZI_ABOVE_LEVEL");
    }

    private void assertDropped(ObjectNode row, String reason) {
        var result = validate(mapper.createArrayNode().add(row));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.dropped()).singleElement().satisfies(drop -> assertThat(drop.reason()).isEqualTo(reason));
    }
    private AiQuestionValidator.ValidationResult validate(ArrayNode rows) {
        return validator.validate(mapper.createObjectNode().set("questions", rows).toString(), prompt);
    }
    private ObjectNode question(String stem) {
        ObjectNode row = mapper.createObjectNode().put("stem", stem).put("explanation", "Self-authored explanation").put("difficulty", 2);
        row.set("options", mapper.createArrayNode().add(option("A", "first", true)).add(option("B", "second", false))
                .add(option("C", "third", false)).add(option("D", "fourth", false)));
        return row;
    }
    /** Stored options JSON as the database keeps it (snake_case), with {@code correct} as the right answer. */
    private static String options(String correct) {
        return "[{\"key\":\"A\",\"text\":\"" + correct + "\",\"is_correct\":true},"
                + "{\"key\":\"B\",\"text\":\"second\",\"is_correct\":false}]";
    }
    private ObjectNode option(String key, String text, boolean correct) {
        return mapper.createObjectNode().put("key", key).put("text", text).put("isCorrect", correct);
    }
}
