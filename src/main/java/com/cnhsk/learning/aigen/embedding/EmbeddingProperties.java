package com.cnhsk.learning.aigen.embedding;

import java.nio.file.Path;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Where the sentence model lives and how its downloaded files are verified. */
@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.embedding")
public class EmbeddingProperties {
    private static final String SHA256 = "[0-9a-f]{64}";

    @NotBlank private String modelId;
    @NotNull private Path cacheDir;
    @NotBlank private String modelUrl;
    @NotBlank private String tokenizerUrl;
    @NotBlank @Pattern(regexp = SHA256) private String modelSha256;
    @NotBlank @Pattern(regexp = SHA256) private String tokenizerSha256;
    @Min(16) @Max(512) private int maxTokens = 128;

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public Path getCacheDir() { return cacheDir; }
    public void setCacheDir(Path cacheDir) { this.cacheDir = cacheDir; }
    public String getModelUrl() { return modelUrl; }
    public void setModelUrl(String modelUrl) { this.modelUrl = modelUrl; }
    public String getTokenizerUrl() { return tokenizerUrl; }
    public void setTokenizerUrl(String tokenizerUrl) { this.tokenizerUrl = tokenizerUrl; }
    public String getModelSha256() { return modelSha256; }
    public void setModelSha256(String modelSha256) { this.modelSha256 = modelSha256; }
    public String getTokenizerSha256() { return tokenizerSha256; }
    public void setTokenizerSha256(String tokenizerSha256) { this.tokenizerSha256 = tokenizerSha256; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
