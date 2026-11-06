package com.cnhsk.learning.assistant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.cnhsk.learning.api.ActiveTestApi;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestInProgressGuardTest {
    @Mock private ActiveTestApi activeTests;
    @InjectMocks private TestInProgressGuard guard;

    @Test
    void FR003_blocksOnlyWhenLearningApiReportsAnActiveTest() {
        Instant now = Instant.parse("2026-11-06T04:00:00Z");
        when(activeTests.hasActiveTest(77L, now)).thenReturn(true);

        assertThatThrownBy(() -> guard.check(77L, now))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    org.assertj.core.api.Assertions.assertThat(exception.getCode())
                            .isEqualTo(ErrorCode.ASSISTANT_BLOCKED_DURING_TEST);
                    org.assertj.core.api.Assertions.assertThat(exception.getStatus().value()).isEqualTo(403);
                });
        verify(activeTests).hasActiveTest(77L, now);
    }

    @Test
    void FR003_allowsWhenNoActiveTestExists() {
        Instant now = Instant.parse("2026-11-06T04:00:00Z");
        when(activeTests.hasActiveTest(77L, now)).thenReturn(false);
        guard.check(77L, now);
        verify(activeTests).hasActiveTest(77L, now);
    }
}
