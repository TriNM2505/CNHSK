package com.cnhsk.community.media.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Everything a browser needs for one signed upload; valid for about an hour. */
@Schema(name = "UploadSignature", requiredProperties = {"cloudName", "apiKey", "folder", "timestamp", "signature",
        "uploadUrl"})
public record UploadSignature(String cloudName, String apiKey, String folder, long timestamp, String signature,
        String uploadUrl) {
}
