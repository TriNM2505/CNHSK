package com.cnhsk.learning.library;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.library.DeckService.Card;
import com.cnhsk.learning.review.FsrsConfig;
import com.cnhsk.learning.review.FsrsScheduler;
import com.cnhsk.learning.review.FsrsScheduler.Rating;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlashcardReviewService {
    private final AuthApi auth;
    private final DeckRepository decks;
    private final FlashcardReviewRepository reviews;
    private final FsrsScheduler scheduler;
    private final Clock clock;

    public FlashcardReviewService(AuthApi auth, DeckRepository decks, FlashcardReviewRepository reviews,
                                  FsrsConfig config, ObjectProvider<FsrsScheduler> schedulers, Clock clock) {
        this.auth = auth;
        this.decks = decks;
        this.reviews = reviews;
        this.scheduler = schedulers.getIfAvailable(() -> new FsrsScheduler(config.settings()));
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Card> due(String rawDeckId, String rawAhead) {
        long userId = auth.currentUser().userId();
        long deckId = id(rawDeckId);
        boolean ahead = ahead(rawAhead);
        Instant now = clock.instant();
        if (!reviews.ownsDeck(userId, deckId)) throw notOwned();
        return reviews.due(userId, deckId, now, ahead);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResult review(String rawCardId, Rating rating) {
        long userId = auth.currentUser().userId();
        decks.lock(userId);
        long cardId = id(rawCardId);
        if (!reviews.lockCard(userId, cardId)) throw notOwned();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean first = reviews.insertFirst(userId, cardId, now);
        var memory = reviews.lockMemory(userId, cardId);
        var result = first ? scheduler.first(rating, now) : scheduler.review(memory, rating, now);
        Instant nextReview = reviews.update(userId, cardId, now, result);
        return new ReviewResult(cardId, nextReview, result.intervalDays());
    }

    private static boolean ahead(String value) {
        if (value == null || value.equals("false")) return false;
        if (value.equals("true")) return true;
        throw DictionaryService.invalid("ahead", "Tham số ahead phải là true hoặc false.");
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

    @Schema(name = "ReviewResult")
    public record ReviewResult(long cardId, Instant nextReviewAt, int intervalDays) { }
}
