package com.cnhsk.learning.aigen;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiGenerationJob {
    private final AiGenerationService generation;
    public AiGenerationJob(AiGenerationService generation) { this.generation = generation; }

    @Scheduled(cron = "${cnhsk.ai-generate.cron}", zone = "Asia/Ho_Chi_Minh")
    public void generate() { generation.runOnce(); }
}
