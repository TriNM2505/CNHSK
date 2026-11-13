package com.cnhsk.learning.reference;

import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ReferenceQueryServiceTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final ReferenceQueryService service = new ReferenceQueryService(jdbc);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"book", "OTHER", " BOOK "})
    void FR004_kindIsRequiredAndMustMatchTheContract(String kind) {
        invalid(() -> service.list(kind, null, null, 0, 20), "kind");
    }

    @Test
    void FR004_invalidFiltersAreRejectedBeforeDatabaseAccess() {
        invalid(() -> service.list("BOOK", 0, null, 0, 20), "hsk");
        invalid(() -> service.list("BOOK", 10, null, 0, 20), "hsk");
        invalid(() -> service.list("BOOK", null, "a".repeat(31), 0, 20), "tag");
        invalid(() -> service.list("BOOK", null, "😀".repeat(31), 0, 20), "tag");
        invalid(() -> service.list("BOOK", null, null, -1, 20), "page");
        invalid(() -> service.list("BOOK", null, null, 0, 0), "size");
        invalid(() -> service.list("BOOK", null, null, 0, 51), "size");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "abc", "1.5", " 1", "+1", "9223372036854775808"})
    void FR006_invalidIdsReturnNotFoundWithoutDatabaseAccess(String id) {
        assertThatThrownBy(() -> service.detail(id)).isInstanceOfSatisfying(ApiException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.getCode())
                        .isEqualTo(ErrorCode.REFERENCE_NOT_FOUND));
        verifyNoInteractions(jdbc);
    }

    private void invalid(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, error -> {
            org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            org.assertj.core.api.Assertions.assertThat(error.getExtra()).containsEntry("details",
                    java.util.List.of(Map.of("field", field, "message", "Tham số yêu cầu không hợp lệ.")));
        });
        verifyNoInteractions(jdbc);
    }
}
