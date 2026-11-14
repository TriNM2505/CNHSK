package com.cnhsk.learning.reference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceQueryService {

    private static final String COLUMNS = "id, kind, title, description, url, thumbnail_url, hsk_min, hsk_max, "
            + "tags, author, publisher, published_year, is_affiliate";
    private final NamedParameterJdbcTemplate jdbc;

    public ReferenceQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PublicReferencePage list(String kind, Integer hsk, String tag, int page, int size) {
        ReferenceItem.Kind parsedKind;
        try {
            parsedKind = ReferenceItem.Kind.valueOf(kind == null ? "" : kind);
        } catch (IllegalArgumentException exception) {
            throw invalid("kind");
        }
        if (hsk != null && (hsk < 1 || hsk > 9)) throw invalid("hsk");
        if (tag != null && tag.codePointCount(0, tag.length()) > 30) throw invalid("tag");
        if (page < 0) throw invalid("page");
        if (size < 1 || size > 50) throw invalid("size");

        String where = " FROM learning.reference_items WHERE status = 'PUBLISHED' AND kind = :kind";
        MapSqlParameterSource parameters = new MapSqlParameterSource("kind", parsedKind.name());
        if (hsk != null) {
            where += " AND (hsk_min IS NULL OR hsk_min <= :hsk) AND (hsk_max IS NULL OR hsk_max >= :hsk)";
            parameters.addValue("hsk", hsk);
        }
        if (tag != null) {
            where += " AND :tag = ANY(tags)";
            parameters.addValue("tag", tag);
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + where, parameters, Long.class);
        parameters.addValue("limit", size).addValue("offset", (long) page * size);
        List<PublicReference> items = jdbc.query("SELECT " + COLUMNS + where
                + " ORDER BY sort_order ASC, id ASC LIMIT :limit OFFSET :offset", parameters,
                (row, index) -> reference(row));
        return new PublicReferencePage(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public PublicReference detail(String rawId) {
        long id;
        try {
            if (rawId == null || !rawId.matches("[0-9]+")) throw new NumberFormatException();
            id = Long.parseLong(rawId);
            if (id <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            throw notFound();
        }
        return jdbc.query("SELECT " + COLUMNS
                        + " FROM learning.reference_items WHERE id = :id AND status = 'PUBLISHED'",
                Map.of("id", id), (row, index) -> reference(row)).stream().findFirst().orElseThrow(
                        ReferenceQueryService::notFound);
    }

    private PublicReference reference(ResultSet row) throws SQLException {
        ReferenceItem.Kind kind = ReferenceItem.Kind.valueOf(row.getString("kind"));
        boolean book = kind == ReferenceItem.Kind.BOOK;
        java.sql.Array tags = row.getArray("tags");
        List<String> values;
        try {
            values = List.copyOf(Arrays.asList((String[]) tags.getArray()));
        } finally {
            tags.free();
        }
        return new PublicReference(row.getLong("id"), kind, row.getString("title"), row.getString("description"),
                row.getString("url"), row.getString("thumbnail_url"), row.getObject("hsk_min", Integer.class),
                row.getObject("hsk_max", Integer.class), values, book ? row.getString("author") : null,
                book ? row.getString("publisher") : null, book ? row.getObject("published_year", Integer.class) : null,
                book ? row.getBoolean("is_affiliate") : null);
    }

    public static ApiException invalid(String field) {
        String message = "Tham số yêu cầu không hợp lệ.";
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message,
                Map.of("details", List.of(Map.of("field", field, "message", message))));
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.REFERENCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy tài nguyên tham khảo.", Map.of());
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(requiredProperties = {"id", "kind", "title", "description", "url", "thumbnailUrl", "hskMin", "hskMax", "tags"})
    public record PublicReference(long id, ReferenceItem.Kind kind,
            @Schema(maxLength = 200) String title,
            @Schema(types = {"string", "null"}, maxLength = 2000) String description,
            @Schema(format = "uri", pattern = "^https://") String url,
            @Schema(types = {"string", "null"}, format = "uri") String thumbnailUrl,
            @Schema(type = "integer", types = {"integer", "null"}, minimum = "1", maximum = "9") Integer hskMin,
            @Schema(type = "integer", types = {"integer", "null"}, minimum = "1", maximum = "9") Integer hskMax,
            @ArraySchema(schema = @Schema(type = "string", maxLength = 30)) List<String> tags,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(types = {"string", "null"}) String author,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(types = {"string", "null"}) String publisher,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(type = "integer", types = {"integer", "null"}) Integer publishedYear,
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("isAffiliate") @Schema(types = {"boolean"}) Boolean isAffiliate) {
    }

    @Schema(requiredProperties = {"items", "page", "size", "total"})
    public record PublicReferencePage(List<PublicReference> items,
            @Schema(type = "integer", types = {"integer"}) int page,
            @Schema(type = "integer", types = {"integer"}) int size, long total) {
    }
}
