package com.cnhsk.auth.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements the auth module's public API for internal module callers. */
@Service
public class AuthApiService implements AuthApi {

    private static final String UNAUTHENTICATED_MESSAGE = "Không thể xác thực người dùng hiện tại.";
    private static final String ACCOUNT_UNVERIFIED_MESSAGE = "Vui lòng xác minh email trước khi tiếp tục.";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public AuthApiService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null.");
    }

    @Override
    public CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw unauthenticated();
        }
        Set<RoleCode> roles = principal.roles().stream()
                .map(RoleCode::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(principal.userId(), roles, principal.emailVerified());
    }

    @Override
    @Transactional(readOnly = true)
    public void requireVerifiedEmail(long userId) {
        boolean verified = userRepository.findById(userId)
                .map(user -> user.getEmailVerifiedAt() != null)
                .orElseThrow(AuthApiService::unauthenticated);
        if (!verified) {
            throw new ApiException(ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN,
                    ACCOUNT_UNVERIFIED_MESSAGE, Map.of());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasRole(long userId, RoleCode role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null.");
        }
        return userRoleRepository.existsByUserIdAndRoleCode(userId,
                com.cnhsk.auth.entity.RoleCode.valueOf(role.name()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> displayNames(Set<Long> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null.");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        userRepository.findAllById(userIds)
                .forEach(user -> names.put(user.getId(), user.getDisplayName()));
        return Map.copyOf(names);
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                UNAUTHENTICATED_MESSAGE, Map.of());
    }
}
