package com.cnhsk.learning.reference;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.reference.ReferenceAdminService.ReferenceInput;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReferenceAdminServiceTest {

    private final AuthApi auth = mock(AuthApi.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ReferenceUrlPolicy policy = mock(ReferenceUrlPolicy.class);
    private final AuditService audit = mock(AuditService.class);
    private final ReferenceAdminService service = new ReferenceAdminService(auth, jdbc, policy, audit,
            Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void editor() {
        when(auth.currentUser()).thenReturn(new CurrentUser(7, Set.of(RoleCode.CONTENT_EDITOR), false));
        when(policy.validate(any(), anyString(), any())).thenReturn(Optional.empty());
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = "CONTENT_EDITOR", mode = EnumSource.Mode.EXCLUDE)
    void FR003_AT15_nonEditorsIncludingAdminCannotUseTheService(RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(7, Set.of(role), true));
        error(() -> service.list(null, null, null, 0, 20), ErrorCode.FORBIDDEN);
        error(() -> service.create(input("Demo", "https://fahasa.com/demo")), ErrorCode.FORBIDDEN);
        verifyNoInteractions(jdbc, audit, policy);
    }

    @Test
    void FR011_invalidFiltersAreRejectedBeforeQuerying() {
        error(() -> service.list("OTHER", null, null, 0, 20), ErrorCode.VALIDATION_FAILED);
        error(() -> service.list(null, "OTHER", null, 0, 20), ErrorCode.VALIDATION_FAILED);
        error(() -> service.list(null, null, null, -1, 20), ErrorCode.VALIDATION_FAILED);
        error(() -> service.list(null, null, null, 0, 0), ErrorCode.VALIDATION_FAILED);
        error(() -> service.list(null, null, null, 0, 51), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(jdbc, audit);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void FR012_invalidIdIs404BeforeDatabaseAccess(String id) {
        error(() -> service.detail(id), ErrorCode.REFERENCE_NOT_FOUND);
        verifyNoInteractions(jdbc, audit);
    }

    @Test
    void FR012_requiredAndLengthValidationDoesNotWriteAnything() {
        error(() -> service.create(null), ErrorCode.VALIDATION_FAILED);
        error(() -> service.create(input(" ", "https://fahasa.com/demo")), ErrorCode.VALIDATION_FAILED);
        error(() -> service.create(input("😀".repeat(201), "https://fahasa.com/demo")), ErrorCode.VALIDATION_FAILED);
        error(() -> service.create(input("Demo", null)), ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(jdbc, audit, policy);
    }

    @ParameterizedTest
    @EnumSource(ReferenceUrlPolicy.Reason.class)
    void FR015_policyReasonAndFieldAreMappedWithoutLeakingTheUrl(ReferenceUrlPolicy.Reason reason) {
        when(policy.validate(any(), anyString(), any())).thenReturn(Optional.of(
                new ReferenceUrlPolicy.Violation("thumbnailUrl", reason)));
        assertThatThrownBy(() -> service.create(input("Demo", "https://fahasa.com/demo")))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.valueOf(reason.name()));
                    assertThat(error.getMessage()).doesNotContain("fahasa.com");
                    assertThat(error.getExtra().toString()).contains("thumbnailUrl").doesNotContain("fahasa.com");
                });
        verifyNoInteractions(jdbc, audit);
    }

    private ReferenceInput input(String title, String url) {
        return new ReferenceInput(ReferenceItem.Kind.BOOK, title, null, url, null, null, null,
                List.of(), 0, null, null, null, false);
    }

    private void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
