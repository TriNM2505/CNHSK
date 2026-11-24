package com.cnhsk.learning.grading.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cnhsk.learning.grading.Rubric;
import com.cnhsk.shared.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GradingResultWriteRubricTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyGradesAreStillAccepted() {
        var write = GradingResultWrite.parse("""
                {"criteria":{"grammar":20,"vocabulary":18,"coherence":15,"writing":22},
                 "feedback":"Tốt","corrections":[]}""", mapper);
        assertThat(write.rubric()).isNull();
        assertThat(write.criteria().grammar()).isEqualTo(20);
        assertThat(write.errors()).isEmpty();
    }

    @Test
    void speakingRubricWithErrorCodesIsParsed() {
        var write = GradingResultWrite.parse("""
                {"rubric":"R_NOI","scores":{"C1":2,"C2":2,"C3":3,"C4":1,"C5":2},
                 "errors":["E-NP-06","E-NL-01"],"feedback":"Cần dùng 了 đúng chỗ","corrections":[]}""", mapper);
        assertThat(write.rubric()).isEqualTo(Rubric.R_NOI);
        assertThat(write.scores()).containsEntry("C3", 3).hasSize(5);
        assertThat(write.errors()).containsExactly("E-NP-06", "E-NL-01");
        assertThat(write.criteria()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"rubric\":\"R_NOI\",\"scores\":{\"C1\":4,\"C2\":2,\"C3\":2,\"C4\":2,\"C5\":2},\"feedback\":\"x\",\"corrections\":[]}",
            "{\"rubric\":\"R_VIET\",\"scores\":{\"C1\":4,\"C2\":2,\"C3\":2},\"feedback\":\"x\",\"corrections\":[]}",
            "{\"rubric\":\"R_VIET\",\"scores\":{\"C1\":4,\"C2\":2,\"C3\":2,\"C4\":1,\"C5\":1},\"feedback\":\"x\",\"corrections\":[]}",
            "{\"rubric\":\"LEGACY\",\"scores\":{},\"feedback\":\"x\",\"corrections\":[]}",
            "{\"rubric\":\"R_VIET\",\"scores\":{\"C1\":4,\"C2\":2,\"C3\":2,\"C4\":1},\"errors\":[\"NP-06\"],\"feedback\":\"x\",\"corrections\":[]}",
            "{\"rubric\":\"R_VIET\",\"scores\":{\"C1\":4,\"C2\":2,\"C3\":2,\"C4\":1},\"errors\":[\"E-NP-06\",\"E-NP-06\"],\"feedback\":\"x\",\"corrections\":[]}",
            "{\"rubric\":\"R_VIET\",\"scores\":{\"C1\":\"4\",\"C2\":2,\"C3\":2,\"C4\":1},\"feedback\":\"x\",\"corrections\":[]}",
            "{\"rubric\":\"R_VIET\",\"criteria\":{\"grammar\":1,\"vocabulary\":1,\"coherence\":1,\"writing\":1},\"scores\":{\"C1\":4,\"C2\":2,\"C3\":2,\"C4\":1},\"feedback\":\"x\",\"corrections\":[]}"
    })
    void invalidRubricGradesAreRejected(String body) {
        assertThatThrownBy(() -> GradingResultWrite.parse(body, mapper)).isInstanceOf(ApiException.class);
    }
}
