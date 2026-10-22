package com.cnhsk.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.SessionInvalidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Coordinates account ban state, token revocation, audit, and session markers. */
@Service
public class BanService {

    private static final Logger log = LoggerFactory.getLogger(BanService.class);
    private static final String USER_NOT_FOUND_MESSAGE = "Không tìm thấy tài khoản.";
    private static final String INVALID_REASON_MESSAGE = "Lý do cấm phải dài từ 5 đến 500 ký tự.";

    private final AdminGuardService adminGuardService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthTokenRepository authTokenRepository;
    private final AuditService auditService;
    private final SessionInvalidationService invalidationService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public BanService(AdminGuardService adminGuardService, UserRepository userRepository,
            UserRoleRepository userRoleRepository, AuthTokenRepository authTokenRepository,
            AuditService auditService, SessionInvalidationService invalidationService,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.adminGuardService = Objects.requireNonNull(adminGuardService, "adminGuardService must not be null.");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null.");
        this.authTokenRepository = Objects.requireNonNull(authTokenRepository,
                "authTokenRepository must not be null.");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null.");
        this.invalidationService = Objects.requireNonNull(invalidationService,
                "invalidationService must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
        this.transactionTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null."));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public void ban(long actorId, long targetUserId, String rawReason) {
        String reason = normalizeReason(rawReason);
        transactionTemplate.executeWithoutResult(status -> banInTransaction(actorId, targetUserId, reason));
    }

    public void unban(long actorId, long targetUserId) {
        transactionTemplate.executeWithoutResult(status -> unbanInTransaction(actorId, targetUserId));

        // Also run on an idempotent retry: it can finish marker synchronization after a prior Redis outage.
        try {
            invalidationService.markUnbanned(targetUserId);
        } catch (RuntimeException exception) {
            log.warn("Session marker update failed after committed account unban; marker will expire (exceptionType={}).",
                    exception.getClass().getSimpleName());
        }
    }

    private void banInTransaction(long actorId, long targetUserId, String reason) {
        long activeAdminCount = adminGuardService.lockAndCountActiveAdmins();
        User target = lockTarget(targetUserId);
        if (actorId == targetUserId) {
            throw apiError(ErrorCode.SELF_BAN, HttpStatus.FORBIDDEN, "Không thể tự cấm tài khoản.");
        }
        if (target.getBannedAt() != null) {
            return;
        }

        boolean targetIsAdmin = userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(targetUserId).stream()
                .anyMatch(role -> role.getRoleCode() == RoleCode.ADMIN);
        if (targetIsAdmin && activeAdminCount <= 1) {
            throw apiError(ErrorCode.BAN_LAST_ADMIN, HttpStatus.CONFLICT,
                    "Không thể cấm ADMIN cuối cùng còn hoạt động.");
        }

        Instant now = clock.instant();
        BanAuditState before = new BanAuditState(target.getBannedAt(), target.getBanReason());
        target.setBannedAt(now);
        target.setBannedBy(actorId);
        target.setBanReason(reason);
        userRepository.saveAndFlush(target);

        List<AuthToken> activeRefreshTokens = authTokenRepository
                .findActiveByUserIdAndTokenTypeForUpdate(targetUserId, TokenType.REFRESH);
        activeRefreshTokens.forEach(token -> token.setRevokedAt(now));
        authTokenRepository.saveAllAndFlush(activeRefreshTokens);

        auditService.record(actorId, "USER_BAN", "user:" + targetUserId,
                before.asMap(), new BanAuditState(now, reason).asMap());

        try {
            invalidationService.markBanned(targetUserId, now.toEpochMilli() + 1, reason);
        } catch (RuntimeException exception) {
            log.error("Session marker write failed during account ban; database transaction will roll back (exceptionType={}).",
                    exception.getClass().getSimpleName());
            throw apiError(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
                    "Đã xảy ra lỗi nội bộ.");
        }
    }

    private void unbanInTransaction(long actorId, long targetUserId) {
        // The shared advisory lock serializes this active-admin change with T-24 grant/revoke and T-25 ban.
        adminGuardService.lockAndCountActiveAdmins();
        User target = lockTarget(targetUserId);
        if (target.getBannedAt() == null) {
            return;
        }

        BanAuditState before = new BanAuditState(target.getBannedAt(), target.getBanReason());
        target.setBannedAt(null);
        target.setBannedBy(null);
        target.setBanReason(null);
        userRepository.saveAndFlush(target);
        auditService.record(actorId, "USER_UNBAN", "user:" + targetUserId,
                before.asMap(), new BanAuditState(null, null).asMap());
    }

    private User lockTarget(long targetUserId) {
        return userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> apiError(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        USER_NOT_FOUND_MESSAGE));
    }

    private static String normalizeReason(String rawReason) {
        if (rawReason == null) {
            throw validationError();
        }
        String reason = rawReason.strip();
        if (reason.length() < 5 || reason.length() > 500) {
            throw validationError();
        }
        return reason;
    }

    private static ApiException validationError() {
        return apiError(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, INVALID_REASON_MESSAGE);
    }

    private static ApiException apiError(ErrorCode code, HttpStatus status, String message) {
        return new ApiException(code, status, message, Map.of());
    }

    private record BanAuditState(Instant bannedAt, String reason) {
        private Map<String, Object> asMap() {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("bannedAt", bannedAt);
            state.put("reason", reason);
            return state;
        }
    }
}
