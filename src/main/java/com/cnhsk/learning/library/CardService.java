package com.cnhsk.learning.library;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.library.CardRepository.ActiveCard;
import com.cnhsk.learning.library.DeckService.Card;
import com.cnhsk.learning.library.web.CardWrite;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CardService {
    private final AuthApi auth;
    private final DeckRepository decks;
    private final CardRepository cards;
    private final LibraryConfig config;
    private final Clock clock;

    public CardService(AuthApi auth, DeckRepository decks, CardRepository cards, LibraryConfig config, Clock clock) {
        this.auth = auth;
        this.decks = decks;
        this.cards = cards;
        this.config = config;
        this.clock = clock;
    }

    public Card create(String rawDeckId, CardWrite request) {
        long userId = auth.currentUser().userId();
        decks.lock(userId);
        Instant now = clock.instant();
        long deckId = ownedDeck(userId, rawDeckId);
        if (request.hasLexemeId() && (request.hasFront() || request.hasBack())) {
            throw DictionaryService.invalid("request", "Chọn mục từ hoặc nhập hai mặt thẻ, không gửi cả hai dạng.");
        }
        Long lexemeId = null;
        String front;
        String back;
        if (request.hasLexemeId()) {
            lexemeId = request.lexemeId();
            var lexeme = cards.lexeme(lexemeId).orElseThrow(() -> new ApiException(ErrorCode.LEXEME_NOT_FOUND,
                    HttpStatus.NOT_FOUND, "Không tìm thấy mục từ.", Map.of()));
            front = lexeme.front();
            back = lexeme.back();
        } else {
            front = text(request.front(), "front", 200);
            back = text(request.back(), "back", 2000);
        }
        List<ActiveCard> active = cards.active(deckId);
        checkDuplicate(active, null, lexemeId, front);
        if (active.size() >= config.getMaxCards()) {
            throw new ApiException(ErrorCode.LIBRARY_LIMIT_REACHED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Đã đạt giới hạn thẻ trong bộ.", Map.of("details", Map.of("kind", "CARDS", "limit", config.getMaxCards())));
        }
        long cardId = cards.insert(deckId, lexemeId, front, back, now).orElseThrow(CardService::duplicate);
        return cards.get(userId, deckId, cardId);
    }

    public Card update(String rawDeckId, String rawCardId, CardWrite request) {
        long userId = auth.currentUser().userId();
        decks.lock(userId);
        Instant now = clock.instant();
        long deckId = ownedDeck(userId, rawDeckId);
        long cardId = ownedCard(deckId, rawCardId);
        if (!request.hasFront() && !request.hasBack()) {
            throw DictionaryService.invalid("request", "Cần gửi ít nhất một mặt thẻ để sửa.");
        }
        Card current = cards.get(userId, deckId, cardId);
        String front = request.hasFront() ? text(request.front(), "front", 200) : current.front();
        String back = request.hasBack() ? text(request.back(), "back", 2000) : current.back();
        checkDuplicate(cards.active(deckId), cardId, current.lexemeId(), front);
        cards.update(deckId, cardId, front, back, now);
        return cards.get(userId, deckId, cardId);
    }

    public void delete(String rawDeckId, String rawCardId) {
        long userId = auth.currentUser().userId();
        decks.lock(userId);
        Instant now = clock.instant();
        long deckId = ownedDeck(userId, rawDeckId);
        cards.delete(deckId, ownedCard(deckId, rawCardId), now);
    }

    private long ownedDeck(long userId, String rawId) {
        long id = id(rawId);
        if (!decks.lockOwned(userId, id)) throw notOwned();
        return id;
    }

    private long ownedCard(long deckId, String rawId) {
        long id = id(rawId);
        if (!cards.lockOwned(deckId, id)) throw notOwned();
        return id;
    }

    private static void checkDuplicate(List<ActiveCard> active, Long self, Long lexemeId, String front) {
        String normalized = whitespace(front);
        for (ActiveCard card : active) {
            if (self != null && card.id() == self) continue;
            if ((lexemeId != null && lexemeId.equals(card.lexemeId())) || normalized.equals(whitespace(card.front()))) throw duplicate();
        }
    }

    private static String text(String value, String field, int limit) {
        if (whitespace(value).isEmpty() || value.codePointCount(0, value.length()) > limit || value.indexOf('\0') >= 0) {
            throw DictionaryService.invalid(field, "Mặt thẻ cần 1–" + limit + " ký tự, không được chỉ có khoảng trắng.");
        }
        return value;
    }

    /** Only whitespace changes for comparison: no case folding, accent removal or Unicode composition. */
    private static String whitespace(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        boolean pendingSpace = false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) result.append(' ');
                result.appendCodePoint(cp);
                pendingSpace = false;
            }
        }
        return result.toString();
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
                "Bạn không có quyền truy cập bộ hoặc thẻ này.", Map.of());
    }

    private static ApiException duplicate() {
        return new ApiException(ErrorCode.CARD_DUPLICATE, HttpStatus.CONFLICT, "Thẻ này đã có trong bộ.", Map.of());
    }
}
