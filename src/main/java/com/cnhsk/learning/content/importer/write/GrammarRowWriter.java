package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.text.TextNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GrammarRowWriter implements RowWriter {
    private final LexemeRepository lexemes;

    public GrammarRowWriter(LexemeRepository lexemes) {
        this.lexemes = lexemes;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.GRAMMAR;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        Lexeme existing = lexemes.findByKindAndText(LexemeKind.GRAMMAR, row.values().get("text")).orElse(null);
        Lexeme grammar = existing == null
                ? new Lexeme(LexemeKind.GRAMMAR, row.values().get("text"), row.values().get("title_vi")) : existing;
        RowWriteSupport.Changes changes = new RowWriteSupport.Changes();
        changes.text(row, "title_vi", grammar.getMeaningVi(), grammar::setMeaningVi);
        changes.shortValue(row, "hsk_level", grammar.getHskLevel(), grammar::setHskLevel);
        LexemeExtra old = RowWriteSupport.extra(grammar.getExtra());
        LexemeExtra extra = new LexemeExtra(RowWriteSupport.examples(row, old.examples(), true), old.tags(),
                RowWriteSupport.value(row, "structure", old.structure()),
                RowWriteSupport.value(row, "explanation_vi", old.explanation()),
                RowWriteSupport.value(row, "common_mistake_vi", old.commonMistake()), old.exercises());
        changes.set(grammar.getExtra(), extra, grammar::setExtra);
        if (existing == null || changes.changed()) {
            grammar.setMeaningNorm(TextNormalizer.normalizeMeaning(grammar.getMeaningVi()));
            lexemes.saveAndFlush(grammar);
            return existing == null ? RowAction.CREATE : RowAction.UPDATE;
        }
        return RowAction.UNCHANGED;
    }
}
