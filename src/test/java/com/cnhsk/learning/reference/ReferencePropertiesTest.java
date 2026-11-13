package com.cnhsk.learning.reference;

import java.util.List;
import java.util.Map;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ReferencePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(ReferenceProperties.class, ReferenceUrlPolicy.class);

    private ApplicationContextRunner configuredRunner() {
        return runner.withInitializer(context -> {
            try {
                new YamlPropertySourceLoader().load("reference-test", new ClassPathResource("application.yaml"))
                        .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Cannot load configuration", exception);
            }
        });
    }

    @Test
    void FR015_applicationYamlBindsEverySpecifiedDomainAndRegistersPolicy() {
        configuredRunner().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ReferenceProperties.class)
                    .hasSingleBean(ReferenceUrlPolicy.class);
            ReferenceProperties properties = context.getBean(ReferenceProperties.class);
            assertThat(properties.getAllowedDomains()).containsOnlyKeys(ReferenceItem.Kind.values());
            assertThat(properties.getAllowedDomains().get(ReferenceItem.Kind.YOUTUBE_CHANNEL))
                    .containsExactly("youtube.com", "youtu.be");
            assertThat(properties.getAllowedDomains().get(ReferenceItem.Kind.PODCAST))
                    .containsExactly("open.spotify.com", "podcasts.apple.com", "soundcloud.com", "ximalaya.com",
                            "podcasts.google.com");
            assertThat(properties.getAllowedDomains().get(ReferenceItem.Kind.BOOK))
                    .containsExactly("tiki.vn", "shopee.vn", "fahasa.com", "amazon.com", "books.google.com", "nxbgd.vn");
            assertThat(properties.getThumbnailDomains())
                    .containsExactly("i.ytimg.com", "yt3.ggpht.com", "salt.tikicdn.com", "m.media-amazon.com");
        });
    }

    @Test
    void FR015_missingKindAndEmptyListsPreventStartup() {
        runner.withPropertyValues("cnhsk.reference.allowed-domains.YOUTUBE_CHANNEL[0]=youtube.com",
                "cnhsk.reference.allowed-domains.PODCAST[0]=open.spotify.com",
                "cnhsk.reference.thumbnail-domains[0]=i.ytimg.com")
                .run(context -> assertThat(context).hasFailed());
        configuredRunner().withPropertyValues("cnhsk.reference.allowed-domains.BOOK=")
                .run(context -> assertThat(context).hasFailed());
        configuredRunner().withPropertyValues("cnhsk.reference.thumbnail-domains=")
                .run(context -> assertThat(context).hasFailed());
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://youtube.com", "youtube.com/path", "", " ", ".youtube.com", "youtube.com."})
    void FR015_invalidDomainConfigurationPreventsStartup(String domain) {
        configuredRunner().withPropertyValues("cnhsk.reference.allowed-domains.YOUTUBE_CHANNEL[0]=" + domain)
                .run(context -> assertThat(context).hasFailed());
        configuredRunner().withPropertyValues("cnhsk.reference.thumbnail-domains[0]=" + domain)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void FR015_nullConfigurationAndNullEntriesFailValidation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            ReferenceProperties properties = new ReferenceProperties();
            properties.setAllowedDomains(null);
            assertThat(factory.getValidator().validate(properties)).isNotEmpty();
            properties.setAllowedDomains(Map.of(ReferenceItem.Kind.BOOK, List.of("fahasa.com")));
            properties.setThumbnailDomains(null);
            assertThat(factory.getValidator().validate(properties)).isNotEmpty();
            properties.setThumbnailDomains(java.util.Arrays.asList((String) null));
            assertThat(factory.getValidator().validate(properties)).isNotEmpty();
        }
    }
}
