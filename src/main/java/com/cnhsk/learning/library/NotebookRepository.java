package com.cnhsk.learning.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cnhsk.learning.library.NotebookService.Note;
import com.cnhsk.learning.library.web.NoteWrite;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Notebook SQL stays in learning and binds every value supplied by a caller. */
@Repository
public class NotebookRepository {

    private static final String COLUMNS = "id, title, content, tags, lexeme_id, source_type, created_at, updated_at";
    private static final String OWNED = """
            FROM learning.collection_items i JOIN learning.collections c ON c.id = i.collection_id
            WHERE c.user_id = :userId AND c.kind = 'NOTEBOOK' AND c.deleted_at IS NULL AND i.deleted_at IS NULL
            """;
    private static final String SELECT = "SELECT i." + COLUMNS.replace(", ", ", i.") + " " + OWNED;
    private final NamedParameterJdbcTemplate jdbc;
    private volatile String similarityFunction;

    public NotebookRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lock(long userId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))",
                Map.of("key", "library:notebook:" + userId), rs -> null);
    }

    public long notebook(long userId) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM learning.collections WHERE user_id=:userId AND kind='NOTEBOOK'
                AND deleted_at IS NULL ORDER BY id LIMIT 1
                """, Map.of("userId", userId), Long.class);
        if (!ids.isEmpty()) return ids.get(0);
        return jdbc.queryForObject("""
                INSERT INTO learning.collections(user_id, kind, name) VALUES (:userId, 'NOTEBOOK', 'Sổ tay') RETURNING id
                """, Map.of("userId", userId), Long.class);
    }

    public long count(long userId) {
        return jdbc.queryForObject("SELECT count(*) " + OWNED, Map.of("userId", userId), Long.class);
    }

    public Optional<Note> find(long userId, long id) {
        return jdbc.query(SELECT + " AND i.id=:id", Map.of("userId", userId, "id", id), this::note).stream().findFirst();
    }

    public Optional<Long> duplicate(long userId, long lexemeId, long excludedId) {
        return jdbc.queryForList("SELECT i.id " + OWNED + " AND i.lexeme_id=:lexemeId AND i.id<>:excludedId",
                Map.of("userId", userId, "lexemeId", lexemeId, "excludedId", excludedId), Long.class).stream().findFirst();
    }

    public boolean lexemeExists(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM learning.lexemes WHERE id=:id)",
                Map.of("id", id), Boolean.class));
    }

    public Note insert(long collectionId, NoteWrite request) {
        Map<String, Object> values = values(request);
        values.put("collectionId", collectionId);
        return jdbc.queryForObject("""
                INSERT INTO learning.collection_items(collection_id, title, content, tags, lexeme_id, source_type, updated_at)
                VALUES (:collectionId, :title, :content, :tags, :lexemeId, :sourceType, clock_timestamp()) RETURNING
                """ + COLUMNS, values, this::note);
    }

    public Note update(long userId, long id, NoteWrite request) {
        Map<String, Object> values = values(request);
        values.put("userId", userId);
        values.put("id", id);
        return jdbc.queryForObject("""
                UPDATE learning.collection_items i SET title=:title, content=:content, tags=:tags,
                    lexeme_id=:lexemeId, source_type=:sourceType, updated_at=clock_timestamp()
                WHERE i.id=:id AND i.deleted_at IS NULL AND i.collection_id IN (
                    SELECT id FROM learning.collections WHERE user_id=:userId AND kind='NOTEBOOK' AND deleted_at IS NULL)
                RETURNING
                """ + "i." + COLUMNS.replace(", ", ", i."), values, this::note);
    }

    public Page search(long userId, String query, String tag, int page, int size) {
        Map<String, Object> values = new HashMap<>();
        values.put("userId", userId);
        String where = "";
        String order = "i.updated_at DESC, i.id DESC";
        if (query != null) {
            String escaped = query.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            where += " AND (i.title ILIKE :pattern ESCAPE '!' OR i.content ILIKE :pattern ESCAPE '!')";
            values.put("pattern", "%" + escaped + "%");
        }
        if (tag != null) {
            where += " AND :tag = ANY(i.tags)";
            values.put("tag", tag);
        }
        long total = jdbc.queryForObject("SELECT count(*) " + OWNED + where, values, Long.class);
        if (query != null) {
            String function = similarityFunction();
            order = "greatest(" + function + "(lower(coalesce(i.title,'')), lower(:query)), "
                    + function + "(lower(i.content), lower(:query))) DESC, " + order;
            values.put("query", query);
        }
        values.put("limit", size);
        values.put("offset", (long) page * size);
        return new Page(jdbc.query(SELECT + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", values, this::note), total);
    }

    private String similarityFunction() {
        String function = similarityFunction;
        if (function == null) {
            String schema = jdbc.queryForObject("""
                    SELECT n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
                    WHERE e.extname=:extension
                    """, Map.of("extension", "pg_trgm"), String.class);
            // Only a quoted identifier from PostgreSQL's extension catalog is used as SQL syntax, never user input.
            function = "\"" + schema.replace("\"", "\"\"") + "\".similarity";
            similarityFunction = function;
        }
        return function;
    }

    private Map<String, Object> values(NoteWrite request) {
        Map<String, Object> values = new HashMap<>();
        values.put("title", request.title());
        values.put("content", request.content());
        values.put("tags", request.tags().toArray(String[]::new));
        values.put("lexemeId", request.lexemeId());
        values.put("sourceType", request.sourceType());
        return values;
    }

    private Note note(ResultSet rs, int row) throws SQLException {
        java.sql.Array tags = rs.getArray("tags");
        try {
            return new Note(rs.getLong("id"), rs.getString("title"), rs.getString("content"),
                    List.copyOf(Arrays.asList((String[]) tags.getArray())), rs.getObject("lexeme_id", Long.class),
                    rs.getString("source_type"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
        } finally {
            tags.free();
        }
    }

    public record Page(List<Note> items, long total) { }
}
