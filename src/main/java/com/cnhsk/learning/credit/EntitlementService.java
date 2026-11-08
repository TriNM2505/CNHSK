package com.cnhsk.learning.credit;

import java.time.Clock;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.learning.api.Consumption;
import com.cnhsk.learning.api.Consumption.Source;
import com.cnhsk.learning.api.EntitlementApi;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.LedgerService.AppendCommand;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Serializes quota and wallet changes using the authentication-owned user row. */
@Service
public class EntitlementService implements EntitlementApi {

    private static final Logger log = LoggerFactory.getLogger(EntitlementService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String INVALID_ARGUMENT = "Tham số không hợp lệ.";

    private final AuthApi auth;
    private final LedgerService ledger;
    private final NamedParameterJdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final BillingConfig config;
    private final Clock clock;

    public EntitlementService(AuthApi auth, LedgerService ledger, NamedParameterJdbcTemplate jdbc,
            StringRedisTemplate redis, ObjectMapper mapper, BillingConfig config, Clock clock) {
        this.auth = auth;
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.redis = redis;
        this.mapper = mapper;
        this.config = config;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Consumption consume(long userId, PaidFeature feature, String idempotencyKey) {
        requireWritableTransaction();
        requireValid(userId > 0 && feature != null && validKey(idempotencyKey));
        String key = "billing:idem:" + userId + ":" + idempotencyKey;
        Consumption cached = readReceipt(key, feature, idempotencyKey);
        if (cached != null) {
            return cached;
        }
        PlanState state = auth.lockPlan(userId);
        cached = readReceipt(key, feature, idempotencyKey);
        if (cached != null) {
            return cached;
        }
        PendingReceipts pending = pendingReceipts();
        cached = pending.receipts.get(key);
        if (cached != null) {
            requireValid(cached.feature() == feature);
            return cached;
        }
        Instant now = clock.instant();
        Instant period = periodStart(now);
        boolean reset = state.usageResetAt().isBefore(period);
        Map<String, Integer> usage = reset ? new HashMap<>() : new HashMap<>(state.freeUsage());
        int used = usage.getOrDefault(feature.name(), 0);
        int limit = quotaLimit(state, feature, now);
        if (used < 0 || limit < 0) {
            throw invalidStoredQuota();
        }
        Consumption result;
        if (used < limit) {
            usage.put(feature.name(), Math.incrementExact(used));
            auth.updateUsage(userId, usage, reset ? period : state.usageResetAt());
            result = new Consumption(Source.QUOTA, null, feature, idempotencyKey, period);
        } else {
            if (ledger.balance(userId) < 1) {
                throw new ApiException(ErrorCode.INSUFFICIENT_CREDITS, HttpStatus.PAYMENT_REQUIRED,
                        "Đã hết hạn mức và điểm.", Map.of());
            }
            if (reset) {
                auth.updateUsage(userId, usage, period);
            }
            long id = ledger.append(new AppendCommand(userId, "DEDUCT", -1, feature.name(),
                    null, null, null, null, null, null)).id();
            result = new Consumption(Source.CREDIT, id, feature, idempotencyKey, period);
        }
        pending.receipts.put(key, result);
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(long userId, Consumption consumption) {
        requireWritableTransaction();
        requireValid(userId > 0 && validReceipt(consumption));
        PlanState state = auth.lockPlan(userId);
        if (consumption.source() == Source.CREDIT) {
            refundCredit(userId, consumption.transactionId());
        } else {
            refundQuota(userId, consumption, state);
        }
    }

    private void refundCredit(long userId, long originalId) {
        OriginalDebit original = jdbc.query("""
                SELECT user_id, tx_type, amount, feature_code FROM learning.credit_transactions
                WHERE id = :id
                """, Map.of("id", originalId), (rs, row) -> new OriginalDebit(rs.getLong("user_id"),
                        rs.getString("tx_type"), rs.getInt("amount"), rs.getString("feature_code")))
                .stream().findFirst().orElse(null);
        if (original == null || original.userId() != userId || !"DEDUCT".equals(original.type())
                || original.amount() >= 0) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Không có quyền truy cập giao dịch.", Map.of());
        }
        Boolean refunded = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM learning.credit_transactions
                WHERE tx_type = 'REFUND' AND ref_transaction_id = :id)
                """, Map.of("id", originalId), Boolean.class);
        if (Boolean.TRUE.equals(refunded)) {
            return;
        }
        ledger.append(new AppendCommand(userId, "REFUND", Math.negateExact(original.amount()),
                original.feature(), null, null, originalId, null, null, null));
    }

    private void refundQuota(long userId, Consumption consumption, PlanState state) {
        Instant now = clock.instant();
        Instant period = periodStart(now);
        int used = state.freeUsage().getOrDefault(consumption.feature().name(), 0);
        if (!period.equals(consumption.periodStart()) || state.usageResetAt().isBefore(period) || used <= 0) {
            return;
        }
        String key = "billing:refund:" + userId + ":" + consumption.idempotencyKey();
        Instant nextPeriod = now.atZone(ZONE).withDayOfMonth(1).toLocalDate()
                .plusMonths(1).atStartOfDay(ZONE).toInstant();
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(key, "1", Duration.between(now, nextPeriod));
            if (!Boolean.TRUE.equals(claimed)) {
                if (claimed == null) {
                    log.warn("Quota refund marker unavailable");
                }
                return;
            }
        } catch (DataAccessException exception) {
            log.warn("Quota refund marker unavailable");
            return;
        }
        // Register first so a failed database update also releases the marker on rollback.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        redis.delete(key);
                    } catch (DataAccessException exception) {
                        log.warn("Quota refund marker cleanup unavailable");
                    }
                }
            }
        });
        Map<String, Integer> usage = new HashMap<>(state.freeUsage());
        usage.put(consumption.feature().name(), used - 1);
        auth.updateUsage(userId, usage, state.usageResetAt());
    }

    private int quotaLimit(PlanState state, PaidFeature feature, Instant now) {
        if (!"PREMIUM".equals(state.planCode()) || state.planExpiresAt() == null
                || !state.planExpiresAt().isAfter(now)) {
            return config.getFreeMonthlyLimit();
        }
        var benefits = jdbc.queryForList("SELECT benefits::text FROM learning.plans WHERE code = :code",
                Map.of("code", "PREMIUM"), String.class);
        if (benefits.isEmpty()) {
            return 0;
        }
        try {
            JsonNode root = mapper.readTree(benefits.get(0));
            if (root == null || !root.isObject()) {
                throw invalidStoredQuota();
            }
            JsonNode value = root.get(feature.name());
            if (value == null) {
                return 0;
            }
            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
                throw invalidStoredQuota();
            }
            return value.intValue();
        } catch (JsonProcessingException exception) {
            throw invalidStoredQuota();
        }
    }

    private Consumption readReceipt(String redisKey, PaidFeature feature, String idempotencyKey) {
        Consumption receipt;
        try {
            String json = redis.opsForValue().get(redisKey);
            if (json == null) {
                return null;
            }
            receipt = mapper.readValue(json, Consumption.class);
            if (!validReceipt(receipt) || !idempotencyKey.equals(receipt.idempotencyKey())) {
                log.warn("Billing idempotency receipt unavailable");
                return null;
            }
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("Billing idempotency receipt unavailable");
            return null;
        }
        requireValid(receipt.feature() == feature);
        return receipt;
    }

    private PendingReceipts pendingReceipts() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof PendingReceipts pending && pending.owner == this) {
                return pending;
            }
        }
        PendingReceipts pending = new PendingReceipts(this);
        TransactionSynchronizationManager.registerSynchronization(pending);
        return pending;
    }

    private final class PendingReceipts implements TransactionSynchronization {
        private final EntitlementService owner;
        private final Map<String, Consumption> receipts = new LinkedHashMap<>();

        private PendingReceipts(EntitlementService owner) {
            this.owner = owner;
        }

        @Override
        public void afterCommit() {
            receipts.forEach((key, value) -> {
                try {
                    redis.opsForValue().set(key, mapper.writeValueAsString(value), IDEMPOTENCY_TTL);
                } catch (DataAccessException | JsonProcessingException exception) {
                    log.warn("Billing idempotency publication unavailable");
                }
            });
        }

        @Override
        public void afterCompletion(int status) {
            receipts.clear();
        }
    }

    private static Instant periodStart(Instant now) {
        return now.atZone(ZONE).toLocalDate().withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
    }

    private static boolean validKey(String key) {
        return key != null && !key.isBlank() && key.length() <= 128;
    }

    private static boolean validReceipt(Consumption receipt) {
        try {
            return receipt != null && receipt.source() != null && receipt.feature() != null
                    && validKey(receipt.idempotencyKey()) && receipt.periodStart() != null
                    && receipt.periodStart().equals(periodStart(receipt.periodStart()))
                    && (receipt.source() == Source.QUOTA ? receipt.transactionId() == null
                            : receipt.transactionId() != null && receipt.transactionId() > 0);
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private static void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Cần transaction ghi đang hoạt động.");
        }
    }

    private static void requireValid(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException(INVALID_ARGUMENT);
        }
    }

    private static IllegalStateException invalidStoredQuota() {
        return new IllegalStateException("Dữ liệu hạn mức không hợp lệ.");
    }

    private record OriginalDebit(long userId, String type, int amount, String feature) {}
}
