package com.cnhsk.learning.mastery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MasteryStateTest {

    @ParameterizedTest
    @MethodSource("stateCodes")
    void eachStateHasAnExplicitRoundTripCode(MasteryState state, String code) {
        assertThat(state.code()).isEqualTo(code);
        assertThat(MasteryState.fromCode(code)).isSameAs(state);
    }

    @Test
    void unknownCodeIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> MasteryState.fromCode("INFERRED"));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> stateCodes() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(MasteryState.NOT_YET, "notyet"),
                org.junit.jupiter.params.provider.Arguments.of(MasteryState.INSUFFICIENT, "insufficient"),
                org.junit.jupiter.params.provider.Arguments.of(MasteryState.REVIEW, "review"),
                org.junit.jupiter.params.provider.Arguments.of(MasteryState.CONSOLIDATE, "consolidate"),
                org.junit.jupiter.params.provider.Arguments.of(MasteryState.EXTEND, "extend"),
                org.junit.jupiter.params.provider.Arguments.of(MasteryState.INFERRED, "inferred"));
    }
}
