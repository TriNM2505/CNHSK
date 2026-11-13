package com.cnhsk.learning.reference;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceUrlPolicyTest {

    private final ReferenceUrlPolicy policy = new ReferenceUrlPolicy(properties());

    private static ReferenceProperties properties() {
        ReferenceProperties properties = new ReferenceProperties();
        properties.setAllowedDomains(Map.of(
                ReferenceItem.Kind.YOUTUBE_CHANNEL, List.of("youtube.com", "youtu.be"),
                ReferenceItem.Kind.PODCAST, List.of("open.spotify.com"),
                ReferenceItem.Kind.BOOK, List.of("fahasa.com")));
        properties.setThumbnailDomains(List.of("i.ytimg.com"));
        return properties;
    }

    @ParameterizedTest
    @CsvSource({
            "http://youtube.com, NON_HTTPS_LINK",
            "javascript:alert(1), NON_HTTPS_LINK",
            "'data:text/plain;base64,YQ==', NON_HTTPS_LINK",
            "HTTPS://youtube.com/x, NON_HTTPS_LINK",
            "https://evil.com/youtube.com, DOMAIN_NOT_ALLOWED",
            "https://youtube.com@evil.com/x, MALFORMED_URL",
            "https://user:pass@youtube.com/x, MALFORMED_URL",
            "https://user@youtube.com/x, MALFORMED_URL",
            "https://@youtube.com/x, MALFORMED_URL",
            "https:///x, MALFORMED_URL",
            "https://:443/x, MALFORMED_URL",
            "https://youtube.com./x, DOMAIN_NOT_ALLOWED",
            "https://notyoutube.com, DOMAIN_NOT_ALLOWED",
            "https://youtube.com.evil.com, DOMAIN_NOT_ALLOWED",
            "https://youtube.com/%zz, MALFORMED_URL",
            "https://you tube.com/x, MALFORMED_URL"
    })
    void AT05_AT06_AT07_FR015_invalidUrlsReturnTheSpecifiedReason(String url, ReferenceUrlPolicy.Reason reason) {
        assertThat(policy.validate(ReferenceItem.Kind.YOUTUBE_CHANNEL, url, null))
                .contains(new ReferenceUrlPolicy.Violation("url", reason));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://m.youtube.com/@kenh", "https://WWW.YouTube.com/@kenh",
            "https://youtube.com/watch?v=demo#section", "https://youtu.be/demo"})
    void AT08_FR015_exactHostsAndBoundarySubdomainsAreAllowed(String url) {
        assertThat(policy.validate(ReferenceItem.Kind.YOUTUBE_CHANNEL, url, null)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " https://youtube.com", "https://youtube.com "})
    void FR015_missingBlankAndUntrimmedUrlsAreMalformed(String url) {
        assertThat(policy.validate(ReferenceItem.Kind.YOUTUBE_CHANNEL, url, null))
                .contains(new ReferenceUrlPolicy.Violation("url", ReferenceUrlPolicy.Reason.MALFORMED_URL));
    }

    @Test
    void FR015_eachKindHasItsOwnAllowlist() {
        assertThat(policy.validate(ReferenceItem.Kind.PODCAST, "https://open.spotify.com/show/demo", null)).isEmpty();
        assertThat(policy.validate(ReferenceItem.Kind.BOOK, "https://fahasa.com/demo", null)).isEmpty();
        assertThat(policy.validate(ReferenceItem.Kind.BOOK, "https://youtube.com/demo", null))
                .contains(new ReferenceUrlPolicy.Violation("url", ReferenceUrlPolicy.Reason.DOMAIN_NOT_ALLOWED));
    }

    @Test
    void FR015_optionalThumbnailUsesItsOwnDomainsAndUrlFailsFirst() {
        assertThat(policy.validate(ReferenceItem.Kind.YOUTUBE_CHANNEL, "https://youtube.com", null)).isEmpty();
        assertThat(policy.validate(ReferenceItem.Kind.YOUTUBE_CHANNEL, "https://youtube.com",
                "https://i.ytimg.com/demo.jpg")).isEmpty();
        assertThat(policy.validate(ReferenceItem.Kind.YOUTUBE_CHANNEL, "https://youtube.com",
                "https://youtube.com/demo.jpg"))
                .contains(new ReferenceUrlPolicy.Violation("thumbnailUrl", ReferenceUrlPolicy.Reason.DOMAIN_NOT_ALLOWED));
        assertThat(policy.validate(ReferenceItem.Kind.YOUTUBE_CHANNEL, "http://youtube.com", ""))
                .contains(new ReferenceUrlPolicy.Violation("url", ReferenceUrlPolicy.Reason.NON_HTTPS_LINK));
    }

    @ParameterizedTest
    @CsvSource({"http://i.ytimg.com/x, NON_HTTPS_LINK", "https://user@i.ytimg.com/x, MALFORMED_URL",
            "https://evil.com/i.ytimg.com, DOMAIN_NOT_ALLOWED", "https:///x, MALFORMED_URL"})
    void FR015_thumbnailAppliesTheSameValidationOrder(String thumbnail, ReferenceUrlPolicy.Reason reason) {
        assertThat(policy.validate(ReferenceItem.Kind.BOOK, "https://fahasa.com/demo", thumbnail))
                .contains(new ReferenceUrlPolicy.Violation("thumbnailUrl", reason));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " https://i.ytimg.com/x", "https://i.ytimg.com/x "})
    void FR015_blankAndUntrimmedThumbnailsAreMalformed(String thumbnail) {
        assertThat(policy.validate(ReferenceItem.Kind.BOOK, "https://fahasa.com/demo", thumbnail))
                .contains(new ReferenceUrlPolicy.Violation("thumbnailUrl", ReferenceUrlPolicy.Reason.MALFORMED_URL));
    }
}
