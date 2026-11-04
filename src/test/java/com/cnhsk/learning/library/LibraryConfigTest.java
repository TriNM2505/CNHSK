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
                "cnhsk.library.authenticated-lookups-per-hour=600").run(context -> {
                    assertThat(context).hasNotFailed();
                    LibraryConfig config = context.getBean(LibraryConfig.class);
                    assertThat(config.getGuestLookupsPerHour()).isEqualTo(60);
                    assertThat(config.getAuthenticatedLookupsPerHour()).isEqualTo(600);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"guest-lookups-per-hour", "authenticated-lookups-per-hour"})
    void FR002_missingEitherLimitPreventsStartup(String missing) {
        String present = missing.equals("guest-lookups-per-hour")
                ? "authenticated-lookups-per-hour" : "guest-lookups-per-hour";
        runner.withPropertyValues("cnhsk.library." + present + "=60")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "2147483648"})
    void FR002_invalidLimitsPreventStartup(String invalid) {
        for (String field : new String[] {"guest-lookups-per-hour", "authenticated-lookups-per-hour"}) {
            runner.withPropertyValues("cnhsk.library.guest-lookups-per-hour=60",
                    "cnhsk.library.authenticated-lookups-per-hour=600", "cnhsk.library." + field + "=" + invalid)
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
