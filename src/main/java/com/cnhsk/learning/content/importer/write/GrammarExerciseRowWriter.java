package com.cnhsk.learning.content.importer.write;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.LexemeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GrammarExerciseRowWriter implements RowWriter {
    private final LexemeRepository lexemes;

    public GrammarExerciseRowWriter(LexemeRepository lexemes) {
        this.lexemes = lexemes;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.GRAMMAR_EXERCISE;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        Lexeme grammar = lexemes.findByKindAndText(LexemeKind.GRAMMAR, row.values().get("grammar_text"))
                .orElseThrow(() -> new IllegalStateException("Missing grammar import reference"));
        LexemeExtra extra = RowWriteSupport.extra(grammar.getExtra());
        List<LexemeExtra.Exercise> exercises = new ArrayList<>(extra.exercises() == null ? List.of() : extra.exercises());
        int no = Integer.parseInt(row.values().get("exercise_no"));
        LexemeExtra.Exercise existing = exercises.stream().filter(e -> e.no() == no).findFirst().orElse(null);
        LexemeExtra.Exercise next = new LexemeExtra.Exercise(no, row.values().get("stem"),
                RowWriteSupport.options(row, existing == null ? null : existing.options(),
                        LexemeExtra.Option::key, LexemeExtra.Option::new), row.values().get("answer"),
                RowWriteSupport.value(row, "explanation_vi", existing == null ? null : existing.explanation()));
        if (next.equals(existing)) {
            return RowAction.UNCHANGED;
        }
        if (existing == null) {
            exercises.add(next);
        } else {
            exercises.set(exercises.indexOf(existing), next);
        }
        exercises.sort(Comparator.comparingInt(LexemeExtra.Exercise::no));
        grammar.setExtra(new LexemeExtra(extra.examples(), extra.tags(), extra.structure(), extra.explanation(),
                extra.commonMistake(), exercises));
        lexemes.saveAndFlush(grammar);
        return existing == null ? RowAction.CREATE : RowAction.UPDATE;
    }
}
