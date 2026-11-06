package com.cnhsk.learning.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LibraryConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(LibraryConfig.class);

    @Test
    void FR002_bindsBothRequiredLookupLimits() {
        runner.withPropertyValues("cnhsk.library.guest-lookups-per-hour=60",
                "cnhsk.library.authenticated-lookups-per-hour=600", "cnhsk.library.max-notes=1000",
                "cnhsk.library.max-decks=50", "cnhsk.library.max-cards=500",
                "cnhsk.library.flashcard-reviews-per-hour=500").run(context -> {
                    assertThat(context).hasNotFailed();
                    LibraryConfig config = context.getBean(LibraryConfig.class);
                    assertThat(config.getGuestLookupsPerHour()).isEqualTo(60);
                    assertThat(config.getAuthenticatedLookupsPerHour()).isEqualTo(600);
                    assertThat(config.getMaxNotes()).isEqualTo(1000);
                    assertThat(config.getMaxDecks()).isEqualTo(50);
                    assertThat(config.getMaxCards()).isEqualTo(500);
                    assertThat(config.getFlashcardReviewsPerHour()).isEqualTo(500);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"guest-lookups-per-hour", "authenticated-lookups-per-hour", "max-notes", "max-decks", "max-cards", "flashcard-reviews-per-hour"})
    void FR002_missingEitherLimitPreventsStartup(String missing) {
        var configured = runner;
        for (String field : new String[] {"guest-lookups-per-hour", "authenticated-lookups-per-hour", "max-notes", "max-decks", "max-cards", "flashcard-reviews-per-hour"}) {
            if (!field.equals(missing)) configured = configured.withPropertyValues("cnhsk.library." + field + "=1000");
        }
        configured.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "2147483648"})
    void FR002_invalidLimitsPreventStartup(String invalid) {
        for (String field : new String[] {"guest-lookups-per-hour", "authenticated-lookups-per-hour", "max-notes", "max-decks", "max-cards", "flashcard-reviews-per-hour"}) {
            runner.withPropertyValues("cnhsk.library.guest-lookups-per-hour=60",
                    "cnhsk.library.authenticated-lookups-per-hour=600", "cnhsk.library.max-notes=1000",
                    "cnhsk.library.max-decks=50", "cnhsk.library.max-cards=500",
                    "cnhsk.library.flashcard-reviews-per-hour=500",
                    "cnhsk.library." + field + "=" + invalid)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
