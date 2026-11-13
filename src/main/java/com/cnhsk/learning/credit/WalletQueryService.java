package com.cnhsk.learning.credit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.learning.credit.web.PlanStatus;
import com.cnhsk.learning.credit.web.PlanStatus.PlanCode;
import com.cnhsk.learning.credit.web.QuotaLine;
import com.cnhsk.learning.credit.web.Transaction;
import com.cnhsk.learning.credit.web.Transaction.TransactionType;
import com.cnhsk.learning.credit.web.TransactionPage;
import com.cnhsk.learning.credit.web.Wallet;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Principal-bound projections; never performs lazy quota resets or ledger writes. */
@Service
public class WalletQueryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Keep public explanations while withholding card material and sensitive legacy free text.
    private static final Pattern SENSITIVE_REASON = Pattern.compile(
            "(?i)[a-f0-9]{64}"
            + "|(?:[0-9a-hjkmnp-tv-zilo][\\s-]*){16}"
            + "|[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"
            + "|(?<![0-9])(?:\\+84|0)[0-9 .-]{8,14}[0-9](?![0-9])"
            + "|eyJ[a-z0-9_-]+\\.[a-z0-9_-]+\\.[a-z0-9_-]+"
            + "|(?:password|secret|token|api[_ -]?key)\\s*[:=]");

    private final AuthApi auth;
    private final LedgerService ledger;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final BillingConfig config;
    private final Clock clock;

    public WalletQueryService(AuthApi auth, LedgerService ledger, NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper, BillingConfig config, Clock clock) {
        this.auth = auth;
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Wallet wallet() {
        long userId = auth.currentUser().userId();
        PlanState state = requirePlan(userId);
        Instant now = clock.instant();
        boolean premium = "PREMIUM".equals(state.planCode()) && state.planExpiresAt() != null
                && state.planExpiresAt().isAfter(now);
        PlanCode code = premium ? PlanCode.PREMIUM : PlanCode.FREE;
        int balance = ledger.balance(userId);
        Catalog catalog = jdbc.query("SELECT name, benefits::text FROM learning.plans WHERE code = :code",
                Map.of("code", code.name()), (rs, row) -> new Catalog(rs.getString("name"), rs.getString("benefits")))
                .stream().findFirst().orElseThrow(WalletQueryService::invalidQuota);
        JsonNode benefits = premium ? benefits(catalog.benefits()) : null;
        Instant month = now.atZone(ZONE).toLocalDate().withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
        Instant resetAt = now.atZone(ZONE).toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(ZONE).toInstant();
        var quota = Arrays.stream(PaidFeature.values()).map(feature -> {
            int limit = premium ? limit(benefits, feature) : config.getFreeMonthlyLimit();
            int used = state.usageResetAt().isBefore(month) ? 0 : state.freeUsage().getOrDefault(feature.name(), 0);
            if (used < 0 || limit < 0) throw invalidQuota();
            return new QuotaLine(feature, limit, used, Math.max(0, limit - used));
        }).toList();
        return new Wallet(balance, new PlanStatus(code, catalog.name(), premium ? state.planExpiresAt() : null, true), quota, resetAt);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TransactionPage history(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalidPagination();
        long userId = auth.currentUser().userId();
        requirePlan(userId);
        var parameters = new MapSqlParameterSource("user", userId)
                .addValue("size", size).addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions WHERE user_id = :user", parameters, Long.class);
        var items = jdbc.query("""
                SELECT seq, tx_type, amount, balance_before, balance_after, feature_code, reason, created_at
                FROM learning.credit_transactions WHERE user_id = :user
                ORDER BY seq DESC LIMIT :size OFFSET :offset
                """, parameters, (rs, row) -> new Transaction(rs.getInt("seq"), TransactionType.valueOf(rs.getString("tx_type")),
                        rs.getInt("amount"), rs.getInt("balance_before"), rs.getInt("balance_after"),
                        rs.getString("feature_code") == null ? null : PaidFeature.valueOf(rs.getString("feature_code")),
                        safeReason(rs.getString("reason")), rs.getTimestamp("created_at").toInstant()));
        return new TransactionPage(items, page, size, total);
    }

    private PlanState requirePlan(long userId) {
        return auth.readPlan(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED,
                HttpStatus.UNAUTHORIZED, "Không thể xác thực người dùng hiện tại.", Map.of()));
    }

    private JsonNode benefits(String value) {
        try {
            JsonNode parsed = mapper.readTree(value);
            if (parsed == null || !parsed.isObject()) throw invalidQuota();
            return parsed;
        } catch (JsonProcessingException exception) {
            throw invalidQuota();
        }
    }

    private static int limit(JsonNode benefits, PaidFeature feature) {
        JsonNode value = benefits.get(feature.name());
        if (value == null) return 0;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) throw invalidQuota();
        return value.intValue();
    }

    private static String safeReason(String reason) {
        return reason == null || !SENSITIVE_REASON.matcher(reason).find() ? reason : null;
    }

    public static ApiException invalidPagination() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Tham số phân trang không hợp lệ.", Map.of());
    }

    private static IllegalStateException invalidQuota() {
        return new IllegalStateException("Dữ liệu hạn mức không hợp lệ.");
    }

    private record Catalog(String name, String benefits) {}
}
