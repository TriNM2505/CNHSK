package com.cnhsk.learning.credit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.credit.web.LedgerPage;
import com.cnhsk.learning.credit.web.LedgerRow;
import com.cnhsk.learning.credit.web.LedgerTotals;
import com.cnhsk.learning.credit.web.Transaction.TransactionType;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Audited, materialized ledger projections in one writable snapshot. */
@Service
public class LedgerQueryService {
    private static final int EXPORT_LIMIT = 10_000;
    private static final String FILTER = " FROM learning.credit_transactions"
            + " WHERE created_at >= :from AND created_at < :to";
    private static final String COLUMNS = "SELECT id, user_id, seq, tx_type, amount, balance_before,"
            + " balance_after, feature_code, reason, created_at";
    private static final String ORDER = " ORDER BY created_at DESC, id DESC";

    private final AuthApi auth;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final LedgerCsvWriter csv;

    public LedgerQueryService(AuthApi auth, NamedParameterJdbcTemplate jdbc, AuditService audit,
            LedgerCsvWriter csv) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.audit = audit;
        this.csv = csv;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = false)
    public LedgerPage list(LedgerFilter filter, int page, int size) {
        long actor = requireAdmin(auth);
        if (filter == null || page < 0 || size < 1 || size > 100) throw LedgerFilter.invalid();
        requireWritableSnapshot(jdbc);
        var parameters = parameters(filter).addValue("size", size).addValue("offset", (long) page * size);
        LedgerTotals totals = jdbc.queryForObject("""
                SELECT count(*) AS count,
                    coalesce(sum(amount) FILTER (WHERE amount > 0), 0) AS credited,
                    coalesce(sum(amount) FILTER (WHERE amount < 0), 0) AS debited
                """ + where(filter), parameters, (rs, row) -> new LedgerTotals(rs.getLong("count"),
                        rs.getLong("credited"), rs.getLong("debited")));
        List<LedgerRow> items = project(fetch(where(filter) + ORDER + " LIMIT :size OFFSET :offset", parameters));
        if (totals == null) throw new IllegalStateException("Ledger totals are unavailable.");
        LedgerPage result = new LedgerPage(items, page, size, totals.count(), totals);
        Map<String, Object> detail = dateDetail(filter);
        detail.put("type", filter.type() == null ? null : filter.type().name());
        audit.recordDetail(actor, "LEDGER_READ", "CREDIT_LEDGER", detail);
        return result;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = false)
    public byte[] export(LedgerFilter filter) {
        long actor = requireAdmin(auth);
        if (filter == null) throw LedgerFilter.invalid();
        requireWritableSnapshot(jdbc);
        List<Projection> records = fetch(where(filter) + ORDER + " LIMIT :limit",
                parameters(filter).addValue("limit", EXPORT_LIMIT + 1));
        if (records.size() > EXPORT_LIMIT) {
            throw new ApiException(ErrorCode.EXPORT_TOO_LARGE, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Ledger export exceeds 10000 rows.", Map.of());
        }
        byte[] result = csv.write(project(records));
        Map<String, Object> detail = dateDetail(filter);
        detail.put("rows", records.size());
        audit.recordDetail(actor, "LEDGER_EXPORT", "CREDIT_LEDGER", detail);
        return result;
    }

    private List<Projection> fetch(String clause, MapSqlParameterSource parameters) {
        return jdbc.query(COLUMNS + clause, parameters, (rs, row) -> new Projection(rs.getLong("id"),
                rs.getLong("user_id"), rs.getInt("seq"), TransactionType.valueOf(rs.getString("tx_type")),
                rs.getInt("amount"), rs.getInt("balance_before"), rs.getInt("balance_after"),
                rs.getString("feature_code"), rs.getString("reason"), rs.getTimestamp("created_at").toInstant()));
    }

    private List<LedgerRow> project(List<Projection> rows) {
        Set<Long> ids = rows.stream().map(Projection::userId).collect(Collectors.toUnmodifiableSet());
        Map<Long, String> emails = ids.isEmpty() ? Map.of() : auth.maskedEmails(ids);
        return rows.stream().map(row -> new LedgerRow(row.id(), row.userId(), maskedEmail(emails, row.userId()),
                row.seq(), row.type(), row.amount(), row.balanceBefore(), row.balanceAfter(), row.featureCode(),
                row.reason(), row.createdAt())).toList();
    }

    private static String where(LedgerFilter filter) {
        return FILTER + (filter.type() == null ? "" : " AND tx_type = :type");
    }

    private static MapSqlParameterSource parameters(LedgerFilter filter) {
        var parameters = new MapSqlParameterSource("from", Timestamp.from(filter.fromInclusive()))
                .addValue("to", Timestamp.from(filter.toExclusive()));
        if (filter.type() != null) parameters.addValue("type", filter.type().name());
        return parameters;
    }

    private static Map<String, Object> dateDetail(LedgerFilter filter) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", filter.from().toString());
        detail.put("to", filter.to().toString());
        return detail;
    }

    static long requireAdmin(AuthApi auth) {
        var current = auth.currentUser();
        if (!current.roles().contains(RoleCode.ADMIN)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this operation.", Map.of());
        }
        return current.userId();
    }

    static void requireWritableSnapshot(NamedParameterJdbcTemplate jdbc) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Ledger audit requires a writable transaction.");
        }
        if (!"off".equals(jdbc.getJdbcTemplate().queryForObject(
                "SELECT current_setting('transaction_read_only')", String.class))) {
            throw new IllegalStateException("Ledger audit requires a writable transaction.");
        }
    }

    static String maskedEmail(Map<Long, String> emails, long userId) {
        String email = emails == null ? null : emails.get(userId);
        if (email == null || email.isBlank()) throw new IllegalStateException("Masked ledger identity is unavailable.");
        return email;
    }

    private record Projection(long id, long userId, int seq, TransactionType type, int amount, int balanceBefore,
            int balanceAfter, String featureCode, String reason, Instant createdAt) {}
}
