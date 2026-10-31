package com.cnhsk.community.moderation;

import java.time.Instant;

import com.cnhsk.community.common.ReportReason;
import com.fasterxml.jackson.annotation.JsonFormat;

public record ReportEntry(
        Long userId,
        ReportReason reason,
        String detail,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant reportedAt) {
}
