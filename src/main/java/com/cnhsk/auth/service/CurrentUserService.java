package com.cnhsk.auth.service;

import java.util.Objects;

import com.cnhsk.auth.dto.CurrentUserResponse;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads the current user's public identity and roles from the auth module database. */
@Service
public class CurrentUserService {

    private static final String UNAUTHENTICATED_MESSAGE = "Không thể xác thực người dùng hiện tại.";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public CurrentUserService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null.");
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(CurrentUserService::unauthenticated);
        var roles = userRoleRepository.findAllByUserIdOrderByRoleCodeAsc(userId).stream()
                .map(userRole -> userRole.getRoleCode())
                .toList();

        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getEmailVerifiedAt() != null, roles, user.getHskLevel(), user.getAvatarUrl());
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                UNAUTHENTICATED_MESSAGE, java.util.Map.of());
    }
}
