package com.cnhsk.community.moderation.web;

import java.io.IOException;
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

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = RejectWrite.Deserializer.class)
public record RejectWrite(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 1_000) String reason) {
    public RejectWrite { if (reason != null) reason = reason.strip(); }

    @AssertTrue(message = "Lý do phải có từ 1 đến 1000 ký tự.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isReasonValid() {
        return reason == null || (!reason.isBlank() && reason.codePointCount(0, reason.length()) <= 1_000);
    }

    public static final class Deserializer extends JsonDeserializer<RejectWrite> {
        @Override
        public RejectWrite deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = parser.getCodec().readTree(parser);
            if (!body.isObject() || parser.nextToken() != null) return invalid(context);
            JsonNode reason = body.path("reason");
            if (reason.isMissingNode() || reason.isNull()) return new RejectWrite(null);
            if (!reason.isTextual()) return invalid(context);
            return new RejectWrite(reason.textValue());
        }

        private static RejectWrite invalid(DeserializationContext context) throws IOException {
            return context.reportInputMismatch(RejectWrite.class, "Dữ liệu yêu cầu không hợp lệ.");
        }
    }
}
