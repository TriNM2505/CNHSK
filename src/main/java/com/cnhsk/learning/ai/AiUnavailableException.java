package com.cnhsk.learning.ai;

import java.util.Objects;

public final class AiUnavailableException extends RuntimeException {
    public enum Reason { NOT_CONFIGURED, TIMEOUT, UPSTREAM_ERROR, BUDGET_EXCEEDED }

    private final Reason reason;

    public AiUnavailableException(Reason reason) {
        super("AI service is temporarily unavailable");
        this.reason = Objects.requireNonNull(reason, "Reason is required");
    }

    public Reason getReason() {
        return reason;
    }
}
