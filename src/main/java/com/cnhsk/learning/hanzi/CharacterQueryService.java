package com.cnhsk.learning.hanzi;

import java.util.List;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.text.PinyinUtils;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterQueryService {
    private final CharacterQueryRepository characters;

    public CharacterQueryService(CharacterQueryRepository characters) {
        this.characters = characters;
    }

    @Transactional(readOnly = true)
    public CharacterPage list(Long hskLevel, String q, long page, long size) {
        if (hskLevel != null && (hskLevel < 1 || hskLevel > 9) || page < 0 || size < 1 || size > 50
                || page > Integer.MAX_VALUE / size || q != null && (q.isBlank() || q.length() > 20)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tham số không hợp lệ.", java.util.Map.of());
        }
        String normalized = null;
        if (q != null) {
            try {
                normalized = PinyinUtils.normalize(q);
            } catch (IllegalArgumentException ignored) {
                // Exact Hanzi searches do not need pinyin normalization.
            }
        }
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        var result = characters.search(LexemeKind.CHAR, hskLevel == null ? null : hskLevel.shortValue(), q,
                normalized, PageRequest.of(Math.toIntExact(page), Math.toIntExact(size)));
        return new CharacterPage(result.getContent().stream().map(CharacterSummary::from).toList(),
                Math.toIntExact(page), Math.toIntExact(size), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CharacterDetail detail(long userId, long id) {
        if (id <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tham số không hợp lệ.", java.util.Map.of("field", "id"));
        }
        Lexeme lexeme = characters.findCharacter(id);
        if (lexeme == null) {
            throw new ApiException(ErrorCode.CHARACTER_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy chữ.", java.util.Map.of());
        }
        return new CharacterDetail(lexeme.getId(), lexeme.getText(), lexeme.getPinyin(), lexeme.getMeaningVi(),
                lexeme.getHskLevel(), lexeme.getStrokeCount(), lexeme.getAudioUrl() != null,
                lexeme.getSinoViet(), lexeme.getRadical(), lexeme.getAudioUrl(),
                new Writing(lexeme.getStrokeCount() != null, characters.recallUnlocked(userId, id),
                        lexeme.getStrokeCount() != null && lexeme.getAudioUrl() != null,
                        characters.lastPracticedAt(userId, id)));
    }

    public record CharacterSummary(long id, String text, String pinyin, String meaning, Short hskLevel,
            Short strokeCount, boolean hasAudio) {
        static CharacterSummary from(Lexeme x) {
            return new CharacterSummary(x.getId(), x.getText(), x.getPinyin(), x.getMeaningVi(), x.getHskLevel(),
                    x.getStrokeCount(), x.getAudioUrl() != null);
        }
    }
    public record CharacterPage(List<CharacterSummary> items, int page, int size, long totalElements) { }
    public record CharacterDetail(long id, String text, String pinyin, String meaning, Short hskLevel,
            Short strokeCount, boolean hasAudio, String sinoViet, String radical, String audioUrl, Writing writing) { }
    public record Writing(boolean strokeAvailable, boolean recallUnlocked, boolean dictationAvailable,
            java.time.Instant lastPracticedAt) { }
}
