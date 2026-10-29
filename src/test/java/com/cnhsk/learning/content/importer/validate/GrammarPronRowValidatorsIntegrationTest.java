package com.cnhsk.learning.content.importer.validate;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.PronStage;
import com.cnhsk.learning.content.entity.PronUnit;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.CsvReader.RowError;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.PronStageRepository;
import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class GrammarPronRowValidatorsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private CsvReader reader;
    @Autowired private GrammarRowValidator grammarValidator;
    @Autowired private GrammarExerciseRowValidator exerciseValidator;
    @Autowired private PronStageRowValidator stageValidator;
    @Autowired private PronUnitRowValidator unitValidator;
    @Autowired private LexemeRepository lexemes;
    @Autowired private PronStageRepository stages;
    @Autowired private List<RowValidator> validators;

    @Test
    void FR009_allSevenTemplateValidatorsAreAvailableAsComponents() {
        assertThat(validators).extracting(RowValidator::dataType).containsExactlyInAnyOrder(ImportDataType.values());
    }

    @Test
    void FR011_grammarExerciseRequiresGrammarKindAndDoesNotWriteExercises() {
        lexemes.saveAndFlush(new Lexeme(LexemeKind.WORD, "是", "là"));
        String csv = "grammar_text,exercise_no,stem,option_a,option_b,answer\n是,01,我___学生,是,有,A\n";

        RowValidationResult missing = exerciseValidator.validate(read(ImportDataType.GRAMMAR_EXERCISE, csv));
        assertThat(missing.validRows()).isEmpty();
        assertThat(missing.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo("grammar_text");
            assertThat(error.code()).isEqualTo("REFERENCE_NOT_FOUND");
        });
        Lexeme grammar = lexemes.saveAndFlush(new Lexeme(LexemeKind.GRAMMAR, "是", "Cấu trúc giới thiệu"));
        LexemeExtra original = grammar.getExtra();
        long count = lexemes.count();

        RowValidationResult valid = exerciseValidator.validate(read(ImportDataType.GRAMMAR_EXERCISE, csv));

        assertThat(valid.errors()).isEmpty();
        assertThat(valid.validRows()).singleElement().satisfies(row -> assertThat(row.values()).containsEntry("exercise_no", "1"));
        assertThat(grammar.getExtra()).isSameAs(original);
        assertThat(lexemes.count()).isEqualTo(count);
        assertThat(lexemes.findByKindAndText(LexemeKind.GRAMMAR, "是").orElseThrow().getExtra()).isEqualTo(original);
    }

    @Test
    void FR009_pronUnitCapacityReadsExistingJsonAndLeavesUnitsUnchanged() {
        PronStage stage = new PronStage((short) 1, "Tầng tự soạn");
        List<PronUnit> original = IntStream.range(0, 99).mapToObj(index -> new PronUnit("OLD_" + index,
                "https://example.com/a.mp3", "Nghe và chọn", List.of(new PronUnit.Option("A", "b"),
                        new PronUnit.Option("B", "p")), "A", "Giải thích tự soạn")).toList();
        stage.setUnits(original);
        stages.saveAndFlush(stage);
        long count = stages.count();
        String csv = "stage_index,unit_code,audio_url,option_a,option_b,answer\n"
                + "01,NEW,https://example.com/a.mp3,b,p,A\n"
                + "1,OVER,https://example.com/a.mp3,b,p,A\n"
                + "1,OLD_0,https://example.com/a.mp3,b,p,B\n"
                + "1,NEW,https://example.com/a.mp3,b,p,A\n";

        RowValidationResult result = unitValidator.validate(read(ImportDataType.PRON_UNIT, csv));

        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(2, 4);
        assertThat(result.errors()).extracting(RowError::line).containsExactly(3, 5);
        assertThat(result.errors()).extracting(RowError::code).containsExactly("INVALID_VALUE", "DUPLICATE_IN_FILE");
        assertThat(stage.getUnits()).containsExactlyElementsOf(original);
        assertThat(stages.count()).isEqualTo(count);
        assertThat(stages.findByStageIndex((short) 1).orElseThrow().getUnits()).containsExactlyElementsOf(original);
    }

    @Test
    void FR009_AT039_badAnswerDoesNotReserveNaturalKeyBeforeALaterValidExercise() {
        lexemes.saveAndFlush(new Lexeme(LexemeKind.GRAMMAR, "是", "Cấu trúc giới thiệu"));
        String csv = "grammar_text,exercise_no,stem,option_a,option_b,answer\n"
                + "是,1,我___学生,是,有,D\n\n\n"
                + "是,01,我___学生,是,有,A\n"
                + "是,1,我___学生,是,有,A\n";

        RowValidationResult result = exerciseValidator.validate(read(ImportDataType.GRAMMAR_EXERCISE, csv));

        assertThat(result.validRows()).extracting(RowValidationResult.ValidatedRow::line).containsExactly(5);
        assertThat(result.errors()).extracting(RowError::code).containsExactly("INVALID_ANSWER", "DUPLICATE_IN_FILE");
        assertThat(result.errors().get(1).message()).isEqualTo("Trùng khoá với dòng 5");
    }

    @Test
    void FR030_FR032_grammarAndStageValidationPreservesOptionalBlankValues() {
        RowValidationResult grammar = grammarValidator.validate(new CsvReader.ReadResult(1, List.of(new CsvReader.Row(2,
                Map.of("text", "是……的", "hsk_level", "2", "title_vi", " Đi  học ", "explanation_vi", " Giải thích\n hai dòng ",
                        "example1_cn", "我是昨天来的。", "example1_vi", "Tôi đến hôm qua.", "common_mistake_vi", " "))), List.of()));
        assertThat(grammar.errors()).isEmpty();
        assertThat(grammar.validRows()).singleElement().satisfies(row -> {
            assertThat(row.meaningNorm()).isEqualTo("di  hoc");
            assertThat(row.values()).containsEntry("common_mistake_vi", "")
                    .containsEntry("explanation_vi", "Giải thích\n hai dòng");
        });

        RowValidationResult stage = stageValidator.validate(read(ImportDataType.PRON_STAGE,
                "stage_index,name,target_sounds,pass_percent\n01,Tầng phát âm,|b|| |p|, \n"));
        assertThat(stage.errors()).isEmpty();
        assertThat(stage.validRows()).singleElement().satisfies(row -> assertThat(row.values())
                .containsEntry("stage_index", "1").containsEntry("target_sounds", "b|p").containsEntry("pass_percent", ""));
        assertThat(stages.findByStageIndex((short) 1)).isEmpty();
    }

    private CsvReader.ReadResult read(ImportDataType type, String csv) {
        return reader.read(type, "self-authored.csv", csv.getBytes(StandardCharsets.UTF_8));
    }
}
