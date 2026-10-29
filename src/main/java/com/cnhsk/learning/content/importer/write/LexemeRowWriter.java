package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeExtra;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.content.text.TextNormalizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LexemeRowWriter implements RowWriter {
    private final LexemeRepository lexemes;

    public LexemeRowWriter(LexemeRepository lexemes) {
        this.lexemes = lexemes;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.LEXEME;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        LexemeKind kind = LexemeKind.valueOf(row.values().get("kind"));
        Lexeme existing = lexemes.findByKindAndText(kind, row.values().get("text")).orElse(null);
        Lexeme lexeme = existing == null
                ? new Lexeme(kind, row.values().get("text"), row.values().get("meaning_vi")) : existing;
        RowWriteSupport.Changes changes = new RowWriteSupport.Changes();
        changes.text(row, "pinyin", lexeme.getPinyin(), lexeme::setPinyin);
        changes.text(row, "sino_viet", lexeme.getSinoViet(), lexeme::setSinoViet);
        changes.text(row, "meaning_vi", lexeme.getMeaningVi(), lexeme::setMeaningVi);
        changes.shortValue(row, "hsk_level", lexeme.getHskLevel(), lexeme::setHskLevel);
        changes.text(row, "audio_url", lexeme.getAudioUrl(), lexeme::setAudioUrl);
        changes.text(row, "radical", lexeme.getRadical(), lexeme::setRadical);
        changes.shortValue(row, "stroke_count", lexeme.getStrokeCount(), lexeme::setStrokeCount);
        LexemeExtra old = RowWriteSupport.extra(lexeme.getExtra());
        LexemeExtra extra = new LexemeExtra(RowWriteSupport.examples(row, old.examples(), false),
                RowWriteSupport.tokens(row, "tags", old.tags()), old.structure(), old.explanation(),
                old.commonMistake(), old.exercises());
        changes.set(lexeme.getExtra(), extra, lexeme::setExtra);
        if (existing == null || changes.changed()) {
            lexeme.setPinyinNorm(PinyinUtils.normalize(lexeme.getPinyin()));
            lexeme.setMeaningNorm(TextNormalizer.normalizeMeaning(lexeme.getMeaningVi()));
            lexemes.saveAndFlush(lexeme);
            return existing == null ? RowAction.CREATE : RowAction.UPDATE;
        }
        return RowAction.UNCHANGED;
    }
}
