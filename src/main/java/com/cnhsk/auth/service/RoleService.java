package com.cnhsk.auth.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.SessionInvalidationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private static final String USER_NOT_FOUND_MESSAGE = "Không tìm thấy tài khoản.";
    private static final String INVALID_ROLE_MESSAGE = "Role không hợp lệ.";

    private final AdminGuardService adminGuardService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;
    private final SessionInvalidationService invalidationService;
    private final Clock clock;

    public RoleService(AdminGuardService adminGuardService, UserRepository userRepository,
            UserRoleRepository userRoleRepository, AuditService auditService,
            SessionInvalidationService invalidationService, Clock clock) {
        this.adminGuardService = Objects.requireNonNull(adminGuardService, "adminGuardService must not be null.");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null.");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null.");
        this.invalidationService = Objects.requireNonNull(invalidationService,
                "invalidationService must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void grantRole(long actorId, long targetUserId, String roleValue) {
        RoleCode roleCode = parseRole(roleValue);
        adminGuardService.lockAndCountActiveAdmins();
        User target = lockTarget(targetUserId);
        List<UserRole> currentRoles = userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(targetUserId);

        if (actorId == targetUserId) {
            throw apiError(ErrorCode.SELF_ROLE_GRANT, HttpStatus.FORBIDDEN,
                    "Không thể tự cấp role cho chính mình.");
        }
        if (currentRoles.stream().anyMatch(role -> role.getRoleCode() == roleCode)) {
            throw apiError(ErrorCode.ROLE_ALREADY_GRANTED, HttpStatus.CONFLICT,
                    "Tài khoản đã có role này.");
        }

        List<String> before = roleNames(currentRoles);
        userRoleRepository.saveAndFlush(new UserRole(target.getId(), roleCode, actorId));
        List<String> after = new ArrayList<>(before);
        after.add(roleCode.name());
        after.sort(String::compareTo);
        auditService.record(actorId, "ROLE_GRANT", "user:" + targetUserId, before, after);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void revokeRole(long actorId, long targetUserId, String roleValue) {
        RoleCode roleCode = parseRole(roleValue);
        long activeAdminCount = adminGuardService.lockAndCountActiveAdmins();
        User target = lockTarget(targetUserId);
        List<UserRole> currentRoles = userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(targetUserId);
        UserRole role = currentRoles.stream()
                .filter(candidate -> candidate.getRoleCode() == roleCode)
                .findFirst()
                .orElseThrow(() -> apiError(ErrorCode.ROLE_NOT_GRANTED, HttpStatus.NOT_FOUND,
                        "Tài khoản không có role này."));

        if (roleCode == RoleCode.ADMIN && actorId == targetUserId) {
            throw apiError(ErrorCode.SELF_ROLE_REVOKE, HttpStatus.FORBIDDEN,
                    "Không thể tự thu hồi role ADMIN.");
        }
        if (roleCode == RoleCode.ADMIN && target.getBannedAt() == null && activeAdminCount <= 1) {
            throw apiError(ErrorCode.REVOKE_LAST_ADMIN, HttpStatus.CONFLICT,
                    "Không thể thu hồi ADMIN cuối cùng còn hoạt động.");
        }

        List<String> before = roleNames(currentRoles);
        userRoleRepository.delete(role);
        userRoleRepository.flush();
        List<String> after = before.stream().filter(value -> !value.equals(roleCode.name())).toList();
        auditService.record(actorId, "ROLE_REVOKE", "user:" + targetUserId, before, after);

        // Write before PostgreSQL commits so Redis failure rolls back the role and audit changes.
        invalidationService.markRevoked(targetUserId, clock.instant().toEpochMilli() + 1);
    }

    private User lockTarget(long targetUserId) {
        return userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> apiError(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        USER_NOT_FOUND_MESSAGE));
    }

    private static RoleCode parseRole(String value) {
        if (value == null || value.isBlank()) {
            throw validationError();
        }
        try {
            return RoleCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw validationError();
        }
    }

    private static ApiException validationError() {
        return apiError(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, INVALID_ROLE_MESSAGE);
    }

    private static ApiException apiError(ErrorCode code, HttpStatus status, String message) {
        return new ApiException(code, status, message, Map.of());
    }

    private static List<String> roleNames(List<UserRole> roles) {
        return roles.stream().map(UserRole::getRoleCode).map(Enum::name).toList();
    }
}
