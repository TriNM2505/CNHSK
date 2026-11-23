package com.cnhsk.community.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cloudinary account used for community images and videos; empty values switch attachments off. */
@Component
@ConfigurationProperties(prefix = "cnhsk.media")
public class MediaProperties {
    private String cloudName = "";
    private String apiKey = "";
    private String apiSecret = "";
    private String folder = "cnhsk/community";

    public boolean configured() {
        return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    /** Every delivered file of this account starts with this prefix. */
    public String deliveryPrefix() {
        return "https://res.cloudinary.com/" + cloudName + "/";
    }

    public String getCloudName() { return cloudName; }
    public void setCloudName(String cloudName) { this.cloudName = cloudName == null ? "" : cloudName.strip(); }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey.strip(); }
    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret == null ? "" : apiSecret.strip(); }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }
}
