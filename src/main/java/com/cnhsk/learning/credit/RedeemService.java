package com.cnhsk.learning.credit;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.credit.web.CreateBatchRequest.CardType;
import com.cnhsk.learning.credit.web.PlanStatus;
import com.cnhsk.learning.credit.web.RedeemRequest;
import com.cnhsk.learning.credit.web.RedeemResult;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RedeemService {
    private static final Logger LOG = LoggerFactory.getLogger(RedeemService.class);
    private final AuthApi auth;
    private final NamedParameterJdbcTemplate jdbc;
    private final LedgerService ledger;
    private final CardCodes codes;
    private final RedeemRateLimitService attempts;
    private final Clock clock;

    public RedeemService(AuthApi auth, NamedParameterJdbcTemplate jdbc, LedgerService ledger,
            CardCodes codes, RedeemRateLimitService attempts, Clock clock) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.codes = codes;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RedeemResult redeem(RedeemRequest request, String remoteAddr) {
        if (request == null) throw RedeemRequest.invalid();
        request.validate();
        var current = auth.currentUser();
        if (!current.roles().contains(RoleCode.LEARNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        long user = current.userId();
        attempts.check(user, remoteAddr);
        var normalized = codes.normalize(request.code());
        if (normalized.isEmpty()) throw invalid(user, remoteAddr);
        try {
            PlanState state = auth.lockPlan(user);
            Instant now = clock.instant();
            String hash = codes.hash(normalized.orElseThrow());
            List<Card> claimed = jdbc.query("""
                    UPDATE learning.credit_cards SET status='USED', used_by=:user, used_at=:now
                    WHERE code_hash=:hash AND status='UNUSED'
                      AND (expires_at IS NULL OR expires_at > :now) AND created_by <> :user
                    RETURNING id,card_type,value,plan_days
                    """, new MapSqlParameterSource("user", user).addValue("now", Timestamp.from(now))
                            .addValue("hash", hash),
                    (rs, row) -> new Card(rs.getLong("id"), CardType.valueOf(rs.getString("card_type")),
                            rs.getInt("value"), rs.getObject("plan_days", Integer.class)));
            if (claimed.isEmpty()) throw invalid(user, remoteAddr);
            Card card = claimed.get(0);
            boolean premium = "PREMIUM".equals(state.planCode()) && state.planExpiresAt() != null
                    && state.planExpiresAt().isAfter(now);
            String planCode = premium ? "PREMIUM" : "FREE";
            Instant expires = premium ? state.planExpiresAt() : null;
            int amount;
            String reason = null;
            PlanInfo plan;
            if (card.type() == CardType.SUBSCRIPTION) {
                plan = plan("PREMIUM");
                expires = (premium ? state.planExpiresAt() : now).plus(card.days(), ChronoUnit.DAYS);
                planCode = "PREMIUM";
                auth.updatePlan(user, planCode, expires);
                amount = 0;
                reason = "PREMIUM +" + card.days() + " ngày, giá niêm yết " + plan.price().toPlainString();
            } else {
                plan = plan(planCode);
                amount = card.value();
            }
            LedgerService.LedgerEntry entry = ledger.append(new LedgerService.AppendCommand(user,
                    card.type() == CardType.CREDIT ? "TOPUP" : "SUBSCRIPTION", amount,
                    null, card.id(), null, null, reason, null, null));
            return new RedeemResult(card.type(), amount, entry.balanceAfter(),
                    new PlanStatus(PlanStatus.PlanCode.valueOf(planCode), plan.name(), expires, true));
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOG.warn("Card redemption failed; cause_type={}", exception.getClass().getSimpleName());
            throw internal();
        }
    }

    private PlanInfo plan(String code) {
        List<PlanInfo> found = jdbc.query("SELECT name,price FROM learning.plans WHERE code=:code",
                Map.of("code", code), (rs, row) -> new PlanInfo(rs.getString("name"), rs.getBigDecimal("price")));
        if (found.isEmpty()) throw internal();
        return found.get(0);
    }

    private ApiException invalid(long user, String ip) {
        attempts.recordFailure(user, ip);
        return new ApiException(ErrorCode.INVALID_CARD_CODE, HttpStatus.BAD_REQUEST,
                "Mã thẻ không hợp lệ hoặc không thể sử dụng.", Map.of());
    }

    private static ApiException internal() {
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi nội bộ.", Map.of());
    }

    private record Card(long id, CardType type, int value, Integer days) { }
    private record PlanInfo(String name, BigDecimal price) { }
}
