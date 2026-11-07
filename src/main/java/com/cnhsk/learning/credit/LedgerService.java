package com.cnhsk.learning.credit;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.auth.api.AuthApi;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The only writer of the append-only credit ledger. */
@Service
public class LedgerService {

    private static final String INVALID_ARGUMENT_MESSAGE = "Tham số không hợp lệ.";
    private static final String LAST_ENTRY_SQL = """
            SELECT seq, balance_after FROM learning.credit_transactions
            WHERE user_id = :user ORDER BY seq DESC LIMIT 1
            """;

    private final AuthApi authApi;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public LedgerService(AuthApi authApi, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.authApi = Objects.requireNonNull(authApi, "authApi must not be null.");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    /** Joins the caller's transaction and locks its user before reading the ledger. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerEntry append(AppendCommand command) {
        if (command == null || command.userId() <= 0 || command.txType() == null || command.txType().isBlank()) {
            throw new IllegalArgumentException(INVALID_ARGUMENT_MESSAGE);
        }
        authApi.lockPlan(command.userId());
        Balance last = lastBalance(command.userId());
        int seq = Math.incrementExact(last.seq());
        int after = Math.addExact(last.amount(), command.amount());
        return jdbc.queryForObject("""
                INSERT INTO learning.credit_transactions
                    (user_id, seq, tx_type, amount, balance_before, balance_after, feature_code,
                     card_id, request_id, ref_transaction_id, reason, performed_by, dispute_ref, created_at)
                VALUES (:user, :seq, :type, :amount, :before, :after, :feature,
                        :card, :request, :reference, :reason, :performer, :dispute, :created)
                RETURNING id, seq, balance_before, balance_after
                """, new MapSqlParameterSource("user", command.userId()).addValue("seq", seq)
                        .addValue("type", command.txType()).addValue("amount", command.amount())
                        .addValue("before", last.amount()).addValue("after", after)
                        .addValue("feature", command.featureCode()).addValue("card", command.cardId())
                        .addValue("request", command.requestId()).addValue("reference", command.refTransactionId())
                        .addValue("reason", command.reason()).addValue("performer", command.performedBy())
                        .addValue("dispute", command.disputeRef()).addValue("created", Timestamp.from(clock.instant())),
                (rs, row) -> new LedgerEntry(rs.getLong("id"), rs.getInt("seq"),
                        rs.getInt("balance_before"), rs.getInt("balance_after")));
    }

    @Transactional(readOnly = true)
    public int balance(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException(INVALID_ARGUMENT_MESSAGE);
        }
        return lastBalance(userId).amount();
    }

    private Balance lastBalance(long userId) {
        return jdbc.query(LAST_ENTRY_SQL, Map.of("user", userId),
                (rs, row) -> new Balance(rs.getInt("seq"), rs.getInt("balance_after")))
                .stream().findFirst().orElse(new Balance(0, 0));
    }

    public record AppendCommand(long userId, String txType, int amount, String featureCode,
            Long cardId, Long requestId, Long refTransactionId, String reason, Long performedBy,
            String disputeRef) {}

    public record LedgerEntry(long id, int seq, int balanceBefore, int balanceAfter) {}

    private record Balance(int seq, int amount) {}
}
