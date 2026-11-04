package com.cnhsk.learning.library;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.library.web.NoteWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotebookService {

    private final AuthApi auth;
    private final NotebookRepository notes;
    private final LibraryConfig config;

    public NotebookService(AuthApi auth, NotebookRepository notes, LibraryConfig config) {
        this.auth = auth;
        this.notes = notes;
        this.config = config;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Note create(NoteWrite request) {
        long userId = auth.currentUser().userId();
        notes.lock(userId);
        checkLexeme(userId, request.lexemeId(), 0);
        if (notes.count(userId) >= config.getMaxNotes()) {
            throw new ApiException(ErrorCode.LIBRARY_LIMIT_REACHED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Sổ tay đã đạt giới hạn ghi chú.", Map.of("details", Map.of("kind", "NOTES", "limit", config.getMaxNotes())));
        }
        return notes.insert(notes.notebook(userId), request);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Note update(String id, NoteWrite request) {
        long userId = auth.currentUser().userId();
        notes.lock(userId);
        long noteId = id(id);
        owned(userId, noteId);
        checkLexeme(userId, request.lexemeId(), noteId);
        return notes.update(userId, noteId, request);
    }

    public Note get(String id) { return owned(auth.currentUser().userId(), id(id)); }

    public NotePage list(String q, String tag, int page, int size) {
        if (page < 0) throw DictionaryService.invalid("page", "Trang phải từ 0 trở lên.");
        if (size < 1 || size > 50) throw DictionaryService.invalid("size", "Kích thước trang phải từ 1 đến 50.");
        String query = q == null ? null : java.text.Normalizer.normalize(q.strip(), java.text.Normalizer.Form.NFC);
        if (query != null && (query.codePointCount(0, query.length()) < 2 || query.codePointCount(0, query.length()) > 100
                || query.codePoints().anyMatch(Character::isISOControl))) {
            throw DictionaryService.invalid("q", "Từ khoá phải từ 2 đến 100 ký tự.");
        }
        if (tag != null && (tag.codePointCount(0, tag.length()) > 50 || tag.indexOf('\0') >= 0)) {
            throw DictionaryService.invalid("tag", "Thẻ dài quá 50 ký tự.");
        }
        var result = notes.search(auth.currentUser().userId(), query, tag, page, size);
        return new NotePage(result.items().stream().map(note -> new NoteListItem(note.id(), note.title(),
                snippet(note.content(), query), note.tags(), note.lexemeId(), note.sourceType(), note.updatedAt())).toList(), result.total());
    }

    private void checkLexeme(long userId, Long lexemeId, long excludedId) {
        if (lexemeId == null) return;
        if (!notes.lexemeExists(lexemeId)) throw new ApiException(ErrorCode.LEXEME_NOT_FOUND,
                HttpStatus.NOT_FOUND, "Không tìm thấy mục từ.", Map.of());
        notes.duplicate(userId, lexemeId, excludedId).ifPresent(noteId -> {
            throw new ApiException(ErrorCode.NOTE_DUPLICATE, HttpStatus.CONFLICT,
                    "Từ này đã có trong sổ tay.", Map.of("details", Map.of("noteId", noteId)));
        });
    }

    private Note owned(long userId, long id) { return notes.find(userId, id).orElseThrow(NotebookService::notOwned); }

    private static long id(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(value);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) { throw notOwned(); }
    }

    private static ApiException notOwned() {
        return new ApiException(ErrorCode.COLLECTION_NOT_OWNED, HttpStatus.FORBIDDEN,
                "Bạn không có quyền truy cập ghi chú này.", Map.of());
    }

    private static String snippet(String content, String query) {
        int match = query == null ? 0 : Math.max(0, content.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT)));
        int start = content.offsetByCodePoints(0, Math.max(0, content.codePointCount(0, Math.min(match, content.length())) - 60));
        int end = content.offsetByCodePoints(start, Math.min(200, content.codePointCount(start, content.length())));
        return content.substring(start, end);
    }

    @Schema(name = "Note")
    public record Note(long id, String title, String content, List<String> tags, Long lexemeId, String sourceType,
                       Instant createdAt, Instant updatedAt) { }

    @Schema(name = "NoteListItem")
    public record NoteListItem(long id, String title, String snippet, List<String> tags, Long lexemeId,
                               String sourceType, Instant updatedAt) { }

    @Schema(name = "NotePage")
    public record NotePage(List<NoteListItem> items, long total) { }
}
