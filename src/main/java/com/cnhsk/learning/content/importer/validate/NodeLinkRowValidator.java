package com.cnhsk.learning.content.importer.validate;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.content.repository.NodeLinkRepository;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NodeLinkRowValidator implements RowValidator {
    private static final int BATCH_SIZE = 500;
    private static final Set<String> TYPES = Set.of("TOPIC", "WORD", "CHAR", "GRAMMAR");
    private final NodeLinkRepository targets;
    private final KnowledgePointRepository points;

    public NodeLinkRowValidator(NodeLinkRepository targets, KnowledgePointRepository points) {
        this.targets = targets;
        this.points = points;
    }

    @Override public ImportDataType dataType() { return ImportDataType.NODE_LINK; }

    @Override
    @Transactional(readOnly = true)
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        for (Context row : rows) {
            row.requiredAndBroken(Set.of());
            if (row.eligible("target_type") && !TYPES.contains(row.value("target_type"))) {
                row.invalid("target_type", "chỉ nhận TOPIC, WORD, CHAR hoặc GRAMMAR");
            }
            if (row.eligible("kid")) row.value("kid", row.value("kid").toUpperCase(Locale.ROOT));
        }
        for (int offset = 0; offset < rows.size(); offset += BATCH_SIZE) {
            checkReferences(rows.subList(offset, Math.min(rows.size(), offset + BATCH_SIZE)));
        }
        return RowValidationSupport.finish(template, input, rows);
    }

    private void checkReferences(List<Context> rows) {
        Set<List<String>> existing = new HashSet<>();
        targets.topics(keys(rows, "TOPIC")).forEach(target -> existing.add(List.of("TOPIC", target.key())));
        for (LexemeKind kind : LexemeKind.values()) {
            targets.lexemes(kind, keys(rows, kind.name()))
                    .forEach(target -> existing.add(List.of(kind.name(), target.key())));
        }
        Set<String> kids = rows.stream().filter(row -> row.eligible("kid"))
                .map(row -> row.value("kid")).collect(Collectors.toSet());
        Map<String, KnowledgePoint> nodes = kids.isEmpty() ? Map.of() : points.findByCodeIn(kids).stream()
                .collect(Collectors.toMap(KnowledgePoint::getCode, point -> point));
        for (Context row : rows) {
            if (row.eligible("target_type") && row.eligible("target_key")
                    && !existing.contains(List.of(row.value("target_type"), row.value("target_key")))) {
                row.error("target_key", "REFERENCE_NOT_FOUND", "Không tìm thấy nội dung — nhập mẫu nội dung tương ứng trước");
            }
            if (row.eligible("kid") && !nodes.containsKey(row.value("kid"))) {
                row.error("kid", "REFERENCE_NOT_FOUND", "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước");
            }
            // Reference errors take precedence: domain is meaningful only after both references resolve.
            if (row.valid()) {
                String domain = switch (row.value("target_type")) {
                    case "TOPIC", "WORD" -> "TV";
                    case "CHAR" -> "CH";
                    default -> "NP";
                };
                if (!nodes.get(row.value("kid")).getDomain().name().equals(domain)) {
                    row.error("kid", "DOMAIN_MISMATCH", "Nội dung loại " + row.value("target_type")
                            + " chỉ gắn được vào nút miền " + domain);
                }
            }
        }
    }

    private static Set<String> keys(List<Context> rows, String type) {
        return rows.stream().filter(row -> row.eligible("target_type") && type.equals(row.value("target_type"))
                        && row.eligible("target_key"))
                .map(row -> row.value("target_key")).collect(Collectors.toSet());
    }
}
