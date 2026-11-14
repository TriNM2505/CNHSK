package com.cnhsk.learning.credit.web;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.cnhsk.learning.api.PaidFeature;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(name = "PlanUpdateRequest")
public record PlanUpdateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100) String name,
        @Schema(types = {"string", "null"}, maxLength = 2000) String description,
        @Schema(type = "integer", types = {"integer", "null"}, minimum = "1", maximum = "365",
                description = "Bắt buộc với PREMIUM") Integer durationDays,
        @Schema(types = {"string", "null"}, pattern = "^[0-9]{1,10}(\\.[0-9]{1,2})?$",
                description = "Bắt buộc với PREMIUM") String price,
        @Schema(implementation = Benefits.CatalogSchema.class) Benefits benefits,
        @Schema(type = "boolean", types = {"boolean", "null"}, description = "Bắt buộc với PREMIUM") Boolean isActive) {
    private static final Pattern PRICE = Pattern.compile("^[0-9]{1,10}(\\.[0-9]{1,2})?$");
    private static final Set<String> FEATURES = Set.of(PaidFeature.AI_GENERATE.name(), PaidFeature.TRANSLATE.name(),
            PaidFeature.ASSISTANT.name(), PaidFeature.GRADING.name());

    public PlanUpdateRequest {
        name = name == null || name.indexOf('\0') >= 0 ? name : name.trim();
        benefits = benefits == null ? null : new Benefits(benefits);
    }

    public static PlanUpdateRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid();
        PlanUpdateRequest request = new PlanUpdateRequest(text(body, "name"), text(body, "description"),
                integer(body, "durationDays"), text(body, "price"), benefits(body), bool(body, "isActive"));
        request.validateFields();
        return request;
    }

    public static PlanUpdateRequest parse(JsonNode body, PlanStatus.PlanCode code) {
        if (code == null || body == null || !body.isObject()) throw invalid();
        if (code != PlanStatus.PlanCode.FREE) return parse(body);
        text(body, "price");
        bool(body, "isActive");
        JsonNode duration = body.get("durationDays");
        if (duration != null && !duration.isNull() && !duration.isIntegralNumber()) throw invalid();
        JsonNode limits = body.get("benefits");
        if (limits != null && !limits.isNull()) {
            if (!limits.isObject()) throw invalid();
            for (JsonNode limit : limits) if (!limit.isIntegralNumber()) throw invalid();
        }
        PlanUpdateRequest request = new PlanUpdateRequest(text(body, "name"), text(body, "description"),
                null, null, null, null);
        request.validate(code);
        return request;
    }

    public void validate(PlanStatus.PlanCode code) {
        if (code == PlanStatus.PlanCode.FREE) {
            validateEditableFields();
            return;
        }
        validateFields();
        if (code == null || (code == PlanStatus.PlanCode.PREMIUM
                && (durationDays == null || price == null || benefits == null || isActive == null))) throw invalid();
    }

    private void validateFields() {
        validateEditableFields();
        if ((durationDays != null && (durationDays < 1 || durationDays > 365))
                || (price != null && !PRICE.matcher(price).matches())) throw invalid();
        if (benefits != null) {
            for (Map.Entry<String, Integer> benefit : benefits.entrySet()) {
                Integer value = benefit.getValue();
                if (benefit.getKey() == null || !FEATURES.contains(benefit.getKey())
                        || value == null || value < 0 || value > 10_000) throw invalid();
            }
        }
    }

    private void validateEditableFields() {
        if (name == null || name.isBlank() || invalidText(name, 100) || invalidText(description, 2000)) throw invalid();
    }

    private static boolean invalidText(String value, int max) {
        return value != null && (value.indexOf('\0') >= 0 || value.codePointCount(0, value.length()) > max);
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw invalid();
        return value.textValue();
    }

    private static Integer integer(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw invalid();
        return value.intValue();
    }

    private static Boolean bool(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isBoolean()) throw invalid();
        return value.booleanValue();
    }

    private static Benefits benefits(JsonNode body) {
        JsonNode value = body.get("benefits");
        if (value == null || value.isNull()) return null;
        if (!value.isObject()) throw invalid();
        Benefits result = new Benefits();
        value.fields().forEachRemaining(entry -> {
            JsonNode limit = entry.getValue();
            if (!FEATURES.contains(entry.getKey()) || !limit.isIntegralNumber() || !limit.canConvertToInt()) throw invalid();
            result.put(entry.getKey(), limit.intValue());
        });
        return result;
    }

    public static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of());
    }
}
