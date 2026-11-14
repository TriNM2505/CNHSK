package com.cnhsk.learning.grading.web;

import java.util.ArrayList;
import java.util.List;

import com.cnhsk.learning.grading.web.GradingRequestViews.Criteria;
import com.cnhsk.learning.grading.web.GradingRequestViews.Correction;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class GradingResultWriteTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "null", "[]", "{}", "{", "{} {}", "42", "\"result\""})
    void AT11_invalidJsonAndTopLevelTypesAre422WithoutRetainingContent(String body) {
        invalid(() -> GradingResultWrite.parse(body, mapper));
    }

    @Test void AT11_allFourCriteriaAreRequiredIntegersFromZeroToTwentyFive() throws Exception {
        for (String field : List.of("grammar", "vocabulary", "coherence", "writing")) {
            for (String value : List.of("null", "true", "\"20\"", "20.0", "-1", "26", "2147483648", "{}", "[]")) {
                var root = valid(); ((ObjectNode) root.get("criteria")).set(field, mapper.readTree(value));
                invalid(() -> GradingResultWrite.parse(root.toString(), mapper));
            }
            var root = valid(); ((ObjectNode) root.get("criteria")).remove(field);
            invalid(() -> GradingResultWrite.parse(root.toString(), mapper));
        }
        assertThat(new GradingResultWrite(new Criteria(0, 25, 0, 25), "Đúng biên", List.of()).criteria())
                .isEqualTo(new Criteria(0, 25, 0, 25));
        invalid(() -> new GradingResultWrite(null, "Nhận xét", List.of()));
        invalid(() -> new GradingResultWrite(new Criteria(0, 26, 0, 0), "Nhận xét", List.of()));
    }

    @Test void AT11_missingOrWrongTypesForRequiredFieldsAreRejected() throws Exception {
        for (String field : List.of("criteria", "feedback", "corrections")) {
            var missing = valid(); missing.remove(field); invalid(() -> GradingResultWrite.parse(missing.toString(), mapper));
            for (String value : List.of("null", "true", "23")) {
                var wrong = valid(); wrong.set(field, mapper.readTree(value));
                invalid(() -> GradingResultWrite.parse(wrong.toString(), mapper));
            }
        }
        for (String value : List.of("null", "true", "23", "\"correction\"", "[]", "{}",
                "{\"original\":null,\"corrected\":\"字\"}", "{\"original\":\"字\",\"corrected\":1}",
                "{\"original\":\"字\",\"corrected\":\"词\",\"note\":false}")) {
            var root = valid(); root.withArray("corrections").add(mapper.readTree(value));
            invalid(() -> GradingResultWrite.parse(root.toString(), mapper));
        }
    }

    @Test void Q_T04_03_unicodeWhitespaceOnlyIsInvalidButValidTextIsNotTrimmed() {
        var criteria = new Criteria(1, 2, 3, 4);
        for (String blank : List.of("", " \t\n", "\u00a0\u3000\u2003")) {
            invalid(() -> new GradingResultWrite(criteria, blank, List.of()));
            invalid(() -> new GradingResultWrite(criteria, "Nhận xét", List.of(new Correction(blank, "词", null))));
            invalid(() -> new GradingResultWrite(criteria, "Nhận xét", List.of(new Correction("字", blank, null))));
        }
        String text = " \nNhận xét 😀\t ";
        var corrections = List.of(new Correction(" 字 \n", " 词\t", ""), new Correction("字", "词", null));
        var write = new GradingResultWrite(criteria, text, corrections);
        assertThat(write.feedback()).isEqualTo(text); assertThat(write.corrections()).isEqualTo(corrections);
        assertThat(write.toString()).doesNotContain(text, "Nhận xét", "字", "词");
    }

    @Test void AT11_lengthsCountCodePointsOnTheOriginalStringsAndCorrectionsAreImmutable() {
        var criteria = new Criteria(25, 25, 25, 25);
        String feedback = "😀".repeat(5000); String correction = "😀".repeat(500);
        var source = new ArrayList<>(List.of(new Correction(correction, correction, correction)));
        var write = new GradingResultWrite(criteria, feedback, source); source.clear();
        assertThat(write.feedback()).isEqualTo(feedback); assertThat(write.corrections()).hasSize(1);
        assertThatThrownBy(() -> write.corrections().clear()).isInstanceOf(UnsupportedOperationException.class);
        invalid(() -> new GradingResultWrite(criteria, feedback + "😀", List.of()));
        invalid(() -> new GradingResultWrite(criteria, "字" + " ".repeat(5000), List.of()));
        invalid(() -> new GradingResultWrite(criteria, "Nhận xét", List.of(new Correction(correction + "😀", "字", null))));
        invalid(() -> new GradingResultWrite(criteria, "Nhận xét", List.of(new Correction("字", correction + "😀", null))));
        invalid(() -> new GradingResultWrite(criteria, "Nhận xét", List.of(new Correction("字", "词", correction + "😀"))));
        assertThat(new GradingResultWrite(criteria, "Nhận xét", java.util.Collections.nCopies(50, new Correction("字", "词", null)))
                .corrections()).hasSize(50);
        invalid(() -> new GradingResultWrite(criteria, "Nhận xét", java.util.Collections.nCopies(51, new Correction("字", "词", null))));
        invalid(() -> new GradingResultWrite(criteria, "Nhận xét", null));
        invalid(() -> new GradingResultWrite(criteria, "Nhận xét", java.util.Collections.singletonList(null)));
    }

    @Test void Q_T04_02_serverManagedAndOtherExtraFieldsAreIgnored() {
        var root = valid().put("score", 999).put("payout", 999).put("status", "EXPIRED").put("userId", 999);
        ((ObjectNode) root.get("criteria")).put("extra", 999);
        var write = GradingResultWrite.parse(root.toString(), mapper);
        assertThat(write).isEqualTo(new GradingResultWrite(new Criteria(20, 18, 21, 19), "Nhận xét tự soạn", List.of()));
    }

    private ObjectNode valid() {
        var root = mapper.createObjectNode();
        root.set("criteria", mapper.valueToTree(new Criteria(20, 18, 21, 19)));
        root.put("feedback", "Nhận xét tự soạn"); root.putArray("corrections"); return root;
    }
    private void invalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.GRADING_INVALID_RESULT);
            assertThat(error.getStatus().value()).isEqualTo(422); assertThat(error.getCause()).isNull();
            assertThat(error.getMessage()).doesNotContain("😀", "字", "词", "Nhận xét tự soạn");
        });
    }
}
