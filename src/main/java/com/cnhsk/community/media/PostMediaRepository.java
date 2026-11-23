package com.cnhsk.community.media;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.community.media.web.MediaItem;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Attachments of posts, kept in display order. */
@Repository
public class PostMediaRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostMediaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    /** Replaces all attachments of a post with the given list, in that order. */
    public void replace(long postId, List<MediaItem> media) {
        jdbc.update("DELETE FROM community.post_media WHERE post_id = :postId", Map.of("postId", postId));
        for (int position = 0; position < media.size(); position++) {
            MediaItem item = media.get(position);
            jdbc.update("""
                    INSERT INTO community.post_media(post_id, position, kind, url, public_id)
                    VALUES (:postId, :position, :kind, :url, :publicId)
                    """, new MapSqlParameterSource().addValue("postId", postId).addValue("position", position)
                    .addValue("kind", item.kind().name()).addValue("url", item.url())
                    .addValue("publicId", item.publicId()));
        }
    }

    public Map<Long, List<MediaItem>> findByPostIds(Collection<Long> postIds) {
        Map<Long, List<MediaItem>> result = new LinkedHashMap<>();
        if (postIds.isEmpty()) return result;
        jdbc.query("""
                SELECT post_id, kind, url, public_id FROM community.post_media
                WHERE post_id IN (:ids) ORDER BY post_id, position
                """, Map.of("ids", postIds), row -> {
            result.computeIfAbsent(row.getLong("post_id"), id -> new ArrayList<>()).add(new MediaItem(
                    MediaItem.MediaKind.valueOf(row.getString("kind")), row.getString("url"),
                    row.getString("public_id")));
        });
        return result;
    }

    public List<MediaItem> findByPostId(long postId) {
        return findByPostIds(List.of(postId)).getOrDefault(postId, List.of());
    }
}
