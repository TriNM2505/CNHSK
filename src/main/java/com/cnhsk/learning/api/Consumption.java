package com.cnhsk.learning.api;

import java.time.Instant;

/** Server-issued receipt retained by a caller for compensation after provider failure. */
public record Consumption(Source source, Long transactionId, PaidFeature feature,
        String idempotencyKey, Instant periodStart) {

    public enum Source { QUOTA, CREDIT }

    @Override
    public String toString() {
        return "Consumption[source=" + source + ", feature=" + feature + ", idempotencyKey=[REDACTED]]";
    }
}
