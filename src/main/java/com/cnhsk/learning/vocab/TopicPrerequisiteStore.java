package com.cnhsk.learning.vocab;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TopicPrerequisiteStore {
    private static final String COLUMNS = "id,name,hsk_level,prereq_ids";
    private final NamedParameterJdbcTemplate jdbc;

    public TopicPrerequisiteStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    Listing list(Integer hsk, String query, int page, int size) {
        String filter = " WHERE (:hsk IS NULL OR hsk_level=:hsk) AND name ILIKE :q ESCAPE '!'";
        var args = new MapSqlParameterSource().addValue("hsk", hsk, java.sql.Types.INTEGER)
                .addValue("q", "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%")
                .addValue("limit", size).addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("SELECT count(*) FROM learning.topics" + filter, args, Long.class);
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM learning.topics" + filter
                + " ORDER BY hsk_level,order_index,id LIMIT :limit OFFSET :offset", args, (rs, index) -> row(rs));
        Set<Long> ids = rows.stream().flatMap(row -> row.prereqIds().stream()).collect(Collectors.toSet());
        Map<Long, Row> refs = new LinkedHashMap<>();
        if (!ids.isEmpty()) jdbc.query("SELECT " + COLUMNS + " FROM learning.topics WHERE id IN (:ids)",
                Map.of("ids", ids), (rs, index) -> row(rs)).forEach(row -> refs.put(row.id(), row));
        return new Listing(rows, refs, total);
    }

    void lockGraph() {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended('topic-prereq',0))", Map.of(), rs -> null);
    }

    boolean lockTopic(long id) {
        return !jdbc.queryForList("SELECT id FROM learning.topics WHERE id=:id FOR UPDATE", Map.of("id", id), Long.class).isEmpty();
    }

    Map<Long, Row> graph() {
        Map<Long, Row> rows = new LinkedHashMap<>();
        jdbc.query("SELECT " + COLUMNS + " FROM learning.topics ORDER BY id", Map.of(), (rs, index) -> row(rs))
                .forEach(row -> rows.put(row.id(), row));
        return rows;
    }

    void update(long id, List<Long> ids) {
        String array = ids.stream().map(Object::toString).collect(Collectors.joining(",", "{", "}"));
        jdbc.update("UPDATE learning.topics SET prereq_ids=CAST(:ids AS bigint[]) WHERE id=:id", Map.of("id", id, "ids", array));
    }

    private static Row row(ResultSet rs) throws SQLException {
        Object[] ids = (Object[]) rs.getArray("prereq_ids").getArray();
        return new Row(rs.getLong("id"), rs.getString("name"), rs.getInt("hsk_level"),
                Arrays.stream(ids).map(value -> ((Number) value).longValue()).toList());
    }

    record Row(long id, String name, int hskLevel, List<Long> prereqIds) { }
    record Listing(List<Row> rows, Map<Long, Row> refs, long total) { }
}
