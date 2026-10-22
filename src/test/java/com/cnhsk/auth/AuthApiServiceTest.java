package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.auth.repository.UserRoleRepository;
import com.cnhsk.auth.service.AuthApiService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthApiServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserRoleRepository userRoleRepository;

    private AuthApi authApi;

    @BeforeEach
    void setUp() {
        authApi = new AuthApiService(userRepository, userRoleRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserMapsVerifiedJwtPrincipalWithoutDatabaseQueries() {
        AuthenticatedUser principal = new AuthenticatedUser(17L,
                Set.of("LEARNER", "TEACHER"), true, "jwt-id", null, Instant.parse("2026-10-21T00:00:00Z"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        CurrentUser currentUser = authApi.currentUser();

        assertThat(currentUser.userId()).isEqualTo(17L);
        assertThat(currentUser.roles()).containsExactlyInAnyOrder(RoleCode.LEARNER, RoleCode.TEACHER);
        assertThat(currentUser.emailVerified()).isTrue();
        verifyNoInteractions(userRepository, userRoleRepository);
    }

    @Test
    void currentUserWithoutPrincipalThrowsUnauthenticated() {
        assertApiFailure(() -> authApi.currentUser(), ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requireVerifiedEmailAllowsVerifiedUser() {
        User user = new User("verified@example.test", "hash", "Verified");
        user.setEmailVerifiedAt(Instant.parse("2026-10-21T00:00:00Z"));
        when(userRepository.findById(17L)).thenReturn(Optional.of(user));

        authApi.requireVerifiedEmail(17L);

        verify(userRepository).findById(17L);
    }

    @Test
    void requireVerifiedEmailRejectsUnverifiedUserWithAccountUnverified() {
        when(userRepository.findById(17L))
                .thenReturn(Optional.of(new User("unverified@example.test", "hash", "Unverified")));

        assertApiFailure(() -> authApi.requireVerifiedEmail(17L),
                ErrorCode.ACCOUNT_UNVERIFIED, HttpStatus.FORBIDDEN);
    }

    @Test
    void requireVerifiedEmailTreatsMissingUserAsUnauthenticated() {
        when(userRepository.findById(17L)).thenReturn(Optional.empty());

        assertApiFailure(() -> authApi.requireVerifiedEmail(17L),
                ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void hasRoleChecksAnyUserThroughRoleRepository() {
        when(userRoleRepository.existsByUserIdAndRoleCode(99L,
                com.cnhsk.auth.entity.RoleCode.TEACHER)).thenReturn(true);

        assertThat(authApi.hasRole(99L, RoleCode.TEACHER)).isTrue();
        verify(userRoleRepository).existsByUserIdAndRoleCode(99L,
                com.cnhsk.auth.entity.RoleCode.TEACHER);
    }

    @Test
    void hasRoleReturnsFalseWhenRoleIsAbsent() {
        when(userRoleRepository.existsByUserIdAndRoleCode(99L,
                com.cnhsk.auth.entity.RoleCode.ADMIN)).thenReturn(false);

        assertThat(authApi.hasRole(99L, RoleCode.ADMIN)).isFalse();
    }

    @Test
    void hasRoleRejectsNullRole() {
        assertThatThrownBy(() -> authApi.hasRole(99L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role must not be null.");
    }

    @Test
    void displayNamesUsesOneLookupAndOmitsUnknownIds() {
        Set<Long> ids = Set.of(1L, 2L, 3L);
        User first = mock(User.class);
        when(first.getId()).thenReturn(1L);
        when(first.getDisplayName()).thenReturn("First");
        User second = mock(User.class);
        when(second.getId()).thenReturn(2L);
        when(second.getDisplayName()).thenReturn("Second");
        when(userRepository.findAllById(ids)).thenReturn(List.of(first, second));

        assertThat(authApi.displayNames(ids)).containsExactlyInAnyOrderEntriesOf(
                Map.of(1L, "First", 2L, "Second"));
        verify(userRepository).findAllById(ids);
    }

    @Test
    void displayNamesForEmptySetReturnsEmptyMapWithoutQuery() {
        assertThat(authApi.displayNames(Set.of())).isEmpty();

        verifyNoInteractions(userRepository);
    }

    private static void assertApiFailure(Runnable invocation, ErrorCode code, HttpStatus status) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getCode()).isEqualTo(code);
                    assertThat(apiException.getStatus()).isEqualTo(status);
                });
    }
}
