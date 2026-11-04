package com.cnhsk.learning.content.importer.validate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.cnhsk.learning.content.importer.CsvReader;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.template.ImportTemplate;
import com.cnhsk.learning.content.importer.validate.RowValidationSupport.Context;
import com.cnhsk.learning.knowledge.PrereqGraph;
import com.cnhsk.learning.knowledge.PrereqGraph.EdgeType;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeEdgeRowValidator implements RowValidator {
    private final KnowledgePointRepository points;

    public KnowledgeEdgeRowValidator(KnowledgePointRepository points) {
        this.points = points;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.KNOWLEDGE_EDGE;
    }

    @Override
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        List<Integer> lines = input.rows().stream().map(CsvReader.Row::line).sorted().toList();
        PrereqGraph graph = new PrereqGraph(points.findAll().stream().map(point -> new PrereqGraph.Node(
                point.getId(), point.getCode(), point.getHskLevel(), Arrays.asList(point.getPrereqIds()),
                Arrays.asList(point.getSupportIds()))).toList());
        Map<List<String>, Integer> firstValidLines = new HashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            Context row = rows.get(index);
            row.requiredAndBroken(Set.of());
            for (String field : List.of("from_kid", "to_kid")) {
                if (row.eligible(field)) {
                    row.value(field, row.value(field).toUpperCase(Locale.ROOT));
                    if (!graph.hasNode(row.value(field))) {
                        row.error(field, "REFERENCE_NOT_FOUND", "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước");
                    }
                }
            }
            if (row.eligible("edge_type") && !Set.of("PREREQ", "SUPPORT").contains(row.value("edge_type"))) {
                row.invalid("edge_type", "chỉ nhận PREREQ hoặc SUPPORT");
            }
            if (!row.valid()) continue;
            List<String> key = List.of(row.value("from_kid"), row.value("to_kid"));
            Integer firstLine = firstValidLines.get(key);
            if (firstLine != null) {
                row.error(null, "DUPLICATE_IN_FILE", "Trùng khoá với dòng " + firstLine);
                continue;
            }
            EdgeType type = EdgeType.valueOf(row.value("edge_type"));
            graph.rejection(key.get(0), key.get(1), type)
                    .ifPresent(error -> row.error(error.field(), error.code(), error.message()));
            if (row.valid()) {
                firstValidLines.put(key, lines.get(index));
                graph.add(key.get(0), key.get(1), type);
            }
        }
        return RowValidationSupport.finish(template, input, rows);
    }
}
