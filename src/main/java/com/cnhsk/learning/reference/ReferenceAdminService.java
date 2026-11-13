package com.cnhsk.learning.reference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.reference.ReferenceItem.Kind;
import com.cnhsk.learning.reference.ReferenceItem.Status;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.audit.AuditService.ReportStat;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceAdminService {

    private static final String COLUMNS = "id,kind,title,description,url,thumbnail_url,hsk_min,hsk_max,tags,"
            + "sort_order,author,publisher,published_year,is_affiliate,status,published_at,updated_at";
    private static final String REPORT_ACTION = "REFERENCE_REPORTED";
    private final AuthApi auth;
    private final NamedParameterJdbcTemplate jdbc;
    private final ReferenceUrlPolicy policy;
    private final AuditService audit;
    private final Clock clock;

    public ReferenceAdminService(AuthApi auth, NamedParameterJdbcTemplate jdbc, ReferenceUrlPolicy policy,
            AuditService audit, Clock clock) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.policy = policy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminReferencePage list(String kind, String status, Boolean reported, int page, int size) {
        editor();
        Kind parsedKind = kind == null ? null : enumValue(Kind.class, kind, "kind");
        Status parsedStatus = status == null ? null : enumValue(Status.class, status, "status");
        if (page < 0) throw invalid("page");
        if (size < 1 || size > 50) throw invalid("size");
        String where = " FROM learning.reference_items r WHERE true";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (parsedKind != null) {
            where += " AND r.kind = :kind";
            parameters.addValue("kind", parsedKind.name());
        }
        if (parsedStatus != null) {
            where += " AND r.status = :status";
            parameters.addValue("status", parsedStatus.name());
        }
        if (Boolean.TRUE.equals(reported)) {
            where += " AND EXISTS (SELECT 1 FROM shared.audit_logs a WHERE a.target_type = 'REFERENCE'"
                    + " AND a.target_id = r.id AND a.action = 'REFERENCE_REPORTED' AND a.created_at >= r.updated_at)";
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + where, parameters, Long.class);
        parameters.addValue("limit", size).addValue("offset", (long) page * size);
        List<Row> rows = jdbc.query("SELECT " + COLUMNS + where
                + " ORDER BY r.sort_order ASC,r.id ASC LIMIT :limit OFFSET :offset", parameters, this::row);
        Map<Long, ReportStat> stats = stats(rows);
        return new AdminReferencePage(rows.stream().map(row -> response(row, stats)).toList(), page, size,
                total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public AdminReference detail(String rawId) {
        editor();
        Row row = read(id(rawId), false);
        return response(row, stats(List.of(row)));
    }

    @Transactional
    public AdminReference create(ReferenceInput input) {
        CurrentUser caller = editor();
        validate(input);
        validateUrl(input);
        Instant now = now();
        MapSqlParameterSource parameters = parameters(input, now).addValue("creator", caller.userId());
        String tags = tags(input, parameters);
        Row created = jdbc.queryForObject("INSERT INTO learning.reference_items(kind,title,description,url,thumbnail_url,"
                + "hsk_min,hsk_max,tags,sort_order,author,publisher,published_year,is_affiliate,status,created_by,"
                + "created_at,updated_at) VALUES (:kind,:title,:description,:url,:thumbnailUrl,:hskMin,:hskMax," + tags
                + ",:sortOrder,:author,:publisher,:publishedYear,:affiliate,'DRAFT',:creator,:now,:now) RETURNING "
                + COLUMNS, parameters, this::row);
        record(caller.userId(), "REFERENCE_CREATED", null, created);
        return response(created, stats(List.of(created)));
    }

    @Transactional
    public AdminReference update(String rawId, ReferenceInput input) {
        CurrentUser caller = editor();
        Row before = read(id(rawId), true);
        if (input == null) throw invalid("request");
        if (before.status() == Status.PUBLISHED) requirePublishable(input);
        validate(input);
        validateUrl(input);
        MapSqlParameterSource parameters = parameters(input, now()).addValue("id", before.id());
        String tags = tags(input, parameters);
        Row updated = jdbc.queryForObject("UPDATE learning.reference_items SET kind=:kind,title=:title,"
                + "description=:description,url=:url,thumbnail_url=:thumbnailUrl,hsk_min=:hskMin,hsk_max=:hskMax,"
                + "tags=" + tags + ",sort_order=:sortOrder,author=:author,publisher=:publisher,"
                + "published_year=:publishedYear,is_affiliate=:affiliate,updated_at=:now WHERE id=:id RETURNING "
                + COLUMNS, parameters, this::row);
        record(caller.userId(), "REFERENCE_UPDATED", before, updated);
        return response(updated, stats(List.of(updated)));
    }

    @Transactional
    public AdminReference transition(String rawId, Action action) {
        CurrentUser caller = editor();
        Row before = read(id(rawId), true);
        Status destination = switch (action) {
            case PUBLISH -> Status.PUBLISHED;
            case HIDE -> Status.HIDDEN;
            case UNHIDE -> before.status() == Status.HIDDEN ? Status.DRAFT : before.status();
        };
        if (before.status() == destination) return response(before, stats(List.of(before)));
        if (action == Action.PUBLISH) {
            requirePublishable(before.input());
            validateUrl(before.input());
        }
        Instant now = now();
        Instant published = action == Action.PUBLISH && before.publishedAt() == null ? now : before.publishedAt();
        Row updated = jdbc.queryForObject("UPDATE learning.reference_items SET status=:status,published_at=:publishedAt,"
                + "updated_at=:now WHERE id=:id RETURNING " + COLUMNS,
                new MapSqlParameterSource("id", before.id()).addValue("status", destination.name())
                        .addValue("publishedAt", timestamp(published)).addValue("now", timestamp(now)), this::row);
        String auditAction = switch (action) {
            case PUBLISH -> "REFERENCE_PUBLISHED";
            case HIDE -> "REFERENCE_HIDDEN";
            case UNHIDE -> "REFERENCE_UNHIDDEN";
        };
        record(caller.userId(), auditAction, before, updated);
        return response(updated, stats(List.of(updated)));
    }

    private CurrentUser editor() {
        CurrentUser user = auth.currentUser();
        if (!user.roles().contains(RoleCode.CONTENT_EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return user;
    }

    private Row read(long id, boolean lock) {
        return jdbc.query("SELECT " + COLUMNS + " FROM learning.reference_items WHERE id=:id"
                        + (lock ? " FOR UPDATE" : ""), Map.of("id", id), this::row).stream()
                .findFirst().orElseThrow(ReferenceAdminService::notFound);
    }

    private Map<Long, ReportStat> stats(List<Row> rows) {
        if (rows.isEmpty()) return Map.of();
        Set<Long> ids = rows.stream().map(Row::id).collect(Collectors.toSet());
        Map<Long, Instant> since = rows.stream().collect(Collectors.toMap(Row::id, Row::updatedAt));
        return audit.countByTargets(REPORT_ACTION, "REFERENCE", ids, since);
    }

    private void record(long actor, String action, Row before, Row after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        audit.recordDetail(actor, action, "REFERENCE", after.id(), detail);
    }

    private void validate(ReferenceInput input) {
        if (input == null) throw invalid("request");
        if (input.kind() == null) throw invalid("kind");
        if (input.title() == null || input.title().isBlank()) throw invalid("title");
        length(input.title(), 200, "title");
        if (input.url() == null) throw invalid("url");
        length(input.url(), 2000, "url");
        length(input.description(), 2000, "description");
        length(input.thumbnailUrl(), 2000, "thumbnailUrl");
        level(input.hskMin(), "hskMin");
        level(input.hskMax(), "hskMax");
        if (input.hskMin() != null && input.hskMax() != null && input.hskMin() > input.hskMax()) throw invalid("hskMax");
        if (input.tags().size() > 10) throw invalid("tags");
        for (String tag : input.tags()) {
            if (tag == null) throw invalid("tags");
            length(tag, 30, "tags");
        }
        length(input.author(), 200, "author");
        length(input.publisher(), 200, "publisher");
        if (input.publishedYear() != null && (input.publishedYear() < 1900 || input.publishedYear() > 2100)) {
            throw invalid("publishedYear");
        }
        if (input.kind() != Kind.BOOK && (input.author() != null || input.publisher() != null
                || input.publishedYear() != null || input.isAffiliate())) throw invalid("kind");
    }

    private void requirePublishable(ReferenceInput input) {
        List<String> missing = new ArrayList<>();
        if (input.title() == null || input.title().isBlank()) missing.add("title");
        if (input.url() == null || input.url().isBlank()) missing.add("url");
        if (input.kind() == Kind.BOOK && (input.author() == null || input.author().isBlank())) missing.add("author");
        if (!missing.isEmpty()) {
            throw new ApiException(ErrorCode.PUBLISH_INVALID_RESOURCE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tài nguyên chưa đủ thông tin để công bố.", Map.of("details", missing.stream()
                            .map(field -> Map.of("field", field, "message", "Thông tin bắt buộc để công bố.")).toList()));
        }
    }

    private void validateUrl(ReferenceInput input) {
        policy.validate(input.kind(), input.url(), input.thumbnailUrl()).ifPresent(violation -> {
            ErrorCode code = switch (violation.reason()) {
                case MALFORMED_URL -> ErrorCode.MALFORMED_URL;
                case NON_HTTPS_LINK -> ErrorCode.NON_HTTPS_LINK;
                case DOMAIN_NOT_ALLOWED -> ErrorCode.DOMAIN_NOT_ALLOWED;
            };
            throw new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, "Đường dẫn không hợp lệ.",
                    Map.of("details", List.of(Map.of("field", violation.field(), "message", "Đường dẫn không hợp lệ."))));
        });
    }

    private MapSqlParameterSource parameters(ReferenceInput input, Instant now) {
        return new MapSqlParameterSource("kind", input.kind().name()).addValue("title", input.title())
                .addValue("description", input.description()).addValue("url", input.url())
                .addValue("thumbnailUrl", input.thumbnailUrl()).addValue("hskMin", input.hskMin())
                .addValue("hskMax", input.hskMax()).addValue("sortOrder", input.sortOrder())
                .addValue("author", input.author()).addValue("publisher", input.publisher())
                .addValue("publishedYear", input.publishedYear()).addValue("affiliate", input.isAffiliate())
                .addValue("now", timestamp(now));
    }

    private String tags(ReferenceInput input, MapSqlParameterSource parameters) {
        List<String> placeholders = new ArrayList<>();
        for (int index = 0; index < input.tags().size(); index++) {
            placeholders.add(":tag" + index);
            parameters.addValue("tag" + index, input.tags().get(index));
        }
        return "ARRAY[" + String.join(",", placeholders) + "]::varchar(30)[]";
    }

    private Row row(ResultSet rs, int index) throws SQLException {
        java.sql.Array array = rs.getArray("tags");
        List<String> tags;
        try {
            tags = List.copyOf(Arrays.asList((String[]) array.getArray()));
        } finally {
            array.free();
        }
        return new Row(rs.getLong("id"), Kind.valueOf(rs.getString("kind")), rs.getString("title"),
                rs.getString("description"), rs.getString("url"), rs.getString("thumbnail_url"),
                rs.getObject("hsk_min", Integer.class), rs.getObject("hsk_max", Integer.class), tags,
                rs.getInt("sort_order"), rs.getString("author"), rs.getString("publisher"),
                rs.getObject("published_year", Integer.class), rs.getBoolean("is_affiliate"),
                Status.valueOf(rs.getString("status")), rs.getTimestamp("published_at") == null ? null
                        : rs.getTimestamp("published_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private AdminReference response(Row row, Map<Long, ReportStat> stats) {
        ReportStat stat = stats.getOrDefault(row.id(), new ReportStat(0, null));
        boolean book = row.kind() == Kind.BOOK;
        return new AdminReference(row.id(), row.kind(), row.title(), row.description(), row.url(), row.thumbnailUrl(),
                row.hskMin(), row.hskMax(), row.tags(), book ? row.author() : null, book ? row.publisher() : null,
                book ? row.publishedYear() : null, book ? row.affiliate() : null, row.status(), row.sortOrder(),
                stat.count(), stat.lastAt(), row.publishedAt(), row.updatedAt());
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private OffsetDateTime timestamp(Instant time) { return time == null ? null : OffsetDateTime.ofInstant(time, ZoneOffset.UTC); }
    private static void length(String value, int maximum, String field) {
        if (value != null && value.codePointCount(0, value.length()) > maximum) throw invalid(field);
    }
    private static void level(Integer value, String field) {
        if (value != null && (value < 1 || value > 9)) throw invalid(field);
    }
    private static long id(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) { throw notFound(); }
    }
    private static ApiException notFound() {
        return new ApiException(ErrorCode.REFERENCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy tài nguyên tham khảo.", Map.of());
    }
    public static ApiException invalid(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", field, "message", "Dữ liệu không hợp lệ."))));
    }
    public static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) { throw invalid(field); }
    }

    public enum Action { PUBLISH, HIDE, UNHIDE }

    public record ReferenceInput(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 200) String title,
            String description, @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 2000) String url, String thumbnailUrl,
            Integer hskMin, Integer hskMax, List<String> tags, int sortOrder, String author, String publisher,
            Integer publishedYear, @JsonProperty("isAffiliate") boolean isAffiliate) {
        public ReferenceInput {
            tags = tags == null ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(tags));
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminReference(long id, Kind kind, String title, String description, String url, String thumbnailUrl,
            Integer hskMin, Integer hskMax, List<String> tags,
            @JsonInclude(JsonInclude.Include.NON_NULL) String author,
            @JsonInclude(JsonInclude.Include.NON_NULL) String publisher,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer publishedYear,
            @JsonInclude(JsonInclude.Include.NON_NULL) @JsonProperty("isAffiliate") Boolean isAffiliate,
            Status status, int sortOrder, @Schema(type = "integer", types = {"integer"}, minimum = "0") long reportCount,
            Instant lastReportedAt, Instant publishedAt, Instant updatedAt) {}

    public record AdminReferencePage(List<AdminReference> items, int page, int size, long total) {}

    private record Row(long id, Kind kind, String title, String description, String url, String thumbnailUrl,
            Integer hskMin, Integer hskMax, List<String> tags, int sortOrder, String author, String publisher,
            Integer publishedYear, boolean affiliate, Status status, Instant publishedAt, Instant updatedAt) {
        ReferenceInput input() {
            return new ReferenceInput(kind, title, description, url, thumbnailUrl, hskMin, hskMax, tags,
                    sortOrder, author, publisher, publishedYear, affiliate);
        }
    }
}
