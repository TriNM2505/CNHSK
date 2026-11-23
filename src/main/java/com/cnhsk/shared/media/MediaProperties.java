package com.cnhsk.shared.media;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cloudinary account shared by community attachments and learners' speaking recordings; empty values switch
 * uploads off. Browsers upload directly with a signature from here, so the secret never leaves the server.
 */
@Component
@ConfigurationProperties(prefix = "cnhsk.media")
public class MediaProperties {
    private String cloudName = "";
    private String apiKey = "";
    private String apiSecret = "";
    private String folder = "cnhsk/community";
    private String speakingFolder = "cnhsk/speaking";

    public boolean configured() {
        return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    /** Every delivered file of this account starts with this prefix. */
    public String deliveryPrefix() {
        return "https://res.cloudinary.com/" + cloudName + "/";
    }

    /** Endpoint that accepts images, videos and audio alike. */
    public String uploadUrl() {
        return "https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload";
    }

    /** Cloudinary signs the alphabetically sorted parameters joined with '&', followed by the secret. */
    public String signature(String uploadFolder, long timestamp) {
        String toSign = "folder=" + uploadFolder + "&timestamp=" + timestamp;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest((toSign + apiSecret).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public String getCloudName() { return cloudName; }
    public void setCloudName(String cloudName) { this.cloudName = cloudName == null ? "" : cloudName.strip(); }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey.strip(); }
    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret == null ? "" : apiSecret.strip(); }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }
    public String getSpeakingFolder() { return speakingFolder; }
    public void setSpeakingFolder(String speakingFolder) { this.speakingFolder = speakingFolder; }
}
