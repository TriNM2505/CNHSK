package com.cnhsk.learning.grading.web;

import java.util.Map;

import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class GradingRequestWriteTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "null", "[]", "{}", "{", "{} {}", "42", "\"essay\"",
            "{\"essay\":null}", "{\"essay\":true}", "{\"essay\":50}", "{\"essay\":[]}",
            "{\"essay\":\"字\",\"prompt\":3}", "{\"essay\":\"字\",\"hskLevel\":0}",
            "{\"essay\":\"字\",\"hskLevel\":10}", "{\"essay\":\"字\",\"hskLevel\":1.5}",
            "{\"essay\":\"字\",\"hskLevel\":\"1\"}", "{\"essay\":\"字\",\"hskLevel\":2147483648}"})
    void Q_T02_02_invalidStructureAndTypesAre422(String body) {
        assertThatThrownBy(() -> GradingRequestWrite.parse(body, mapper)).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(error.getStatus().value()).isEqualTo(422);
            assertThat(error.getCause()).isNull();
        });
    }

    @Test
    void FR002_promptCountsCodePointsAndEssayLengthIsLeftToEssayRules() throws Exception {
        String prompt = "😀".repeat(500);
        var write = GradingRequestWrite.parse(mapper.writeValueAsString(Map.of("essay", " \n字\n字 \t", "prompt", prompt, "hskLevel", 9)), mapper);
        assertThat(write.essay()).isEqualTo(" \n字\n字 \t");
        assertThat(write.prompt()).isEqualTo(prompt); assertThat(write.hskLevel()).isEqualTo(9);
        String invalid = mapper.writeValueAsString(Map.of("essay", "字", "prompt", prompt + "😀"));
        assertThatThrownBy(() -> GradingRequestWrite.parse(invalid, mapper)).isInstanceOf(ApiException.class);
        assertThat(GradingRequestWrite.parse("{\"essay\":\"\",\"prompt\":null,\"hskLevel\":null}", mapper).essay()).isEmpty();
        assertThat(write.toString()).doesNotContain("字", "😀");
    }

    @Test
    void FR003_serverManagedFieldsAreNotReadFromTheClient() {
        var write = GradingRequestWrite.parse("{\"essay\":\"字\",\"userId\":123,\"status\":\"COMPLETED\",\"consumption\":{\"source\":\"CREDIT\"}}", mapper);
        assertThat(write).isEqualTo(new GradingRequestWrite("字", null, null));
    }
}
