package com.cnhsk.community.moderation.web;

import java.io.IOException;

import com.cnhsk.community.common.ReportReason;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ReportWrite.Deserializer.class)
public record ReportWrite(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ReportTargetType targetType,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long targetId,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, enumAsRef = true) ReportReason reason,
        @Size(max = 1_000)
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, types = {"string", "null"}, maxLength = 1_000)
        String detail) {

    public ReportWrite {
        if (detail != null) {
            detail = detail.strip();
            if (detail.isEmpty()) detail = null;
        }
    }

    @AssertTrue(message = "Vui lòng ghi chi tiết khi chọn lý do khác.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isDetailValid() {
        return reason != ReportReason.OTHER || (detail != null && !detail.isBlank());
    }

    @Schema(name = "ReportTargetType", enumAsRef = true)
    public enum ReportTargetType { POST, COMMENT }

    /** Keeps scalar coercion local to reporting instead of changing the shared mapper. */
    public static final class Deserializer extends JsonDeserializer<ReportWrite> {
        @Override
        public ReportWrite deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = parser.getCodec().readTree(parser);
            if (!body.isObject() || parser.nextToken() != null) return invalid(context);
            JsonNode type = body.path("targetType");
            JsonNode id = body.path("targetId");
            JsonNode reason = body.path("reason");
            JsonNode detail = body.path("detail");
            if ((!absent(type) && !type.isTextual())
                    || (!absent(id) && (!id.isIntegralNumber() || !id.canConvertToLong()))
                    || (!absent(reason) && !reason.isTextual())
                    || (!absent(detail) && !detail.isTextual())) return invalid(context);
            try {
                return new ReportWrite(absent(type) ? null : ReportTargetType.valueOf(type.textValue()),
                        absent(id) ? null : id.longValue(),
                        absent(reason) ? null : ReportReason.valueOf(reason.textValue()),
                        absent(detail) ? null : detail.textValue());
            } catch (IllegalArgumentException invalidEnum) {
                return invalid(context);
            }
        }

        private static boolean absent(JsonNode value) {
            return value.isMissingNode() || value.isNull();
        }

        private static ReportWrite invalid(DeserializationContext context) throws IOException {
            return context.reportInputMismatch(ReportWrite.class, "Dữ liệu yêu cầu không hợp lệ.");
        }
    }
}
