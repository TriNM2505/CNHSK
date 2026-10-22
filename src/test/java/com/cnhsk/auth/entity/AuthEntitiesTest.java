package com.cnhsk.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class AuthEntitiesTest {

    @Test
    void userStatusIsDerivedWithBanTakingPrecedence() {
        User user = new User();
        assertThat(user.status()).isEqualTo(UserStatus.UNVERIFIED);

        user.setEmailVerifiedAt(Instant.parse("2026-10-20T10:00:00Z"));
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);

        user.setBannedAt(Instant.parse("2026-10-20T11:00:00Z"));
        assertThat(user.status()).isEqualTo(UserStatus.BANNED);
    }

    @Test
    void userMapsAllAuthOwnedFieldsExceptFreeUsageCounters() {
        User user = new User();
        Instant timestamp = Instant.parse("2026-10-20T10:00:00Z");
        user.setEmail("learner@example.test");
        user.setPasswordHash("bcrypt-hash");
        user.setDisplayName("Learner");
        user.setAvatarUrl("https://example.test/avatar.png");
        user.setHskLevel((short) 3);
        user.setEmailVerifiedAt(timestamp);
        user.setSuspendedAt(timestamp);
        user.setSuspendedUntil(timestamp.plusSeconds(3600));
        user.setBannedAt(timestamp);
        user.setBannedBy(12L);
        user.setBanReason("policy violation");
        user.setReminderTime(LocalTime.of(8, 30));
        user.setReminderChannels("WEB,EMAIL");
        user.setPlanCode("FREE");
        user.setPlanExpiresAt(null);

        assertThat(user.getId()).isNull();
        assertThat(user.getEmail()).isEqualTo("learner@example.test");
        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(user.getDisplayName()).isEqualTo("Learner");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.test/avatar.png");
        assertThat(user.getHskLevel()).isEqualTo((short) 3);
        assertThat(user.getEmailVerifiedAt()).isEqualTo(timestamp);
        assertThat(user.getSuspendedAt()).isEqualTo(timestamp);
        assertThat(user.getSuspendedUntil()).isEqualTo(timestamp.plusSeconds(3600));
        assertThat(user.getBannedAt()).isEqualTo(timestamp);
        assertThat(user.getBannedBy()).isEqualTo(12L);
        assertThat(user.getBanReason()).isEqualTo("policy violation");
        assertThat(user.getReminderTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(user.getReminderChannels()).isEqualTo("WEB,EMAIL");
        assertThat(user.getPlanCode()).isEqualTo("FREE");
        assertThat(user.getPlanExpiresAt()).isNull();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    void userRoleIdUsesBothCompositeKeyParts() {
        UserRoleId first = new UserRoleId(7L, RoleCode.ADMIN);
        UserRoleId equal = new UserRoleId(7L, RoleCode.ADMIN);
        UserRoleId differentRole = new UserRoleId(7L, RoleCode.LEARNER);
        UserRoleId differentUser = new UserRoleId(8L, RoleCode.ADMIN);

        assertThat(first.getUserId()).isEqualTo(7L);
        assertThat(first.getRoleCode()).isEqualTo(RoleCode.ADMIN);
        assertThat(first).isEqualTo(equal).hasSameHashCodeAs(equal);
        assertThat(first).isNotEqualTo(differentRole).isNotEqualTo(differentUser);
        assertThat(first).isNotEqualTo(null).isNotEqualTo("not a key");
    }

    @Test
    void userRoleAndTokenExposeTheirPersistedProperties() {
        Instant now = Instant.parse("2026-10-20T10:00:00Z");
        UserRole role = new UserRole(5L, RoleCode.TEACHER, 9L);
        role.setGrantedBy(null);
        assertThat(role.getUserId()).isEqualTo(5L);
        assertThat(role.getRoleCode()).isEqualTo(RoleCode.TEACHER);
        assertThat(role.getGrantedBy()).isNull();
        assertThat(role.getGrantedAt()).isNull();

        AuthToken token = new AuthToken(5L, TokenType.EMAIL_VERIFY, "sha256-hash", now);
        token.setTokenHash("rotated-hash");
        token.setExpiresAt(now.plusSeconds(60));
        token.setRevokedAt(now);
        assertThat(token.getId()).isNull();
        assertThat(token.getUserId()).isEqualTo(5L);
        assertThat(token.getTokenType()).isEqualTo(TokenType.EMAIL_VERIFY);
        assertThat(token.getTokenHash()).isEqualTo("rotated-hash");
        assertThat(token.getExpiresAt()).isEqualTo(now.plusSeconds(60));
        assertThat(token.getRevokedAt()).isEqualTo(now);
        assertThat(token.getCreatedAt()).isNull();
    }

    @Test
    void enumsContainOnlyDatabaseContractValues() {
        assertThat(RoleCode.values()).containsExactly(
                RoleCode.LEARNER, RoleCode.TEACHER, RoleCode.MODERATOR,
                RoleCode.CONTENT_EDITOR, RoleCode.ACCOUNTANT, RoleCode.ADMIN);
        assertThat(TokenType.values()).containsExactly(
                TokenType.REFRESH, TokenType.RESET_PASSWORD, TokenType.EMAIL_VERIFY);
    }
}
