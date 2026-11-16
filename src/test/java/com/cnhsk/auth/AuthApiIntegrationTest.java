package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.PublicProfile;
import com.cnhsk.auth.entity.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.entity.UserRole;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Clock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect")
class AuthApiIntegrationTest extends AbstractIntegrationTest {

    private static final Instant ADMIN_NOW = Instant.parse("2026-11-16T06:00:00Z");
    @MockitoBean Clock clock;

    @BeforeEach
    void fixedAdminClock() {
        when(clock.instant()).thenReturn(ADMIN_NOW);
        when(clock.millis()).thenReturn(ADMIN_NOW.toEpochMilli());
    }

    private static final String PASSWORD = "UnverifiedPass123";

    @Autowired private AuthApi authApi;
    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void AT12_unverifiedUserCanLoginButVerifiedEmailRequirementRejectsPointAction() throws Exception {
        String email = "auth-api-" + java.util.UUID.randomUUID() + "@example.test";
        User user = userRepository.saveAndFlush(new User(email,
                new BCryptPasswordEncoder(12).encode(PASSWORD), "Auth API Learner"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.LEARNER, null));

        assertThatThrownBy(() -> authApi.requireVerifiedEmail(user.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getCode()).isEqualTo(ErrorCode.ACCOUNT_UNVERIFIED);
                    assertThat(apiException.getStatus().value()).isEqualTo(403);
                });

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "password", PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void T01_bannedUserIdsIncludesBannedButNotSuspendedUsers() {
        User banned = new User(uniqueEmail(), "hash", "Banned");
        banned.setBannedAt(Instant.now());
        banned.setBanReason("policy violation");
        User suspended = new User(uniqueEmail(), "hash", "Suspended");
        suspended.setSuspendedAt(Instant.now());
        suspended.setSuspendedUntil(Instant.now().plusSeconds(3600));
        User active = new User(uniqueEmail(), "hash", "Active");
        userRepository.saveAllAndFlush(java.util.List.of(banned, suspended, active));

        assertThat(authApi.bannedUserIds())
                .contains(banned.getId())
                .doesNotContain(suspended.getId(), active.getId());
    }

    @Test
    void T01_publicProfilesReturnsRequestedProfilesIncludingBannedAndOmitsMissingIds() {
        User visible = new User(uniqueEmail(), "hash", "Visible");
        visible.setAvatarUrl("https://example.test/avatar.png");
        User banned = new User(uniqueEmail(), "hash", "Banned profile");
        banned.setBannedAt(Instant.now());
        banned.setBanReason("policy violation");
        userRepository.saveAllAndFlush(java.util.List.of(visible, banned));

        assertThat(authApi.publicProfiles(java.util.Set.of(visible.getId(), banned.getId(), Long.MAX_VALUE)))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        visible.getId(), new PublicProfile("Visible", "https://example.test/avatar.png"),
                        banned.getId(), new PublicProfile("Banned profile", null)));
        assertThat(Arrays.stream(PublicProfile.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("displayName", "avatarUrl");
    }

    @Test
    void contactEmailsReturnsOnlyRequestedExistingAccountsWithoutAPrincipal() {
        String hash = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
        User first = userRepository.saveAndFlush(new User(uniqueEmail(), hash, "Người đạt giải tự soạn"));
        User other = userRepository.saveAndFlush(new User(uniqueEmail(), hash, "Người ngoài tập được hỏi"));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        var emails = authApi.contactEmails(java.util.Set.of(first.getId(), Long.MAX_VALUE));
        assertThat(emails).containsOnly(java.util.Map.entry(first.getId(), first.getEmail()));
        assertThat(emails).doesNotContainKey(other.getId());
        assertThat(authApi.contactEmails(java.util.Set.of(first.getId(), other.getId())))
                .containsOnly(java.util.Map.entry(first.getId(), first.getEmail()), java.util.Map.entry(other.getId(), other.getEmail()));
        assertThat(authApi.contactEmails(java.util.Set.of())).isEmpty();
        assertThatThrownBy(() -> emails.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void activeAdminRequiresAnExistingPositiveIdWithTheAdminRole() {
        User admin = admin();
        User learner = userRepository.saveAndFlush(new User(uniqueEmail(), "hash", "Learner"));
        userRoleRepository.saveAndFlush(new UserRole(learner.getId(), RoleCode.LEARNER, null));
        User noRole = userRepository.saveAndFlush(new User(uniqueEmail(), "hash", "No role"));

        assertThat(authApi.isActiveAdmin(admin.getId())).isTrue();
        for (long id : java.util.List.of(0L, -1L, Long.MAX_VALUE, learner.getId(), noRole.getId()))
            assertThat(authApi.isActiveAdmin(id)).as("id %s", id).isFalse();
    }

    @Test
    void bannedOrCurrentlySuspendedAdminsAreInactive() {
        User banned = admin();
        banned.setBannedAt(ADMIN_NOW.minusSeconds(1));
        banned.setBanReason("Fixture ban");
        userRepository.saveAndFlush(banned);
        User suspended = admin();
        suspended.setSuspendedAt(ADMIN_NOW.minusSeconds(60));
        suspended.setSuspendedUntil(ADMIN_NOW.plusSeconds(1));
        userRepository.saveAndFlush(suspended);

        assertThat(authApi.isActiveAdmin(banned.getId())).isFalse();
        assertThat(authApi.isActiveAdmin(suspended.getId())).isFalse();
    }

    @Test
    void suspensionExpiryIsInclusiveAndHistoricalSuspensionDoesNotDisableAdmin() {
        for (Instant expiry : java.util.List.of(ADMIN_NOW, ADMIN_NOW.minusSeconds(1))) {
            User admin = admin();
            admin.setSuspendedAt(ADMIN_NOW.minusSeconds(60));
            admin.setSuspendedUntil(expiry);
            userRepository.saveAndFlush(admin);
            assertThat(authApi.isActiveAdmin(admin.getId())).as("expiry %s", expiry).isTrue();
        }
        User historical = admin();
        historical.setSuspendedAt(ADMIN_NOW.minusSeconds(60));
        userRepository.saveAndFlush(historical);
        assertThat(authApi.isActiveAdmin(historical.getId())).isTrue();
    }

    @Test
    void activeAdminDoesNotRequireEmailVerification() {
        User admin = admin();
        assertThat(admin.getEmailVerifiedAt()).isNull();
        assertThat(authApi.isActiveAdmin(admin.getId())).isTrue();
    }

    private User admin() {
        User user = userRepository.saveAndFlush(new User(uniqueEmail(), "hash", "Admin fixture"));
        userRoleRepository.saveAndFlush(new UserRole(user.getId(), RoleCode.ADMIN, null));
        return user;
    }

    private static String uniqueEmail() {
        return "auth-api-public-" + UUID.randomUUID() + "@example.test";
    }
}
