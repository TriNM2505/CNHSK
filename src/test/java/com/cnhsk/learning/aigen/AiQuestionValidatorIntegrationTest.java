package com.cnhsk.learning.aigen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.cnhsk.learning.ai.StubClaudeClient;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(AiPromptBuilderIntegrationTest.SqlConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AiQuestionValidatorIntegrationTest extends AbstractIntegrationTest {
    @Autowired AiQuestionValidator validator;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired StubClaudeClient stub;
    @Autowired AiPromptBuilderIntegrationTest.SqlCapture sql;
    AiPromptBuilderIntegrationTest.Fixtures fixtures;
    AiPromptBuilder.Prompt prompt;
    int calls;

    @BeforeEach
    void prepare() {
        fixtures = new AiPromptBuilderIntegrationTest.Fixtures(jdbc);
        long node = fixtures.node("Validation target", "Can do");
        prompt = new AiPromptBuilder.Prompt(node, fixtures.kid(node), 1, List.of(), List.of());
        calls = stub.callCount();
    }

    @AfterEach
    void cleanup() { fixtures.close(); assertThat(stub.callCount()).isEqualTo(calls); }

    @Test
    void dropsInvalidRowsByOriginalIndexAndRetainsValidDraftWithoutPersisting(CapturedOutput output) {
        long errorNode = fixtures.node("Error reference", "Can do");
        fixtures.lexeme(null, "CHAR", "𠀁", 1);
        fixtures.lexeme(null, "CHAR", "𠀂", 2);
        fixtures.question(prompt.knowledgePointId(), "SECONDARY", "ARCHIVED", "MANUAL", "existing duplicate",
                AiPromptBuilderIntegrationTest.fixtureOptions(null));
        String privateStem = "valid-private-" + UUID.randomUUID();
        ObjectNode two = question("two correct"); ((ObjectNode) two.withArray("options").get(1)).put("isCorrect", true);
        ObjectNode unknown = question("unknown reference"); ((ObjectNode) unknown.withArray("options").get(1)).put("errorKid", "VB1-999");
        ObjectNode valid = question(privateStem + " 𠀁"); ((ObjectNode) valid.withArray("options").get(1)).put("errorKid", fixtures.kid(errorNode));
        ArrayNode rows = mapper.createArrayNode().add(two).add(question(" existing\u2003duplicate "))
                .add(question("above level 𠀂")).add(unknown).add(valid);
        List<Long> before = fixtures.counts();
        sql.clear();
        var result = validate(rows);
        assertThat(result.accepted()).singleElement().satisfies(value -> {
            assertThat(value.stem()).isEqualTo(privateStem + " 𠀁");
            assertThat(value.correctKey()).isEqualTo("A");
            assertThat(value.options().get(1).errorKid()).isEqualTo(fixtures.kid(errorNode));
        });
        assertThat(result.dropped()).extracting(AiQuestionValidator.Dropped::index).containsExactly(0, 1, 2, 3);
        assertThat(result.dropped()).extracting(AiQuestionValidator.Dropped::reason).containsExactly(
                "INVALID_CORRECT_ANSWER", "DUPLICATE_STEM", "HANZI_ABOVE_LEVEL", "UNKNOWN_ERROR_KID");
        sql.assertContentReads(3);
        assertThat(sql.statements).hasSize(3);
        assertThat(fixtures.counts()).isEqualTo(before);
        assertThat(output.getAll()).doesNotContain(privateStem, "two correct", "unknown reference");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "APPROVED", "REJECTED", "ARCHIVED"})
    void duplicateCheckIncludesEveryStatusAndBothLabelRoles(String status) {
        long other = fixtures.node("Other", "Can do");
        fixtures.question(prompt.knowledgePointId(), "PRIMARY", status, "IMPORT", "primary existing", AiPromptBuilderIntegrationTest.fixtureOptions(null));
        fixtures.question(prompt.knowledgePointId(), "SECONDARY", status, "MANUAL", "secondary existing", AiPromptBuilderIntegrationTest.fixtureOptions(null));
        fixtures.question(prompt.knowledgePointId(), "SECONDARY", status.equals("APPROVED") ? "ARCHIVED" : status,
                "AI", "provider existing", AiPromptBuilderIntegrationTest.fixtureOptions(null));
        fixtures.question(other, "PRIMARY", status, "IMPORT", "unrelated existing", AiPromptBuilderIntegrationTest.fixtureOptions(null));
        sql.clear();
        var result = validate(mapper.createArrayNode().add(question("primary existing")).add(question("secondary existing"))
                .add(question("provider existing")).add(question("unrelated existing")));
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem).containsExactly("unrelated existing");
        assertThat(result.dropped()).extracting(AiQuestionValidator.Dropped::reason).containsExactly("DUPLICATE_STEM", "DUPLICATE_STEM", "DUPLICATE_STEM");
        sql.assertContentReads(1);
        assertThat(sql.statements).hasSize(1);
    }

    @Test
    void allowsOnlyGlobalSingleCharacterHanAtOrBelowTheRequestedLevel() {
        long other = fixtures.node("Unrelated character owner", "Can do");
        fixtures.lexeme(other, "CHAR", "𠀁", 1);
        fixtures.lexeme(null, "CHAR", "豈", 1);
        fixtures.lexeme(null, "CHAR", "𠀂", 2);
        fixtures.lexeme(null, "CHAR", "𠀃", null);
        fixtures.lexeme(null, "WORD", "𠀄", 1);
        fixtures.lexeme(null, "GRAMMAR", "𠀅", 1);
        fixtures.lexeme(null, "CHAR", "𠀆𠀇", 1);
        ArrayNode rows = mapper.createArrayNode().add(question("global allowed 𠀁豈，😀"));
        for (String forbidden : List.of("𠀂", "𠀃", "𠀄", "𠀅", "𠀆", "𠀈")) rows.add(question("forbidden " + forbidden));
        ObjectNode explanation = question("explanation only"); explanation.put("explanation", "𠀈 explanation is not checked"); rows.add(explanation);
        sql.clear();
        var result = validate(rows);
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem)
                .containsExactly("global allowed 𠀁豈，😀", "explanation only");
        assertThat(result.dropped()).hasSize(6).allSatisfy(drop -> assertThat(drop.reason()).isEqualTo("HANZI_ABOVE_LEVEL"));
        sql.assertContentReads(2);
        assertThat(sql.statements).hasSize(2);
    }

    @Test
    void charactersOfTheNodeOwnWordsAreAllowedEvenWhenTheCharacterIsListedHigher() {
        fixtures.lexeme(null, "CHAR", "𠀋", 3);
        fixtures.lexeme(prompt.knowledgePointId(), "WORD", "𠀋𠀌", 1);
        long other = fixtures.node("Other word owner", "Can do");
        fixtures.lexeme(other, "WORD", "𠀍𠀎", 1);
        sql.clear();
        var result = validate(mapper.createArrayNode().add(question("node word 𠀋𠀌")).add(question("other node word 𠀍")));
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem).containsExactly("node word 𠀋𠀌");
        assertThat(result.dropped()).extracting(AiQuestionValidator.Dropped::reason).containsExactly("HANZI_ABOVE_LEVEL");
        sql.assertContentReads(2);
    }

    @Test
    void nearDuplicateUsesTheStoredCorrectAnswerInTheSameSingleRead() {
        fixtures.question(prompt.knowledgePointId(), "PRIMARY", "APPROVED", "MANUAL", "Weekend library visit planned",
                AiPromptBuilderIntegrationTest.fixtureOptions(null));
        ObjectNode otherAnswer = question("Weekend library visit plannef");
        ((ObjectNode) otherAnswer.withArray("options").get(0)).put("text", "another answer");
        sql.clear();
        var result = validate(mapper.createArrayNode().add(question("Weekend library visit plannee")).add(otherAnswer));
        assertThat(result.accepted()).extracting(AiQuestionValidator.ValidatedQuestion::stem)
                .containsExactly("Weekend library visit plannef");
        assertThat(result.dropped()).containsExactly(new AiQuestionValidator.Dropped(0, "NEAR_DUPLICATE_STEM"));
        sql.assertContentReads(1);
        assertThat(sql.statements).hasSize(1);
    }

    @Test
    void batchesReferencesAcrossManyRowsAndSkipsEmptyReferenceSets() {
        long errorNode = fixtures.node("Error", "Can do");
        fixtures.lexeme(null, "CHAR", "𠀁", 1);
        ArrayNode rows = mapper.createArrayNode();
        for (int index = 0; index < 25; index++) {
            ObjectNode row = question("batched " + index + " 𠀁");
            ((ObjectNode) row.withArray("options").get(1)).put("errorKid", fixtures.kid(errorNode));
            rows.add(row);
        }
        sql.clear();
        assertThat(validate(rows).accepted()).hasSize(25);
        sql.assertContentReads(3);
        assertThat(sql.statements).hasSize(3);
        sql.clear();
        assertThat(validate(mapper.createArrayNode().add(question("Latin only"))).accepted()).hasSize(1);
        sql.assertContentReads(1);
        assertThat(sql.statements).hasSize(1);
    }

    @Test
    void malformedWholeOutputAndEmptyArrayPerformNoDatabaseReads(CapturedOutput output) {
        String secret = "provider-private-" + UUID.randomUUID();
        List<Long> before = fixtures.counts();
        sql.clear();
        assertThatThrownBy(() -> validator.validate("{\"questions\":[\"" + secret, prompt))
                .isInstanceOf(AiQuestionValidator.InvalidOutputException.class).hasMessage("INVALID_OUTPUT").hasNoCause();
        var empty = validator.validate("{\"questions\":[]}", prompt);
        assertThat(empty.accepted()).isEmpty(); assertThat(empty.dropped()).isEmpty();
        assertThat(sql.statements).isEmpty();
        assertThat(fixtures.counts()).isEqualTo(before);
        assertThat(output.getAll()).doesNotContain(secret);
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
    private ObjectNode option(String key, String text, boolean correct) {
        return mapper.createObjectNode().put("key", key).put("text", text).put("isCorrect", correct);
    }
}
