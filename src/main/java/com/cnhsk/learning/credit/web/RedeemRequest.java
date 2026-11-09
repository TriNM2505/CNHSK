package com.cnhsk.learning.credit.web;

import java.util.Map;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

public record RedeemRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 40,
                description = "Không ghi log; chuẩn hoá ở server") String code) {
    public static RedeemRequest parse(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("code")
                || !body.path("code").isTextual()) throw invalid();
        RedeemRequest request = new RedeemRequest(body.path("code").textValue());
        request.validate();
        return request;
    }

    public void validate() {
        if (code == null || code.codePointCount(0, code.length()) > 40) throw invalid();
    }

    public static ApiException invalid() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of());
    }

    @Override public String toString() { return "RedeemRequest[redacted]"; }
}
