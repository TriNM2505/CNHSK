package com.cnhsk.learning.ai;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.ai")
public class AiConfig {
    /** Which client calls the model; a typo must stop startup instead of silently disabling AI. */
    @NotBlank
    @Pattern(regexp = "deepseek|anthropic")
    private String provider = "deepseek";

    @NotBlank
    private String assistantModel;

    @NotBlank
    private String translateModel;

    @NotNull
    private Duration timeout;

    @NotNull
    @Min(1)
    private Long monthlyTokenBudget;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getAssistantModel() { return assistantModel; }
    public void setAssistantModel(String assistantModel) { this.assistantModel = assistantModel; }
    public String getTranslateModel() { return translateModel; }
    public void setTranslateModel(String translateModel) { this.translateModel = translateModel; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }
    public Long getMonthlyTokenBudget() { return monthlyTokenBudget; }
    public void setMonthlyTokenBudget(Long monthlyTokenBudget) { this.monthlyTokenBudget = monthlyTokenBudget; }

    @AssertTrue(message = "AI timeout must be positive")
    public boolean isTimeoutValid() {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }
}
