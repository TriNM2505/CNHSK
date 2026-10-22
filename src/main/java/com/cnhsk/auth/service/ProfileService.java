package com.cnhsk.auth.service;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.cnhsk.auth.dto.ChangePasswordRequest;
import com.cnhsk.auth.dto.ProfileResponse;
import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.TokenType;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.AuthTokenRepository;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates the authenticated user's profile and password. */
@Service
public class ProfileService {

    private static final String UNAUTHENTICATED_MESSAGE = "Không thể xác thực người dùng hiện tại.";
    private static final String SESSION_REVOKED_MESSAGE = "Phiên đăng nhập đã bị thu hồi.";
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder(12);

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final Clock clock;

    public ProfileService(UserRepository userRepository, AuthTokenRepository authTokenRepository, Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.authTokenRepository = Objects.requireNonNull(authTokenRepository,
                "authTokenRepository must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(long userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(long userId, JsonNode patch) {
        User user = findUser(userId);
        if (patch == null || !patch.isObject()) {
            throw validation("request", "Nội dung hồ sơ phải là một object JSON.");
        }

        List<Map<String, String>> errors = new ArrayList<>();
        patch.fieldNames().forEachRemaining(field -> {
            if (!List.of("displayName", "avatarUrl", "hskLevel").contains(field)) {
                errors.add(detail(field, "Trường này không được phép chỉnh sửa."));
            }
        });
        if (!errors.isEmpty()) {
            throw validation(errors);
        }

        if (patch.has("displayName")) {
            JsonNode value = patch.get("displayName");
            if (value == null || !value.isTextual()) {
                errors.add(detail("displayName", "Tên hiển thị phải là chuỗi và không được null."));
            } else {
                String displayName = value.textValue().strip();
                if (displayName.length() < 2 || displayName.length() > 100) {
                    errors.add(detail("displayName", "Tên hiển thị cần 2–100 ký tự."));
                } else {
                    user.setDisplayName(displayName);
                }
            }
        }

        if (patch.has("avatarUrl")) {
            JsonNode value = patch.get("avatarUrl");
            if (value == null || value.isNull()) {
                user.setAvatarUrl(null);
            } else if (!value.isTextual() || !validAvatarUrl(value.textValue())) {
                errors.add(detail("avatarUrl", "Ảnh đại diện phải là URL HTTPS không quá 500 ký tự."));
            } else {
                user.setAvatarUrl(value.textValue());
            }
        }

        if (patch.has("hskLevel")) {
            JsonNode value = patch.get("hskLevel");
            if (value == null || value.isNull()) {
                user.setHskLevel(null);
            } else if (!value.isIntegralNumber() || !value.canConvertToInt()
                    || value.intValue() < 1 || value.intValue() > 9) {
                errors.add(detail("hskLevel", "Cấp HSK mục tiêu phải từ 1 đến 9 hoặc null."));
            } else {
                user.setHskLevel((short) value.intValue());
            }
        }

        if (!errors.isEmpty()) {
            throw validation(errors);
        }
        return toResponse(user);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changePassword(long userId, UUID currentSessionId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw validation("confirmPassword", "Hai mật khẩu không khớp.");
        }
        validateNewPassword(request.newPassword());

        User user = userRepository.findByIdForUpdate(userId).orElseThrow(ProfileService::unauthenticated);
        List<AuthToken> activeRefreshTokens = authTokenRepository.findActiveByUserIdAndTokenTypeForUpdate(
                userId, TokenType.REFRESH);
        Instant now = clock.instant();
        boolean currentSessionActive = activeRefreshTokens.stream()
                .anyMatch(token -> currentSessionId.equals(token.getSessionId()) && token.getExpiresAt().isAfter(now));
        if (!currentSessionActive) {
            throw new ApiException(ErrorCode.SESSION_REVOKED, HttpStatus.UNAUTHORIZED,
                    SESSION_REVOKED_MESSAGE, Map.of());
        }
        if (!PASSWORD_ENCODER.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.CURRENT_PASSWORD_INCORRECT, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mật khẩu hiện tại không đúng.", Map.of());
        }
        if (request.newPassword().equalsIgnoreCase(user.getEmail())) {
            throw validation("newPassword", "Mật khẩu không được trùng email.");
        }
        if (PASSWORD_ENCODER.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_UNCHANGED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mật khẩu mới phải khác mật khẩu hiện tại.", Map.of());
        }

        user.setPasswordHash(PASSWORD_ENCODER.encode(request.newPassword()));
        activeRefreshTokens.stream()
                .filter(token -> !currentSessionId.equals(token.getSessionId()))
                .forEach(token -> token.setRevokedAt(now));
        authTokenRepository.saveAll(activeRefreshTokens);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId).orElseThrow(ProfileService::unauthenticated);
    }

    private static ProfileResponse toResponse(User user) {
        return new ProfileResponse(user.getDisplayName(), user.getAvatarUrl(), user.getHskLevel(),
                user.getEmail(), user.getEmailVerifiedAt() != null);
    }

    private static boolean validAvatarUrl(String value) {
        if (value.length() > 500 || !value.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void validateNewPassword(String password) {
        if (password.length() < 8 || password.length() > 72 || !password.equals(password.strip())
                || !LETTER.matcher(password).find() || !DIGIT.matcher(password).find()) {
            throw validation("newPassword", "Mật khẩu cần 8–72 ký tự, gồm chữ và số.");
        }
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                UNAUTHENTICATED_MESSAGE, Map.of());
    }

    private static Map<String, String> detail(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    private static ApiException validation(String field, String message) {
        return validation(List.of(detail(field, message)));
    }

    private static ApiException validation(List<Map<String, String>> details) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", details));
    }
}
