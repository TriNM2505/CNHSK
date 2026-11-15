package com.cnhsk.learning.vocab;

import static com.cnhsk.learning.vocab.TopicPrerequisiteModels.*;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.learning.vocab.TopicPrerequisiteStore.Row;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicPrerequisiteService {
    private final TopicPrerequisiteStore store;
    private final AuditService audit;

    public TopicPrerequisiteService(TopicPrerequisiteStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public TopicPage list(Integer hsk, String query, int page, int size) {
        if (hsk != null && (hsk < 1 || hsk > 9)) throw invalid("hsk");
        if (query != null && query.length() > 100) throw invalid("q");
        if (page < 0) throw invalid("page");
        if (size < 1 || size > 50) throw invalid("size");
        var listing = store.list(hsk, query == null ? "" : query.trim(), page, size);
        return new TopicPage(listing.rows().stream().map(row -> view(row, listing.refs(), null)).toList(), page, size, listing.total());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminTopic setPrerequisites(long id, long actorId, List<Long> input) {
        if (input == null || input.size() > 10 || input.stream().anyMatch(Objects::isNull)
                || new HashSet<>(input).size() != input.size()) throw invalid("prereqIds");
        List<Long> ids = List.copyOf(input);
        // Global advisory lock must precede the target row lock; learner starts hold FOR SHARE.
        store.lockGraph();
        if (!store.lockTopic(id)) throw missing();
        Map<Long, Row> rows = store.graph();
        if (!rows.keySet().containsAll(ids)) throw missing();
        Row before = rows.get(id);
        Map<Long, List<Long>> graph = new LinkedHashMap<>();
        rows.forEach((key, row) -> graph.put(key, row.prereqIds()));
        graph.put(id, ids);
        List<Long> cycle = TopicPrerequisiteGraph.cycle(graph);
        if (!cycle.isEmpty()) throw new ApiException(ErrorCode.PREREQUISITE_CYCLE, HttpStatus.UNPROCESSABLE_ENTITY,
                "Tiên quyết tạo thành vòng.", Map.of("details", Map.of("path", cycle)));
        if (!TopicPrerequisiteGraph.hasRoot(graph)) throw new ApiException(ErrorCode.PREREQUISITE_NO_ROOT,
                HttpStatus.UNPROCESSABLE_ENTITY, "Đồ thị chủ đề phải có ít nhất một chủ đề gốc.", Map.of());
        store.update(id, ids);
        audit.recordDetail(actorId, "TOPIC_PREREQ_UPDATE", "TOPIC", id, Map.of("before", before.prereqIds(), "after", ids));
        var warnings = ids.stream().map(rows::get).filter(row -> row.hskLevel() > before.hskLevel())
                .map(row -> "Tiên quyết “" + row.name() + "” (HSK " + row.hskLevel() + ") cao hơn chủ đề (HSK " + before.hskLevel() + ").").toList();
        return view(new Row(id, before.name(), before.hskLevel(), ids), rows, warnings);
    }

    private static AdminTopic view(Row row, Map<Long, Row> rows, List<String> warnings) {
        List<TopicRef> refs = row.prereqIds().stream().distinct().map(rows::get).filter(Objects::nonNull)
                .map(ref -> new TopicRef(ref.id(), ref.name(), ref.hskLevel())).toList();
        return new AdminTopic(row.id(), row.name(), row.hskLevel(), refs, refs.isEmpty(), warnings);
    }

    public static ApiException invalid(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }

    private static ApiException missing() {
        return new ApiException(ErrorCode.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy chủ đề hoặc tiên quyết.", Map.of());
    }
}
