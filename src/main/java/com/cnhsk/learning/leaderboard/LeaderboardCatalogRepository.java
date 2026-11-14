package com.cnhsk.learning.leaderboard;

import java.sql.Types;
import java.util.List;

import com.cnhsk.learning.leaderboard.LeaderboardDtos.TopicOption;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class LeaderboardCatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public LeaderboardCatalogRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<TopicOption> topics(Integer hsk) {
        return jdbc.query("""
                SELECT id, name, hsk_level FROM learning.topics
                WHERE (:hsk IS NULL OR hsk_level = :hsk)
                ORDER BY hsk_level, order_index, id
                """, new MapSqlParameterSource().addValue("hsk", hsk, Types.INTEGER),
                (rs, index) -> new TopicOption(rs.getLong("id"), rs.getString("name"), rs.getInt("hsk_level")));
    }

    public boolean exists(long topicId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM learning.topics WHERE id = :id)",
                new MapSqlParameterSource("id", topicId), Boolean.class));
    }
}
