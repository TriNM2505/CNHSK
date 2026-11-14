package com.cnhsk.learning.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.cnhsk.learning.credit.web.Benefits;
import com.cnhsk.learning.credit.web.PlanStatus.PlanCode;
import com.cnhsk.learning.credit.web.PlanUpdateRequest;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class PlanUpdateRequestTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test void freeNeedsOnlyTrimmedNameAndNullableDescription() throws Exception {
        for (String json : List.of("{\"name\":\"  Miễn phí  \"}",
                "{\"name\":\"  Miễn phí  \",\"description\":null}",
                "{\"name\":\"  Miễn phí  \",\"durationDays\":null,\"price\":null,\"benefits\":null,\"isActive\":null}")) {
            PlanUpdateRequest request = PlanUpdateRequest.parse(MAPPER.readTree(json), PlanCode.FREE);
            request.validate(PlanCode.FREE);
            assertThat(request.name()).isEqualTo("Miễn phí");
            assertThat(request.description()).isNull();
            assertThat(request.durationDays()).isNull();
            assertThat(request.price()).isNull();
            assertThat(request.benefits()).isNull();
            assertThat(request.isActive()).isNull();
        }
    }

    @Test void freeDiscardsSuppliedPremiumFieldsAndUnknownTopLevelFields() throws Exception {
        ObjectNode body = valid();
        body.put("unrecognized", "ignored");
        PlanUpdateRequest request = PlanUpdateRequest.parse(body, PlanCode.FREE);
        request.validate(PlanCode.FREE);
        assertThat(request.durationDays()).isNull();
        assertThat(request.price()).isNull();
        assertThat(request.benefits()).isNull();
        assertThat(request.isActive()).isNull();
    }

    @Test void freeIgnoresPremiumSemanticsAfterCheckingJsonTypes() {
        for (String price : List.of("abc", "", "-1", "1.000", "10000000000", "１２")) {
            ObjectNode body = valid().put("price", price).put("durationDays", Long.MAX_VALUE).put("isActive", false);
            body.set("benefits", MAPPER.createObjectNode().put("UNKNOWN", -1).put("TRANSLATE", Long.MAX_VALUE));
            PlanUpdateRequest request = PlanUpdateRequest.parse(body, PlanCode.FREE);
            request.validate(PlanCode.FREE);
            assertThat(request.name()).isEqualTo("Gói thử nghiệm");
            assertThat(request.durationDays()).isNull();
            assertThat(request.price()).isNull();
            assertThat(request.benefits()).isNull();
            assertThat(request.isActive()).isNull();
        }
        new PlanUpdateRequest("Free", null, -1, "abc", new Benefits(Map.of("UNKNOWN", -1)), false).validate(PlanCode.FREE);
    }

    @ParameterizedTest @ValueSource(strings = {"durationDays", "price", "benefits", "isActive"})
    void premiumRequiresEveryPremiumFieldPresentAndNonNull(String field) {
        ObjectNode absent = valid(); absent.remove(field);
        expectInvalid(() -> PlanUpdateRequest.parse(absent).validate(PlanCode.PREMIUM));
        ObjectNode explicitNull = valid(); explicitNull.putNull(field);
        expectInvalid(() -> PlanUpdateRequest.parse(explicitNull).validate(PlanCode.PREMIUM));
    }

    @Test void premiumAcceptsFalseZeroPriceSparseAndEmptyBenefitsAndBoundaryDays() {
        for (int days : List.of(1, 365)) {
            for (String price : List.of("0", "0.0", "0.00", "9999999999.99", "0000000000")) {
                ObjectNode body = valid().put("durationDays", days).put("price", price).put("isActive", false);
                body.set("benefits", MAPPER.createObjectNode());
                PlanUpdateRequest request = PlanUpdateRequest.parse(body);
                request.validate(PlanCode.PREMIUM);
                assertThat(request.benefits()).isEmpty();
                assertThat(request.isActive()).isFalse();
                assertThat(request.price()).isEqualTo(price);
            }
        }
        ObjectNode body = valid();
        body.set("benefits", MAPPER.createObjectNode().put("AI_GENERATE", 0).put("TRANSLATE", 10000)
                .put("ASSISTANT", 1).put("GRADING", 9999));
        PlanUpdateRequest request = PlanUpdateRequest.parse(body);
        request.validate(PlanCode.PREMIUM);
        assertThat(request.benefits()).containsExactlyInAnyOrderEntriesOf(Map.of("AI_GENERATE", 0, "TRANSLATE", 10000,
                "ASSISTANT", 1, "GRADING", 9999));
    }

    @Test void benefitObjectFieldOrderDoesNotChangeTheNamedLimits() {
        ObjectNode forward = valid();
        forward.set("benefits", MAPPER.createObjectNode().put("AI_GENERATE", 0).put("TRANSLATE", 10000)
                .put("ASSISTANT", 1).put("GRADING", 9999));
        ObjectNode reverse = valid();
        reverse.set("benefits", MAPPER.createObjectNode().put("GRADING", 9999).put("ASSISTANT", 1)
                .put("TRANSLATE", 10000).put("AI_GENERATE", 0));
        PlanUpdateRequest first = PlanUpdateRequest.parse(forward);
        PlanUpdateRequest second = PlanUpdateRequest.parse(reverse);
        first.validate(PlanCode.PREMIUM);
        second.validate(PlanCode.PREMIUM);
        assertThat(first.benefits()).containsExactlyInAnyOrderEntriesOf(second.benefits());
        JsonNode firstJson = MAPPER.valueToTree(first.benefits());
        JsonNode secondJson = MAPPER.valueToTree(second.benefits());
        assertThat(firstJson).isEqualTo(secondJson);
    }

    @ParameterizedTest @ValueSource(strings = {"", "-1", "+1", " 1", "1 ", ".1", "1.", "1.000", "0.000",
            "1e2", "1,00", "10000000000", "99999999999.99", "１２", "NaN"})
    void priceRemainsAPlainNonnegativeDecimalStringWithAtMostTenWholeDigits(String price) {
        ObjectNode body = valid().put("price", price);
        expectInvalid(() -> PlanUpdateRequest.parse(body, PlanCode.PREMIUM).validate(PlanCode.PREMIUM));
    }

    @ParameterizedTest @MethodSource("invalidNodes")
    void premiumRejectsWrongJsonTypesAndInvalidBoundsWithoutCoercion(String field, String json) throws Exception {
        ObjectNode body = valid(); body.set(field, MAPPER.readTree(json));
        expectInvalid(() -> PlanUpdateRequest.parse(body, PlanCode.PREMIUM).validate(PlanCode.PREMIUM));
    }

    @ParameterizedTest @MethodSource("freeWrongTypes")
    void freeStillRejectsWrongJsonTypesWithoutCoercion(String field, String json) throws Exception {
        ObjectNode body = valid(); body.set(field, MAPPER.readTree(json));
        expectInvalid(() -> PlanUpdateRequest.parse(body, PlanCode.FREE));
    }

    static Stream<Arguments> freeWrongTypes() {
        return Stream.of(Arguments.of("name", "null"), Arguments.of("name", "1"), Arguments.of("description", "false"),
                Arguments.of("durationDays", "\"30\""), Arguments.of("durationDays", "30.0"), Arguments.of("durationDays", "{}"),
                Arguments.of("price", "1"), Arguments.of("price", "false"), Arguments.of("price", "[]"),
                Arguments.of("isActive", "\"false\""), Arguments.of("isActive", "1"),
                Arguments.of("benefits", "[]"), Arguments.of("benefits", "1"), Arguments.of("benefits", "\"limits\""),
                Arguments.of("benefits", "{\"UNKNOWN\":null}"), Arguments.of("benefits", "{\"UNKNOWN\":\"1\"}"),
                Arguments.of("benefits", "{\"UNKNOWN\":true}"), Arguments.of("benefits", "{\"UNKNOWN\":1.0}"));
    }

    static Stream<Arguments> invalidNodes() {
        return Stream.of(
                Arguments.of("name", "null"), Arguments.of("name", "1"), Arguments.of("name", "true"),
                Arguments.of("name", "{}"), Arguments.of("name", "[]"),
                Arguments.of("description", "1"), Arguments.of("description", "false"), Arguments.of("description", "[]"),
                Arguments.of("durationDays", "\"30\""), Arguments.of("durationDays", "true"), Arguments.of("durationDays", "1.0"),
                Arguments.of("durationDays", "2147483648"), Arguments.of("durationDays", "-2147483649"),
                Arguments.of("durationDays", "0"), Arguments.of("durationDays", "366"), Arguments.of("durationDays", "{}"),
                Arguments.of("price", "1"), Arguments.of("price", "1.20"), Arguments.of("price", "false"), Arguments.of("price", "[]"),
                Arguments.of("isActive", "\"true\""), Arguments.of("isActive", "1"), Arguments.of("isActive", "{}"),
                Arguments.of("benefits", "[]"), Arguments.of("benefits", "1"), Arguments.of("benefits", "\"x\""),
                Arguments.of("benefits", "{\"UNKNOWN\":1}"), Arguments.of("benefits", "{\"translate\":1}"),
                Arguments.of("benefits", "{\"TRANSLATE\":null}"), Arguments.of("benefits", "{\"TRANSLATE\":\"1\"}"),
                Arguments.of("benefits", "{\"TRANSLATE\":true}"), Arguments.of("benefits", "{\"TRANSLATE\":1.0}"),
                Arguments.of("benefits", "{\"TRANSLATE\":2147483648}"), Arguments.of("benefits", "{\"TRANSLATE\":-1}"),
                Arguments.of("benefits", "{\"TRANSLATE\":10001}"));
    }

    @Test void namesAndDescriptionsUseUnicodeCodePointLimitsAndRejectNulAndBlankNames() {
        String supplementary = new String(Character.toChars(0x20000));
        PlanUpdateRequest accepted = PlanUpdateRequest.parse(valid().put("name", supplementary.repeat(100))
                .put("description", supplementary.repeat(2000)));
        accepted.validate(PlanCode.PREMIUM);
        assertThat(accepted.name().codePointCount(0, accepted.name().length())).isEqualTo(100);
        for (String name : List.of("", " \t\r\n ", "\u2003", "a\0b", "x".repeat(101), supplementary.repeat(101))) {
            expectInvalid(() -> PlanUpdateRequest.parse(valid().put("name", name)));
        }
        for (String description : List.of("a\0b", "x".repeat(2001), supplementary.repeat(2001))) {
            expectInvalid(() -> PlanUpdateRequest.parse(valid().put("description", description)));
        }
        ObjectNode missingName = valid(); missingName.remove("name");
        expectInvalid(() -> PlanUpdateRequest.parse(missingName));
    }

    @ParameterizedTest @ValueSource(strings = {"null", "[]", "1", "true", "\"text\""})
    void requestRootMustBeAnObject(String json) throws Exception {
        JsonNode body = MAPPER.readTree(json);
        expectInvalid(() -> PlanUpdateRequest.parse(body));
    }

    @Test void directCallValidationEnforcesTheSameRulesAndCopiesCallerBenefits() {
        Benefits original = new Benefits(Map.of("TRANSLATE", 10));
        PlanUpdateRequest request = new PlanUpdateRequest("  Gói  ", null, 30, "1.2", original, true);
        original.put("TRANSLATE", 99);
        request.validate(PlanCode.PREMIUM);
        assertThat(request.name()).isEqualTo("Gói");
        assertThat(request.benefits()).containsEntry("TRANSLATE", 10);
        expectInvalid(() -> request.validate(null));
        expectInvalid(() -> PlanUpdateRequest.parse(null));
        expectInvalid(() -> new PlanUpdateRequest("", null, 30, "1", new Benefits(), true).validate(PlanCode.PREMIUM));
        expectInvalid(() -> new PlanUpdateRequest("Gói", null, 30, "1", new Benefits(Map.of("UNKNOWN", 1)), true)
                .validate(PlanCode.PREMIUM));
    }

    private static ObjectNode valid() {
        ObjectNode body = MAPPER.createObjectNode().put("name", "Gói thử nghiệm").put("durationDays", 30)
                .put("price", "120000").put("isActive", true);
        body.set("benefits", MAPPER.createObjectNode().put("TRANSLATE", 10));
        return body;
    }

    private static void expectInvalid(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(exception.getMessage()).isEqualTo("Dữ liệu không hợp lệ.");
            assertThat(exception.getCause()).isNull();
        });
    }
}
