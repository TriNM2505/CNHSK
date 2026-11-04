package com.cnhsk.learning.content.importer.write;

import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.content.importer.ExamImportSupport;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExamRowWriter implements RowWriter {
    private final NamedParameterJdbcTemplate jdbc;
    private final ExamImportSupport exams;
    private final AuthApi auth;

    public ExamRowWriter(NamedParameterJdbcTemplate jdbc, ExamImportSupport exams, AuthApi auth) {
        this.jdbc = jdbc;
        this.exams = exams;
        this.auth = auth;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.EXAM;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        var values = row.values();
        String code = values.get("code");
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "exam:import:" + code), rs -> null);
        var existing = exams.exams(List.of(code), true).stream().findFirst();
        var sections = ExamImportSupport.sections(values.get("sections"));
        int level = Integer.parseInt(values.get("hsk_level"));
        if (existing.isPresent()) {
            ExamImportSupport.conflict(exams.linkedQuestions(List.of(code)).getOrDefault(code, List.of()), sections, level)
                    .ifPresent(error -> { throw new RowWriteRejectedException(error.code(), error.field(), error.message()); });
            if (existing.get().matches(values)) return RowAction.UNCHANGED;
        }
        var args = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("code", code).addValue("title", values.get("title")).addValue("level", level)
                .addValue("sections", exams.writeSections(sections)).addValue("status", values.get("status"));
        if (existing.isEmpty()) {
            jdbc.update("""
                    INSERT INTO learning.exams (code, title, hsk_level, sections, status, created_by, published_at)
                    VALUES (:code, :title, :level, CAST(:sections AS jsonb), :status, :user,
                        CASE WHEN :status = 'PUBLISHED' THEN now() END)
                    """, args.addValue("user", auth.currentUser().userId()));
            return RowAction.CREATE;
        }
        jdbc.update("""
                UPDATE learning.exams SET title = :title, hsk_level = :level, sections = CAST(:sections AS jsonb),
                    status = :status, published_at = COALESCE(published_at, CASE WHEN :status = 'PUBLISHED' THEN now() END)
                WHERE code = :code
                """, args);
        return RowAction.UPDATE;
    }
}
