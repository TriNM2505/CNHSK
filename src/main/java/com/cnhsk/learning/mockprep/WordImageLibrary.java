package com.cnhsk.learning.mockprep;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.media.MediaProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One picture per HSK 1–3 word, kept in the word's {@code extra.image_url}. Picture questions of the mock exams take
 * their pictures from here, so a word needs a picture only once however many papers use it. Editors upload the
 * pictures straight to the word-image folder; only files from that folder are accepted.
 */
@Service
public class WordImageLibrary {
    static final int MAX_LEVEL = 3;

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaProperties media;
    private final AuditService audit;
    private final Clock clock;

    public WordImageLibrary(NamedParameterJdbcTemplate jdbc, MediaProperties media, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.media = media;
        this.audit = audit;
        this.clock = clock;
    }

    /** Words of one level, those still without a picture first. */
    @Transactional(readOnly = true)
    public List<WordImage> words(int level) {
        return jdbc.query("""
                SELECT id, text, pinyin, meaning_vi, extra->>'image_url' AS image_url
                FROM learning.lexemes
                WHERE kind = 'WORD' AND hsk_level = :level
                ORDER BY (extra->>'image_url') IS NOT NULL, frequency DESC, id
                """, new MapSqlParameterSource("level", level), (rs, row) -> new WordImage(rs.getLong("id"),
                rs.getString("text"), rs.getString("pinyin"), rs.getString("meaning_vi"), rs.getString("image_url")));
    }

    public UploadSignature signUpload() {
        requireMedia();
        long timestamp = clock.instant().getEpochSecond();
        String folder = media.getWordImageFolder();
        return new UploadSignature(media.getCloudName(), media.getApiKey(), folder, timestamp,
                media.signature(folder, timestamp), media.uploadUrl());
    }

    @Transactional
    public WordImage attach(long actorId, long lexemeId, String imageUrl, String publicId) {
        requireMedia();
        if (imageUrl == null || publicId == null || imageUrl.length() > 2_000 || publicId.length() > 255
                || !imageUrl.startsWith(media.deliveryPrefix()) || !publicId.startsWith(media.getWordImageFolder() + "/")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                    Map.of("details", List.of(Map.of("field", "imageUrl", "message", "Ảnh phải được tải lên thư mục ảnh từ vựng."))));
        }
        int updated = jdbc.update("""
                UPDATE learning.lexemes SET extra = jsonb_set(extra, '{image_url}', to_jsonb(CAST(:url AS text)))
                WHERE id = :id AND kind = 'WORD' AND hsk_level BETWEEN 1 AND :max
                """, new MapSqlParameterSource("url", imageUrl).addValue("id", lexemeId).addValue("max", MAX_LEVEL));
        if (updated == 0) {
            throw new ApiException(ErrorCode.LEXEME_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy từ.", Map.of());
        }
        audit.recordDetail(actorId, "WORD_IMAGE_SET", "LEXEME", lexemeId, Map.of("publicId", publicId));
        return find(lexemeId);
    }

    @Transactional
    public void detach(long actorId, long lexemeId) {
        int updated = jdbc.update("UPDATE learning.lexemes SET extra = extra - 'image_url' WHERE id = :id AND kind = 'WORD'",
                new MapSqlParameterSource("id", lexemeId));
        if (updated == 0) {
            throw new ApiException(ErrorCode.LEXEME_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy từ.", Map.of());
        }
        audit.recordDetail(actorId, "WORD_IMAGE_REMOVED", "LEXEME", lexemeId, Map.of());
    }

    private WordImage find(long lexemeId) {
        return jdbc.queryForObject("""
                SELECT id, text, pinyin, meaning_vi, extra->>'image_url' AS image_url FROM learning.lexemes WHERE id = :id
                """, new MapSqlParameterSource("id", lexemeId), (rs, row) -> new WordImage(rs.getLong("id"),
                rs.getString("text"), rs.getString("pinyin"), rs.getString("meaning_vi"), rs.getString("image_url")));
    }

    private void requireMedia() {
        if (!media.configured()) {
            throw new ApiException(ErrorCode.MEDIA_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa bật tải ảnh lên Cloudinary.", Map.of());
        }
    }

    @Schema(name = "WordImage", requiredProperties = {"lexemeId", "text", "meaningVi"})
    public record WordImage(long lexemeId, String text, String pinyin, String meaningVi,
            @Schema(types = {"string", "null"}) String imageUrl) {}

    @Schema(name = "WordImageUploadSignature")
    public record UploadSignature(String cloudName, String apiKey, String folder, long timestamp, String signature,
            String uploadUrl) {}
}
