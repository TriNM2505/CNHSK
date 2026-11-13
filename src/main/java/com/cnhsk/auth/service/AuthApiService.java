package com.cnhsk.auth.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.PlanState;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.cnhsk.shared.util.Masking;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Implements the auth module's public API for internal module callers. */
@Service
public class AuthApiService implements AuthApi {

    private static final String UNAUTHENTICATED_MESSAGE = "Không thể xác thực người dùng hiện tại.";
    private static final String ACCOUNT_UNVERIFIED_MESSAGE = "Vui lòng xác minh email trước khi tiếp tục.";
    private static final String INVALID_ARGUMENT_MESSAGE = "Tham số không hợp lệ.";
    private static final TypeReference<Map<String, Integer>> USAGE_TYPE = new TypeReference<>() {};

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AuthApiService(UserRepository userRepository, UserRoleRepository userRoleRepository,
            NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null.");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null.");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null.");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null.");
        this.clock = Objects.requireNonNull(clock, "clock must not be null.");
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

    @Override
    @Transactional(readOnly = true)
    public Set<Long> bannedUserIds() {
        return jdbc.query("SELECT id FROM auth.users WHERE banned_at IS NOT NULL", Map.of(), rs -> {
            Set<Long> ids = new java.util.LinkedHashSet<>();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
            return Set.copyOf(ids);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, PublicProfile> publicProfiles(Set<Long> userIds) {
        requireValid(userIds != null && userIds.stream().allMatch(id -> id != null && id > 0));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PublicProfile> profiles = jdbc.query("""
                SELECT id, display_name, avatar_url FROM auth.users WHERE id IN (:ids)
                """, Map.of("ids", userIds), rs -> {
                    Map<Long, PublicProfile> result = new LinkedHashMap<>();
                    while (rs.next()) {
                        result.put(rs.getLong("id"), new PublicProfile(
                                rs.getString("display_name"), rs.getString("avatar_url")));
                    }
                    return result;
                });
        return Map.copyOf(profiles);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanState> readPlan(long userId) {
        requireValid(userId > 0);
        return jdbc.query("""
                SELECT plan_code, plan_expires_at, free_usage::text, usage_reset_at
                FROM auth.users WHERE id = :id
                """, Map.of("id", userId), (rs, row) -> new PlanState(rs.getString("plan_code"),
                        instant(rs.getTimestamp("plan_expires_at")), readUsage(rs.getString("free_usage")),
                        instant(rs.getTimestamp("usage_reset_at"))))
                .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PlanState lockPlan(long userId) {
        requireWritableTransaction();
        requireValid(userId > 0);
        return jdbc.query("""
                SELECT plan_code, plan_expires_at, free_usage::text, usage_reset_at
                FROM auth.users WHERE id = :id FOR UPDATE
                """, Map.of("id", userId), (rs, row) -> new PlanState(rs.getString("plan_code"),
                        instant(rs.getTimestamp("plan_expires_at")), readUsage(rs.getString("free_usage")),
                        instant(rs.getTimestamp("usage_reset_at"))))
                .stream().findFirst().orElseThrow(AuthApiService::userNotFound);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updatePlan(long userId, String planCode, Instant expiresAt) {
        requireWritableTransaction();
        requireValid(userId > 0 && Set.of("FREE", "PREMIUM", "PREMIUM_PLUS").contains(
                planCode == null ? "" : planCode));
        requireValid("FREE".equals(planCode) == (expiresAt == null));
        requireUpdatedUser(jdbc.update("""
                UPDATE auth.users SET plan_code = :code, plan_expires_at = :expires, updated_at = :updated
                WHERE id = :id
                """, new MapSqlParameterSource("id", userId).addValue("code", planCode)
                        .addValue("expires", timestamp(expiresAt)).addValue("updated", timestamp(clock.instant()))));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateUsage(long userId, Map<String, Integer> usage, Instant resetAt) {
        requireWritableTransaction();
        requireValid(userId > 0 && usage != null && resetAt != null);
        requireValid(usage.entrySet().stream().allMatch(entry -> entry.getKey() != null
                && !entry.getKey().isBlank() && entry.getValue() != null && entry.getValue() >= 0));
        String json;
        try {
            json = mapper.writeValueAsString(usage);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(INVALID_ARGUMENT_MESSAGE);
        }
        requireUpdatedUser(jdbc.update("""
                UPDATE auth.users SET free_usage = CAST(:usage AS jsonb), usage_reset_at = :reset
                WHERE id = :id
                """, new MapSqlParameterSource("id", userId).addValue("usage", json)
                        .addValue("reset", timestamp(resetAt))));
    }

    @Override
    @Transactional
    public int downgradeExpiredPlans(Instant now) {
        requireWritableTransaction();
        requireValid(now != null);
        return jdbc.update("""
                UPDATE auth.users SET plan_code = 'FREE', plan_expires_at = NULL, updated_at = :now
                WHERE plan_code <> 'FREE' AND plan_expires_at <= :now
                """, Map.of("now", timestamp(now)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> maskedEmails(Set<Long> userIds) {
        requireValid(userIds != null && userIds.stream().allMatch(id -> id != null && id > 0));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT id, email FROM auth.users WHERE id IN (:ids)", Map.of("ids", userIds),
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.put(rs.getLong("id"), Masking.email(rs.getString("email"))));
        return Map.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActivePlan(String planCode, Instant now) {
        requireValid(planCode != null && !planCode.isBlank() && now != null);
        String predicate;
        if ("PREMIUM".equals(planCode)) {
            predicate = "plan_code = 'PREMIUM' AND plan_expires_at > :now";
        } else if ("FREE".equals(planCode)) {
            predicate = "plan_code = 'FREE' OR plan_expires_at <= :now";
        } else {
            return 0;
        }
        return jdbc.queryForObject("SELECT count(*) FROM auth.users WHERE " + predicate,
                Map.of("now", timestamp(now)), Long.class);
    }

    private Map<String, Integer> readUsage(String json) {
        try {
            return mapper.readValue(json, USAGE_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Dữ liệu hạn mức không hợp lệ.");
        }
    }

    private static void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Cần transaction ghi đang hoạt động.");
        }
    }

    private static void requireValid(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException(INVALID_ARGUMENT_MESSAGE);
        }
    }

    private static void requireUpdatedUser(int count) {
        if (count == 0) {
            throw userNotFound();
        }
    }

    private static ApiException userNotFound() {
        return new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND,
                "Không tìm thấy tài khoản.", Map.of());
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                UNAUTHENTICATED_MESSAGE, Map.of());
    }
}
