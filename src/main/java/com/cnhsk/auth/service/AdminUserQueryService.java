package com.cnhsk.auth.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.cnhsk.auth.dto.AdminUserDetailResponse;
import com.cnhsk.auth.dto.AdminUserPageResponse;
import com.cnhsk.auth.dto.AdminUserRoleResponse;
import com.cnhsk.auth.dto.AdminUserSummaryResponse;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.entity.UserStatus;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.util.Masking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final String VALIDATION_MESSAGE = "Dữ liệu tìm kiếm không hợp lệ.";
    private static final String USER_NOT_FOUND_MESSAGE = "Không tìm thấy tài khoản.";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditService auditService;

    public AdminUserQueryService(UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            AuditService auditService) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null.");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null.");
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse search(String queryValue, String roleValue, String statusValue,
            String pageValue, String sizeValue) {
        String query = normalizeQuery(queryValue);
        RoleCode role = parseEnum(roleValue, RoleCode.class);
        UserStatus status = parseEnum(statusValue, UserStatus.class);
        int pageNumber = parseInteger(pageValue, DEFAULT_PAGE, "page");
        int pageSize = parseInteger(sizeValue, DEFAULT_SIZE, "size");

        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw validationFailure();
        }

        PageRequest pageable = PageRequest.of(pageNumber, pageSize, Sort.unsorted());
        Page<User> users = userRepository.searchAdminUsers(query,
                role == null ? null : role.name(),
                status == null ? null : status.name(),
                pageable);
        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Map<Long, List<RoleCode>> rolesByUser = userIds.isEmpty()
                ? Map.of()
                : userRoleRepository.findAllByUserIdInOrderByUserIdAscRoleCodeAsc(userIds).stream()
                        .collect(Collectors.groupingBy(UserRole::getUserId,
                                Collectors.mapping(UserRole::getRoleCode, Collectors.toList())));

        List<AdminUserSummaryResponse> items = users.getContent().stream()
                .map(user -> new AdminUserSummaryResponse(
                        user.getId(),
                        Masking.email(user.getEmail()),
                        user.getDisplayName(),
                        rolesByUser.getOrDefault(user.getId(), List.of()),
                        user.status(),
                        user.getCreatedAt()))
                .toList();
        return new AdminUserPageResponse(items, users.getNumber(), users.getSize(), users.getTotalElements());
    }

    @Transactional
    public AdminUserDetailResponse getDetail(long actorId, long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND,
                        USER_NOT_FOUND_MESSAGE, Map.of()));
        List<AdminUserRoleResponse> roles = userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(userId).stream()
                .map(role -> new AdminUserRoleResponse(role.getRoleCode(), role.getGrantedBy(), role.getGrantedAt()))
                .toList();

        // Audit and detail read share this transaction; the controller cannot return PII until it commits.
        auditService.record(actorId, "VIEW_USER_PII", "user:" + userId, null, null);

        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                roles,
                user.status(),
                user.getBannedAt(),
                user.getBanReason(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt());
    }

    private String normalizeQuery(String queryValue) {
        if (queryValue == null) {
            return null;
        }
        String query = queryValue.strip();
        if (query.length() < 2 || query.length() > MAX_QUERY_LENGTH) {
            throw validationFailure();
        }
        return query;
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw validationFailure();
        }
    }

    private int parseInteger(String value, int defaultValue, String field) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw validationFailure();
        }
    }

    private ApiException validationFailure() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                VALIDATION_MESSAGE, Map.of());
    }
}
