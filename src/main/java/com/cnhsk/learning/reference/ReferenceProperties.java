package com.cnhsk.learning.reference;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.reference")
public class ReferenceProperties {

    private static final Pattern DOMAIN = Pattern.compile(
            "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+");

    private Map<ReferenceItem.Kind, List<String>> allowedDomains = new EnumMap<>(ReferenceItem.Kind.class);
    private List<String> thumbnailDomains = List.of();
    /** Sites a learner may read on directly (book previews, free official documents); empty turns reading links off. */
    private List<String> previewDomains = List.of();

    public Map<ReferenceItem.Kind, List<String>> getAllowedDomains() {
        return allowedDomains;
    }

    public void setAllowedDomains(Map<ReferenceItem.Kind, List<String>> allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    public List<String> getThumbnailDomains() {
        return thumbnailDomains;
    }

    public void setThumbnailDomains(List<String> thumbnailDomains) {
        this.thumbnailDomains = thumbnailDomains;
    }

    public List<String> getPreviewDomains() {
        return previewDomains;
    }

    public void setPreviewDomains(List<String> previewDomains) {
        this.previewDomains = previewDomains;
    }

    @AssertTrue(message = "Reference domains must contain every kind and nonempty domain-only lists")
    public boolean isDomainConfigurationValid() {
        if (allowedDomains == null || !validDomains(thumbnailDomains) || previewDomains == null
                || (!previewDomains.isEmpty() && !validDomains(previewDomains))) {
            return false;
        }
        for (ReferenceItem.Kind kind : ReferenceItem.Kind.values()) {
            if (!validDomains(allowedDomains.get(kind))) {
                return false;
            }
        }
        return true;
    }

    private boolean validDomains(List<String> domains) {
        return domains != null && !domains.isEmpty()
                && domains.stream().allMatch(domain -> domain != null && DOMAIN.matcher(domain).matches());
    }
}
