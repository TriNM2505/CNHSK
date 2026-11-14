package com.cnhsk.learning.reference;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.RateLimiter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceReportService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceReportService.class);
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private final AuthApi auth;
    private final NamedParameterJdbcTemplate jdbc;
    private final RateLimiter counters;
    private final AuditService audit;
    private final Clock clock;

    public ReferenceReportService(AuthApi auth, NamedParameterJdbcTemplate jdbc, RateLimiter counters,
            AuditService audit, Clock clock) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.counters = counters;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public void report(String rawId, ReportCommand command) {
        if (command == null || command.reason() == null) throw invalid("reason");
        if (command.note() != null && command.note().codePointCount(0, command.note().length()) > 300) {
            throw invalid("note");
        }
        var caller = auth.currentUser();
        if (!caller.roles().contains(RoleCode.LEARNER)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        long id = id(rawId);
        if (jdbc.queryForList("SELECT id FROM learning.reference_items "
                + "WHERE id = :id AND status = 'PUBLISHED' FOR SHARE", Map.of("id", id), Long.class).isEmpty()) {
            throw notFound();
        }

        var now = clock.instant();
        var day = now.atZone(VIETNAM).toLocalDate();
        long retry = Math.max(1, (Duration.between(now, day.plusDays(1).atStartOfDay(VIETNAM).toInstant())
                .toMillis() + 999) / 1000);
        String key = "reference:report:" + caller.userId() + ":" + id + ":" + DateTimeFormatter.BASIC_ISO_DATE.format(day);
        long count;
        try {
            count = counters.increment(key, Duration.ofSeconds(retry));
        } catch (RuntimeException exception) {
            log.warn("Reference report rate limit unavailable; rejecting request");
            throw internalError();
        }
        if (count > 1) {
            throw new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn đã báo lỗi mục này hôm nay. Vui lòng thử lại vào ngày mai.",
                    Map.of("retryAfterSeconds", retry));
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", command.reason().name());
        detail.put("note", command.note());
        try {
            audit.recordDetail(caller.userId(), "REFERENCE_REPORTED", "REFERENCE", id, detail);
        } catch (RuntimeException exception) {
            // Keep the reserved daily slot; do not expose audit parameters in the error path.
            throw internalError();
        }
    }

    public static ApiException invalid(String field) {
        String message = "Dữ liệu không hợp lệ.";
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message,
                Map.of("details", java.util.List.of(Map.of("field", field, "message", message))));
    }

    private long id(String rawId) {
        try {
            if (rawId == null || !rawId.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(rawId);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException exception) {
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.REFERENCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy tài nguyên tham khảo.", Map.of());
    }

    private static ApiException internalError() {
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                "Đã xảy ra lỗi nội bộ.", Map.of());
    }

    public enum Reason {
        DEAD_LINK, WRONG_CONTENT, OTHER
    }

    @Schema(requiredProperties = {"reason"})
    public record ReportCommand(Reason reason,
            @Schema(types = {"string", "null"}, maxLength = 300) String note) {
    }
}
