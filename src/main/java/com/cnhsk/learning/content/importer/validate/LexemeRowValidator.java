package com.cnhsk.learning.content.importer.validate;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.content.text.CjkUtils;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.learning.content.text.TextNormalizer;
import org.springframework.stereotype.Component;

@Component
public class LexemeRowValidator implements RowValidator {

    @Override
    public ImportDataType dataType() {
        return ImportDataType.LEXEME;
    }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of("text", "radical", "example_cn"));
            row.kind();
            if ("WORD".equals(row.value("kind"))) {
                for (String column : List.of("radical", "stroke_count")) {
                    if (row.present(column)) {
                        row.invalid(column, "chỉ dùng cho CHAR");
                    }
                }
            }
            if (row.present("example_cn")) {
                row.require("example_vi");
            }
            row.lengths();
            validateText(row);
            if (row.eligible("pinyin") && !PinyinUtils.hasValidCharacters(row.value("pinyin"))) {
                row.error("pinyin", "INVALID_PINYIN", "Pinyin chứa ký tự không hợp lệ");
            }
            row.integer("hsk_level", 1, 9, true);
            row.url("audio_url");
            if ("CHAR".equals(row.value("kind"))) {
                row.integer("stroke_count", 1, 64, false);
                validateRadical(row);
            }
            if (row.eligible("example_cn") && row.value("example_cn").codePoints().noneMatch(CjkUtils::isCjk)) {
                row.error("example_cn", "NOT_CJK", "Cột example_cn phải là chữ Hán");
            }
            validateTags(row);
        }
        return RowValidationSupport.finish(template, input, rows);
    }

    private static void validateText(Context row) {
        if (!row.eligible("text") || !row.eligible("kind")) {
            return;
        }
        String text = row.value("text");
        if (!CjkUtils.isCjk(text)) {
            row.error("text", "NOT_CJK", "Cột text phải là chữ Hán");
        } else if (row.value("kind").equals("CHAR") && RowValidationSupport.length(text) != 1) {
            row.invalid("text", "CHAR phải có đúng 1 chữ Hán");
        }
    }

    private static void validateRadical(Context row) {
        if (row.eligible("radical")) {
            int codePoint = row.value("radical").codePointAt(0);
            if (!CjkUtils.isCjk(codePoint) && (codePoint < 0x2E80 || codePoint > 0x2FDF)) {
                row.invalid("radical", "phải là chữ Hán hoặc bộ thủ U+2E80–U+2FDF");
            }
        }
    }

    private static void validateTags(Context row) {
        if (!row.eligible("tags")) {
            return;
        }
        List<String> tags = Arrays.stream(row.value("tags").split("\\|", -1))
                .map(tag -> TextNormalizer.normalizeCell("tags", tag))
                .filter(tag -> !tag.isEmpty()).toList();
        row.value("tags", String.join("|", tags));
        if (tags.size() > 10 || tags.stream().anyMatch(tag -> RowValidationSupport.length(tag) > 30)) {
            row.invalid("tags", "tối đa 10 nhãn, mỗi nhãn tối đa 30 ký tự");
        }
    }
}
