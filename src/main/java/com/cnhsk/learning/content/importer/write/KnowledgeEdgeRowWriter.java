package com.cnhsk.learning.content.importer.write;

import java.util.Arrays;
import java.util.Map;

import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.knowledge.PrereqGraph;
import com.cnhsk.learning.knowledge.PrereqGraph.EdgeType;
import com.cnhsk.learning.knowledge.entity.KnowledgePoint;
import com.cnhsk.learning.knowledge.repository.KnowledgePointRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KnowledgeEdgeRowWriter implements RowWriter {
    private final KnowledgePointRepository points;
    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgeEdgeRowWriter(KnowledgePointRepository points, NamedParameterJdbcTemplate jdbc) {
        this.points = points;
        this.jdbc = jdbc;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.KNOWLEDGE_EDGE;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        // Always serialize graph changes before locking a target: different targets can still form a cycle.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "knowledge:edge-graph"), result -> null);
        String from = row.values().get("from_kid");
        String to = row.values().get("to_kid");
        EdgeType type = EdgeType.valueOf(row.values().get("edge_type"));
        KnowledgePoint target = points.findByCodeForUpdate(to).orElseThrow(() -> new RowWriteRejectedException(
                "REFERENCE_NOT_FOUND", "to_kid", "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước"));
        var snapshot = points.findAll();
        PrereqGraph graph = new PrereqGraph(snapshot.stream().map(point -> new PrereqGraph.Node(
                point.getId(), point.getCode(), point.getHskLevel(), Arrays.asList(point.getPrereqIds()),
                Arrays.asList(point.getSupportIds()))).toList());
        graph.rejection(from, to, type).ifPresent(error -> {
            throw new RowWriteRejectedException(error.code(), error.field(), error.message());
        });
        if (graph.contains(from, to, type)) return RowAction.UNCHANGED;
        long sourceId = snapshot.stream().filter(point -> point.getCode().equals(from)).findFirst().orElseThrow().getId();
        Long[] old = type == EdgeType.PREREQ ? target.getPrereqIds() : target.getSupportIds();
        Long[] appended = Arrays.copyOf(old, old.length + 1);
        appended[old.length] = sourceId;
        if (type == EdgeType.PREREQ) target.setPrereqIds(appended);
        else target.setSupportIds(appended);
        points.saveAndFlush(target);
        return RowAction.CREATE;
    }
}
