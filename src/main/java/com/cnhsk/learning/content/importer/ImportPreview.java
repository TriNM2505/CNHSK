package com.cnhsk.learning.content.importer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.importer.template.ImportDataType;

public record ImportPreview(long runId, ImportDataType dataType, String fileName, String status,
        Totals totals, boolean alreadyImported, Instant expiresAt, List<PreviewRow> preview,
        List<CsvReader.RowError> errors, int errorCount) {

    public record Totals(int total, int valid, int invalid, int create, int update, int unchanged) { }

    public enum RowAction { CREATE, UPDATE, UNCHANGED }

    public record PreviewRow(int line, RowAction action, Map<String, String> values) { }
}
