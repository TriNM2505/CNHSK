package com.cnhsk.learning.reference;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything a browser needs for one signed cover upload; valid for about an hour. Same shape as the community
 * upload signature, but owned here because learning code must not depend on the community module.
 */
@Schema(name = "ReferenceCoverSignature", requiredProperties = {"cloudName", "apiKey", "folder", "timestamp",
        "signature", "uploadUrl"})
public record ReferenceCoverSignature(String cloudName, String apiKey, String folder, long timestamp,
        String signature, String uploadUrl) {
}