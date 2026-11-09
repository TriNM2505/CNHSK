package com.cnhsk.learning.credit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.credit.web.BatchPage;
import com.cnhsk.learning.credit.web.BatchSummary;
import com.cnhsk.learning.credit.web.CreateBatchRequest;
import com.cnhsk.learning.credit.web.CreateBatchRequest.CardType;
import com.cnhsk.learning.credit.web.CreateBatchRequest.Channel;
import com.cnhsk.learning.credit.web.CreatedBatch;
import com.cnhsk.learning.credit.web.VoidResult;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardBatchService {
    private static final Logger LOG = LoggerFactory.getLogger(CardBatchService.class);
    private static final int MAX_RETRIES = 5;
    private static final String GROUP_SQL = """
            SELECT batch_id, min(card_type) AS card_type, min(value) AS value,
                   min(plan_days) AS plan_days, min(channel) AS channel,
                   min(partner_code) AS partner_code, min(campaign) AS campaign,
                   min(issued_to) AS issued_to, min(expires_at) AS expires_at,
                   count(*) AS total, count(*) FILTER (WHERE status='UNUSED') AS unused,
                   count(*) FILTER (WHERE status='USED') AS used,
                   count(*) FILTER (WHERE status='VOIDED') AS voided,
                   min(created_at) AS created_at, min(created_by) AS created_by
            FROM learning.credit_cards
            """;

    private final AuthApi auth;
    private final NamedParameterJdbcTemplate jdbc;
    private final CardCodes codes;
    private final Clock clock;
    private final AuditService audit;
    private final Supplier<String> rawGenerator;
    private final Supplier<String> batchGenerator;

    @Autowired
    public CardBatchService(AuthApi auth, NamedParameterJdbcTemplate jdbc, CardCodes codes,
            Clock clock, AuditService audit) {
        this(auth, jdbc, codes, clock, audit, codes::generate, codes::newBatchId);
    }

    CardBatchService(AuthApi auth, NamedParameterJdbcTemplate jdbc, CardCodes codes, Clock clock,
            AuditService audit, Supplier<String> rawGenerator, Supplier<String> batchGenerator) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.codes = codes;
        this.clock = clock;
        this.audit = audit;
        this.rawGenerator = rawGenerator;
        this.batchGenerator = batchGenerator;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreatedBatch create(CreateBatchRequest request) {
        long actor = admin();
        Instant now = clock.instant();
        if (request == null) throw CreateBatchRequest.invalid();
        request.validate(now);
        try {
            if (request.cardType() == CardType.SUBSCRIPTION) {
                List<Boolean> active = jdbc.query("SELECT is_active FROM learning.plans WHERE code='PREMIUM' FOR SHARE",
                        Map.of(), (rs, row) -> rs.getBoolean(1));
                if (active.isEmpty() || !active.get(0)) {
                    throw new ApiException(ErrorCode.PLAN_NOT_AVAILABLE, HttpStatus.UNPROCESSABLE_ENTITY,
                            "Gói PREMIUM hiện không khả dụng.", Map.of());
                }
            }
            String batch = unusedBatchId();
            List<String> formatted = insertCodes(batch, actor, request, now);
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("batchId", batch);
            detail.put("cardType", request.cardType().name());
            detail.put("quantity", request.quantity());
            detail.put("value", request.value());
            detail.put("planDays", request.planDays());
            detail.put("totalValue", request.cardType() == CardType.CREDIT
                    ? request.quantity() * request.value() : null);
            detail.put("channel", request.channel().name());
            audit.recordDetail(actor, "CARD_BATCH_CREATE", "CARD_BATCH", detail);
            return new CreatedBatch(batch, formatted);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Database errors can contain bound hashes; never propagate their cause or message.
            LOG.warn("Card batch operation failed; cause_type={}", exception.getClass().getSimpleName());
            throw internal();
        }
    }

    private String unusedBatchId() {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            String batch = batchGenerator.get();
            Boolean exists = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM learning.credit_cards WHERE batch_id=:batch)",
                    Map.of("batch", batch), Boolean.class);
            if (Boolean.FALSE.equals(exists)) return batch;
        }
        throw internal();
    }

    private List<String> insertCodes(String batch, long actor, CreateBatchRequest request, Instant now) {
        Set<String> seen = new HashSet<>();
        List<String> accepted = new ArrayList<>();
        for (int round = 0; round <= MAX_RETRIES && accepted.size() < request.quantity(); round++) {
            Map<String, String> candidates = new LinkedHashMap<>();
            int missing = request.quantity() - accepted.size();
            for (int index = 0; index < missing; index++) {
                String raw = rawGenerator.get();
                if (seen.add(raw)) candidates.putIfAbsent(codes.hash(raw), raw);
            }
            if (candidates.isEmpty()) continue;
            MapSqlParameterSource parameters = new MapSqlParameterSource("batch", batch)
                    .addValue("type", request.cardType().name())
                    .addValue("value", request.cardType() == CardType.CREDIT ? request.value() : request.planDays())
                    .addValue("days", request.planDays()).addValue("channel", request.channel().name())
                    .addValue("partner", request.partnerCode()).addValue("campaign", request.campaign())
                    .addValue("issued", request.issuedTo()).addValue("expires", timestamp(request.expiresAt()))
                    .addValue("actor", actor).addValue("created", Timestamp.from(now));
            List<String> tuples = new ArrayList<>();
            int index = 0;
            for (String hash : candidates.keySet()) {
                String parameter = "hash" + index++;
                parameters.addValue(parameter, hash);
                tuples.add("(:batch,:" + parameter + ",:type,:value,:days,'UNUSED',:channel,"
                        + ":partner,:campaign,:issued,:expires,:actor,:created)");
            }
            Set<String> inserted = new HashSet<>(jdbc.queryForList("""
                    INSERT INTO learning.credit_cards
                    (batch_id,code_hash,card_type,value,plan_days,status,channel,partner_code,
                     campaign,issued_to,expires_at,created_by,created_at) VALUES
                    """ + String.join(",", tuples)
                    + " ON CONFLICT (code_hash) DO NOTHING RETURNING code_hash", parameters, String.class));
            candidates.forEach((hash, raw) -> {
                if (inserted.contains(hash)) accepted.add(codes.format(raw));
            });
        }
        if (accepted.size() != request.quantity()) throw internal();
        return accepted;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BatchPage list(int page, int size) {
        admin();
        if (page < 0 || size < 1 || size > 100) throw CreateBatchRequest.invalid();
        try {
            Long total = jdbc.queryForObject("SELECT count(DISTINCT batch_id) FROM learning.credit_cards",
                    Map.of(), Long.class);
            List<BatchRow> rows = jdbc.query(GROUP_SQL + " GROUP BY batch_id"
                            + " ORDER BY created_at DESC, batch_id DESC LIMIT :size OFFSET :offset",
                    Map.of("size", size, "offset", (long) page * size), CardBatchService::row);
            return new BatchPage(summaries(rows), page, size, total == null ? 0 : total);
        } catch (DataAccessException exception) {
            LOG.warn("Card batch operation failed; cause_type={}", exception.getClass().getSimpleName());
            throw internal();
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BatchSummary detail(String batchId) {
        admin();
        requireBatchId(batchId);
        try {
            List<BatchRow> rows = jdbc.query(GROUP_SQL + " WHERE batch_id=:batch GROUP BY batch_id",
                    Map.of("batch", batchId), CardBatchService::row);
            if (rows.isEmpty()) throw notFound();
            return summaries(rows).get(0);
        } catch (DataAccessException exception) {
            LOG.warn("Card batch operation failed; cause_type={}", exception.getClass().getSimpleName());
            throw internal();
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VoidResult voidBatch(String batchId) {
        long actor = admin();
        requireBatchId(batchId);
        try {
            Boolean exists = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM learning.credit_cards WHERE batch_id=:batch)",
                    Map.of("batch", batchId), Boolean.class);
            if (!Boolean.TRUE.equals(exists)) throw notFound();
            int changed = jdbc.update("UPDATE learning.credit_cards SET status='VOIDED'"
                    + " WHERE batch_id=:batch AND status='UNUSED'", Map.of("batch", batchId));
            audit.recordDetail(actor, "CARD_BATCH_VOID", "CARD_BATCH", Map.of("batchId", batchId, "voided", changed));
            return new VoidResult(batchId, changed);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOG.warn("Card batch operation failed; cause_type={}", exception.getClass().getSimpleName());
            throw internal();
        }
    }

    private List<BatchSummary> summaries(List<BatchRow> rows) {
        Map<Long, String> names = rows.isEmpty() ? Map.of()
                : auth.displayNames(rows.stream().map(BatchRow::creator).collect(Collectors.toSet()));
        return rows.stream().map(row -> {
            BatchSummary item = row.summary();
            return new BatchSummary(item.batchId(), item.cardType(), item.value(), item.planDays(), item.channel(),
                    item.partnerCode(), item.campaign(), item.issuedTo(), item.expiresAt(), item.total(),
                    item.unused(), item.used(), item.voided(), item.createdAt(), names.get(row.creator()));
        }).toList();
    }

    private static BatchRow row(ResultSet rs, int index) throws SQLException {
        CardType type = CardType.valueOf(rs.getString("card_type"));
        Timestamp expiry = rs.getTimestamp("expires_at");
        return new BatchRow(new BatchSummary(rs.getString("batch_id"), type,
                type == CardType.CREDIT ? rs.getInt("value") : null,
                type == CardType.SUBSCRIPTION ? rs.getInt("plan_days") : null,
                Channel.valueOf(rs.getString("channel")), rs.getString("partner_code"), rs.getString("campaign"),
                rs.getString("issued_to"), expiry == null ? null : expiry.toInstant(), rs.getInt("total"),
                rs.getInt("unused"), rs.getInt("used"), rs.getInt("voided"),
                rs.getTimestamp("created_at").toInstant(), null), rs.getLong("created_by"));
    }

    private long admin() {
        var current = auth.currentUser();
        if (!current.roles().contains(RoleCode.ADMIN)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        return current.userId();
    }

    private static void requireBatchId(String batchId) {
        if (batchId == null || !batchId.matches("^B[0-9]{8}-[0-9A-Z]{6}$")) throw notFound();
    }

    private static Timestamp timestamp(Instant instant) { return instant == null ? null : Timestamp.from(instant); }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.CARD_BATCH_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy lô mã.", Map.of());
    }

    private static ApiException internal() {
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi nội bộ.", Map.of());
    }

    private record BatchRow(BatchSummary summary, long creator) { }
}
