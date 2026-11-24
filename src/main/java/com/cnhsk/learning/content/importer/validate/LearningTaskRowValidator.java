package com.cnhsk.learning.content.importer.validate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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

/**
 * Speaking and writing tasks. Target domains follow the rubric of the task kind (appendix E):
 * R-NOI C2 pronunciation → NA, C3 vocabulary → TV, C4 grammar → NP;
 * R-VIET C2 grammar → NP, C3 vocabulary → TV, C4 characters → CH. C1/C5 always feed the task's NL node.
 */
@Component
public class LearningTaskRowValidator implements RowValidator {
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{3,50}$");
    private static final Pattern TARGET = Pattern.compile("^(C[2-4]):((NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3})$");
    private static final Map<String, Map<String, String>> DOMAINS = Map.of(
            "SPEAKING", Map.of("C2", "NA", "C3", "TV", "C4", "NP"),
            "WRITING", Map.of("C2", "NP", "C3", "TV", "C4", "CH"));
    private static final int MAX_TARGETS = 12;
    private final KnowledgePointRepository points;

    public LearningTaskRowValidator(KnowledgePointRepository points) {
        this.points = points;
    }

    @Override public ImportDataType dataType() { return ImportDataType.LEARNING_TASK; }

    private record Target(String criterion, String kid) { }

    @Override
    @Transactional(readOnly = true)
    public RowValidationResult validate(CsvReader.ReadResult input) {
        ImportTemplate template = ImportTemplate.forType(dataType());
        List<Context> rows = RowValidationSupport.rows(template, input);
        Map<Context, List<Target>> parsed = new java.util.HashMap<>();
        for (Context row : rows) {
            row.requiredAndBroken(Set.of("prompt_zh"));
            row.lengths();
            for (String column : List.of("code", "kind", "nl_kid", "is_active")) {
                if (row.eligible(column)) row.value(column, row.value(column).toUpperCase(Locale.ROOT));
            }
            row.matching("code", CODE, "chỉ gồm A–Z, 0–9, _ hoặc -, dài 3–50");
            if (row.eligible("kind") && !DOMAINS.containsKey(row.value("kind"))) row.invalid("kind", "chỉ nhận SPEAKING hoặc WRITING");
            row.integer("hsk_level", 1, 9, true);
            row.integer("min_chars", 1, 2000, false);
            row.integer("max_seconds", 10, 300, false);
            if (row.eligible("is_active") && !Set.of("TRUE", "FALSE").contains(row.value("is_active"))) {
                row.invalid("is_active", "chỉ nhận TRUE hoặc FALSE");
            }
            if (row.eligible("targets")) {
                List<Target> targets = targets(row.value("targets"));
                if (targets == null) {
                    row.invalid("targets", "dạng C2:KID|C3:KID|C4:KID, tiêu chí C2–C4, không lặp, tối đa " + MAX_TARGETS);
                } else {
                    parsed.put(row, targets);
                    row.value("targets", targets.stream().map(t -> t.criterion() + ":" + t.kid())
                            .collect(Collectors.joining("|")));
                }
            }
        }
        Set<String> kids = new HashSet<>();
        rows.stream().filter(row -> row.eligible("nl_kid")).forEach(row -> kids.add(row.value("nl_kid")));
        parsed.values().forEach(list -> list.forEach(target -> kids.add(target.kid())));
        Map<String, KnowledgePoint> nodes = kids.isEmpty() ? Map.of() : points.findByCodeIn(kids).stream()
                .collect(Collectors.toMap(KnowledgePoint::getCode, point -> point));
        for (Context row : rows) {
            if (row.eligible("nl_kid")) {
                KnowledgePoint nl = nodes.get(row.value("nl_kid"));
                if (nl == null) row.error("nl_kid", "REFERENCE_NOT_FOUND", "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước");
                else if (!nl.getDomain().name().equals("NL")) row.error("nl_kid", "DOMAIN_MISMATCH", "Nhiệm vụ phải gắn nút miền NL");
            }
            List<Target> targets = parsed.get(row);
            if (targets == null || !row.eligible("targets")) continue;
            for (Target target : targets) {
                KnowledgePoint node = nodes.get(target.kid());
                if (node == null) {
                    row.error("targets", "REFERENCE_NOT_FOUND", "Nút " + target.kid() + " chưa tồn tại");
                    break;
                }
                String expected = row.eligible("kind") ? DOMAINS.get(row.value("kind")).get(target.criterion()) : null;
                if (expected != null && !expected.equals(node.getDomain().name())) {
                    row.error("targets", "DOMAIN_MISMATCH", "Tiêu chí " + target.criterion() + " của bài "
                            + row.value("kind") + " chỉ nhận nút miền " + expected);
                    break;
                }
            }
        }
        return RowValidationSupport.finish(template, input, rows);
    }

    /** Parses and sorts "C4:np1-001 | C2:NA1-006"; null when malformed. */
    private static List<Target> targets(String cell) {
        List<Target> result = new ArrayList<>();
        Set<Target> seen = new HashSet<>();
        for (String part : cell.split("\\|", -1)) {
            var matcher = TARGET.matcher(part.strip().toUpperCase(Locale.ROOT));
            if (!matcher.matches()) return null;
            Target target = new Target(matcher.group(1), matcher.group(2));
            if (!seen.add(target)) return null;
            result.add(target);
        }
        if (result.isEmpty() || result.size() > MAX_TARGETS) return null;
        result.sort(Comparator.comparing(Target::criterion).thenComparing(Target::kid));
        return List.copyOf(result);
    }
}
