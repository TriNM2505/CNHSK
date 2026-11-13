package com.cnhsk.learning.reference;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class ReferenceUrlPolicy {

    private final ReferenceProperties properties;

    public ReferenceUrlPolicy(ReferenceProperties properties) {
        this.properties = properties;
    }

    public Optional<Violation> validate(ReferenceItem.Kind kind, String url, String thumbnailUrl) {
        Optional<Violation> violation = validateUrl("url", url, properties.getAllowedDomains().get(kind));
        if (violation.isPresent() || thumbnailUrl == null) {
            return violation;
        }
        return validateUrl("thumbnailUrl", thumbnailUrl, properties.getThumbnailDomains());
    }

    private Optional<Violation> validateUrl(String field, String value, List<String> domains) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            return Optional.of(new Violation(field, Reason.MALFORMED_URL));
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            return Optional.of(new Violation(field, Reason.MALFORMED_URL));
        }
        if (!value.startsWith("https://")) {
            return Optional.of(new Violation(field, Reason.NON_HTTPS_LINK));
        }
        if (uri.getRawUserInfo() != null || uri.getHost() == null || uri.getHost().isEmpty()) {
            return Optional.of(new Violation(field, Reason.MALFORMED_URL));
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        String normalizedHost = host;
        boolean allowed = domains != null && domains.stream()
                .anyMatch(domain -> normalizedHost.equals(domain) || normalizedHost.endsWith("." + domain));
        return allowed ? Optional.empty() : Optional.of(new Violation(field, Reason.DOMAIN_NOT_ALLOWED));
    }

    public record Violation(String field, Reason reason) {
    }

    public enum Reason {
        MALFORMED_URL, NON_HTTPS_LINK, DOMAIN_NOT_ALLOWED
    }
}
