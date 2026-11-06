package com.cnhsk.learning.library;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.library")
public class LibraryConfig {

    @NotNull
    @Min(1)
    private Integer guestLookupsPerHour;

    @NotNull
    @Min(1)
    private Integer authenticatedLookupsPerHour;

    @NotNull
    @Min(1)
    private Integer maxNotes;

    @NotNull
    @Min(1)
    private Integer maxDecks;

    @NotNull
    @Min(1)
    private Integer maxCards;

    @NotNull
    @Min(1)
    private Integer flashcardReviewsPerHour;

    public Integer getFlashcardReviewsPerHour() { return flashcardReviewsPerHour; }

    public void setFlashcardReviewsPerHour(Integer value) { flashcardReviewsPerHour = value; }

    public Integer getMaxDecks() { return maxDecks; }

    public void setMaxDecks(Integer maxDecks) { this.maxDecks = maxDecks; }

    public Integer getMaxCards() { return maxCards; }

    public void setMaxCards(Integer maxCards) { this.maxCards = maxCards; }

    public Integer getMaxNotes() {
        return maxNotes;
    }

    public void setMaxNotes(Integer maxNotes) {
        this.maxNotes = maxNotes;
    }

    public Integer getGuestLookupsPerHour() {
        return guestLookupsPerHour;
    }

    public void setGuestLookupsPerHour(Integer guestLookupsPerHour) {
        this.guestLookupsPerHour = guestLookupsPerHour;
    }

    public Integer getAuthenticatedLookupsPerHour() {
        return authenticatedLookupsPerHour;
    }

    public void setAuthenticatedLookupsPerHour(Integer authenticatedLookupsPerHour) {
        this.authenticatedLookupsPerHour = authenticatedLookupsPerHour;
    }
}
