package com.cnhsk.learning.aigen;

import java.time.Duration;

import com.cnhsk.shared.settings.SettingKey;
import com.cnhsk.shared.settings.SettingsApi;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.ai-generate")
public class AiGenerateConfig {
    private final SettingsApi settings;

    @NotBlank
    private final String generateModel;

    @NotBlank
    private String cron;

    @NotNull
    private Duration timeout;

    public AiGenerateConfig(SettingsApi settings, @Value("${cnhsk.ai.generate-model}") String generateModel) {
        this.settings = settings;
        this.generateModel = generateModel;
    }

    public int systemUserId() { return settings.getInt(SettingKey.AI_GENERATE_SYSTEM_USER_ID); }
    public int windowDays() { return settings.getInt(SettingKey.AI_GENERATE_WINDOW_DAYS); }
    public int minWrong() { return settings.getInt(SettingKey.AI_GENERATE_MIN_WRONG); }
    public int minBank() { return settings.getInt(SettingKey.AI_GENERATE_MIN_BANK); }
    public int batchSize() { return settings.getInt(SettingKey.AI_GENERATE_BATCH_SIZE); }
    public int maxBatchesPerRun() { return settings.getInt(SettingKey.AI_GENERATE_MAX_BATCHES_PER_RUN); }

    public String getGenerateModel() { return generateModel; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    @AssertTrue(message = "Generation cron must be a six-field Spring expression or disabled")
    public boolean isCronValid() {
        return cron != null && (Scheduled.CRON_DISABLED.equals(cron)
                || (cron.trim().split("\\s+").length == 6 && CronExpression.isValidExpression(cron)));
    }

    @AssertTrue(message = "Generation timeout must be positive")
    public boolean isTimeoutValid() {
        return timeout != null && !timeout.isZero() && !timeout.isNegative();
    }
}
