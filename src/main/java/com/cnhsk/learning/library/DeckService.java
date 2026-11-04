package com.cnhsk.learning.library;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.library.web.DeckWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DeckService {
    private final AuthApi auth;
    private final DeckRepository decks;
    private final LibraryConfig config;
    private final Clock clock;

    public DeckService(AuthApi auth, DeckRepository decks, LibraryConfig config, Clock clock) {
        this.auth = auth;
        this.decks = decks;
        this.config = config;
        this.clock = clock;
    }

    public List<DeckSummary> list() {
        return decks.list(auth.currentUser().userId(), clock.instant());
    }

    public DeckDetail detail(String rawId) {
        return decks.detail(auth.currentUser().userId(), id(rawId), clock.instant()).orElseThrow(DeckService::notOwned);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeckDetail create(DeckWrite request) {
        long userId = auth.currentUser().userId();
        decks.lock(userId);
        Instant now = clock.instant();
        validate(request);
        if (request.topicId() != null && !decks.topicExists(request.topicId())) {
            throw new ApiException(ErrorCode.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND, "Không tìm thấy chủ đề.", Map.of());
        }
        if (decks.count(userId) >= config.getMaxDecks()) {
            throw new ApiException(ErrorCode.LIBRARY_LIMIT_REACHED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Đã đạt giới hạn bộ thẻ.", Map.of("details", Map.of("kind", "DECKS", "limit", config.getMaxDecks())));
        }
        long deckId = decks.insert(userId, request, now);
        if (request.topicId() != null) decks.copyTopic(deckId, request.topicId(), config.getMaxCards(), now);
        return decks.detail(userId, deckId, now).orElseThrow(DeckService::notOwned);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DeckSummary update(String rawId, DeckWrite request) {
        long userId = auth.currentUser().userId();
        decks.lock(userId);
        Instant now = clock.instant();
        long deckId = id(rawId);
        if (!decks.lockOwned(userId, deckId)) throw notOwned();
        validate(request);
        decks.update(userId, deckId, request, now);
        return decks.summary(userId, deckId, now).orElseThrow(DeckService::notOwned);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(String rawId) {
        long userId = auth.currentUser().userId();
        decks.lock(userId);
        Instant now = clock.instant();
        long deckId = id(rawId);
        if (!decks.lockOwned(userId, deckId)) throw notOwned();
        decks.softDelete(userId, deckId, now);
    }

    private static void validate(DeckWrite request) {
        String name = request.name();
        if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 100 || name.indexOf('\0') >= 0) {
            throw DictionaryService.invalid("name", "Tên bộ thẻ cần 1–100 ký tự.");
        }
        String description = request.description();
        if (description != null && (description.codePointCount(0, description.length()) > 500 || description.indexOf('\0') >= 0)) {
            throw DictionaryService.invalid("description", "Mô tả tối đa 500 ký tự.");
        }
    }

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
                "Bạn không có quyền truy cập bộ thẻ này.", Map.of());
    }

    @Schema(name = "DeckSummary")
    public record DeckSummary(long id, String name, String description, long cardCount, long dueCount, Instant createdAt) { }

    @Schema(name = "DeckDetail")
    public record DeckDetail(long id, String name, String description, long cardCount, long dueCount, Instant createdAt,
                             List<Card> cards) { }

    @Schema(name = "Card")
    public record Card(long id, Long lexemeId, String front, String back, Instant dueAt, int reviewCount, int lapseCount) { }
}
