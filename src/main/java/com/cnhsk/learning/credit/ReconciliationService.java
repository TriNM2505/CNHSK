package com.cnhsk.learning.credit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.credit.web.Reconciliation;
import com.cnhsk.learning.credit.web.ReconciliationIssue;
import com.cnhsk.learning.credit.web.ReconciliationIssue.Kind;
import com.cnhsk.shared.audit.AuditService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Checks global ledger invariants without changing balances or acquiring row locks. */
@Service
public class ReconciliationService {
    private static final int MASK_CHUNK = 1_000;
    private static final String SQL = """
            WITH ordered AS (
                SELECT user_id, seq, amount, balance_before, balance_after,
                    lag(seq) OVER ledger_order AS previous_seq,
                    lag(balance_after) OVER ledger_order AS previous_balance,
                    lead(seq) OVER ledger_order AS next_seq
                FROM learning.credit_transactions
                WINDOW ledger_order AS (PARTITION BY user_id ORDER BY seq)
            ), summary AS (
                SELECT count(*) FILTER (WHERE next_seq IS NULL) AS users, count(*) AS rows,
                    coalesce(sum(balance_after) FILTER (WHERE next_seq IS NULL), 0) AS total_balance,
                    coalesce(sum(amount), 0) AS total_amount
                FROM ordered
            ), issues AS (
                SELECT user_id, seq, rule.kind, rule.kind_order
                FROM ordered
                CROSS JOIN LATERAL (VALUES
                    ('SEQ_GAP', 0, (previous_seq IS NULL AND seq <> 1)
                        OR (previous_seq IS NOT NULL AND seq::bigint <> previous_seq::bigint + 1)),
                    ('BALANCE_CHAIN_BROKEN', 1, previous_seq IS NOT NULL AND balance_before <> previous_balance),
                    ('FIRST_BALANCE_NOT_ZERO', 2, previous_seq IS NULL AND balance_before <> 0)
                ) AS rule(kind, kind_order, violated)
                WHERE rule.violated
            )
            SELECT summary.users, summary.rows, summary.total_balance, summary.total_amount,
                issues.user_id, issues.seq, issues.kind
            FROM summary LEFT JOIN issues ON true
            ORDER BY issues.user_id ASC, issues.seq ASC, issues.kind_order ASC
            """;

    private final AuthApi auth;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public ReconciliationService(AuthApi auth, NamedParameterJdbcTemplate jdbc, AuditService audit) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = false)
    public Reconciliation reconcile() {
        long actor = LedgerQueryService.requireAdmin(auth);
        LedgerQueryService.requireWritableSnapshot(jdbc);
        Snapshot snapshot = jdbc.query(SQL, Map.of(), rs -> {
            if (!rs.next()) throw new IllegalStateException("Ledger reconciliation summary is unavailable.");
            long users = rs.getLong("users");
            long rows = rs.getLong("rows");
            long totalBalance = rs.getLong("total_balance");
            long totalAmount = rs.getLong("total_amount");
            List<Issue> issues = new ArrayList<>();
            do {
                String kind = rs.getString("kind");
                if (kind != null) issues.add(new Issue(rs.getLong("user_id"), rs.getInt("seq"), Kind.valueOf(kind)));
            } while (rs.next());
            return new Snapshot(users, rows, totalBalance, totalAmount, List.copyOf(issues));
        });
        if (snapshot == null) throw new IllegalStateException("Ledger reconciliation summary is unavailable.");
        Map<Long, String> emails = maskedEmails(snapshot.issues());
        List<ReconciliationIssue> issues = snapshot.issues().stream()
                .map(issue -> new ReconciliationIssue(issue.userId(),
                        LedgerQueryService.maskedEmail(emails, issue.userId()), issue.seq(), issue.kind())).toList();
        Reconciliation result = new Reconciliation(snapshot.users(), snapshot.rows(), snapshot.totalBalance(),
                snapshot.totalAmount(), issues);
        audit.recordDetail(actor, "LEDGER_RECONCILE", "CREDIT_LEDGER", Map.of(
                "users", result.users(), "rows", result.rows(), "totalBalance", result.totalBalance(),
                "totalAmount", result.totalAmount(), "issueCount", result.issues().size()));
        return result;
    }

    private Map<Long, String> maskedEmails(List<Issue> issues) {
        List<Long> ids = issues.stream().map(Issue::userId).distinct().toList();
        Map<Long, String> emails = new HashMap<>();
        for (int start = 0; start < ids.size(); start += MASK_CHUNK) {
            Set<Long> chunk = ids.subList(start, Math.min(ids.size(), start + MASK_CHUNK)).stream()
                    .collect(Collectors.toUnmodifiableSet());
            Map<Long, String> masked = auth.maskedEmails(chunk);
            for (long id : chunk) emails.put(id, LedgerQueryService.maskedEmail(masked, id));
        }
        return emails;
    }

    private record Issue(long userId, int seq, Kind kind) {}

    private record Snapshot(long users, long rows, long totalBalance, long totalAmount, List<Issue> issues) {}
}
