package com.cnhsk.learning.content.importer.validate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Error codes (appendix D): every code but the communicative E-NL-* ones points at an existing node. */
@Component
public class ErrorCodeRowValidator implements RowValidator {
    private static final Pattern CODE = Pattern.compile("^E-(NA|CH|TV|NP|NL|VB|DI)-[0-9]{2}$");
    private final KnowledgePointRepository points;

    public ErrorCodeRowValidator(KnowledgePointRepository points) {
        this.points = points;
    }

    @Override public ImportDataType dataType() { return ImportDataType.ERROR_CODE; }

    @Override
    @Transactional(readOnly = true)
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of());
            row.lengths();
            if (row.eligible("code")) row.value("code", row.value("code").toUpperCase(Locale.ROOT));
            if (row.eligible("kid")) row.value("kid", row.value("kid").toUpperCase(Locale.ROOT));
            row.matching("code", CODE, "dạng E-<miền>-<2 số>, ví dụ E-NP-07");
            row.integer("severity", 1, 3, false);
            row.canonicalInteger("severity");
            boolean communicative = row.eligible("code") && row.value("code").startsWith("E-NL-");
            if (!communicative && !row.present("kid")) row.require("kid");
            if (communicative && row.present("kid")) {
                row.invalid("kid", "mã E-NL-* để trống: lỗi đổ về nút NL của nhiệm vụ được chấm");
            }
        }
        Set<String> kids = rows.stream().filter(row -> row.eligible("kid")).map(row -> row.value("kid"))
                .collect(Collectors.toSet());
        Map<String, KnowledgePoint> nodes = kids.isEmpty() ? Map.of() : points.findByCodeIn(kids).stream()
                .collect(Collectors.toMap(KnowledgePoint::getCode, point -> point));
        for (Context row : rows) {
            if (!row.eligible("kid")) continue;
            // The code's domain segment need not match the node: appendix D maps E-TV-01 (classifier) to NP1-010.
            if (!nodes.containsKey(row.value("kid"))) {
                row.error("kid", "REFERENCE_NOT_FOUND", "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước");
            }
        }
        return RowValidationSupport.finish(template, input, rows);
    }
}
