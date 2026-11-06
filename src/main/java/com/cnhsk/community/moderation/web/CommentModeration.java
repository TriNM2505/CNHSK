package com.cnhsk.community.moderation.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
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
import org.springframework.http.HttpStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = CommentModeration.Deserializer.class)
public record CommentModeration(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 1_000) String reason) {
    public CommentModeration { if (reason != null) reason = reason.strip(); }

    public enum Action { HIDE, DELETE, RESTORE }

    @AssertTrue(message = "Lý do phải có từ 1 đến 1000 ký tự và không dùng dấu xoá của tác giả.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isReasonValid() {
        return reason == null || (!reason.isBlank() && reason.codePointCount(0, reason.length()) <= 1_000
                && !"AUTHOR_DELETED".equals(reason));
    }

    public void validateIndependent() {
        if (action == null || reason == null || !isReasonValid()) {
            String message = "Thao tác và lý do kiểm duyệt không hợp lệ.";
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message,
                    Map.of("details", List.of(Map.of("field", "action/reason", "message", message))));
        }
    }

    public static final class Deserializer extends JsonDeserializer<CommentModeration> {
        @Override
        public CommentModeration deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = parser.getCodec().readTree(parser);
            if (!body.isObject() || parser.nextToken() != null) return invalid(context);
            JsonNode action = body.path("action");
            JsonNode reason = body.path("reason");
            if ((!action.isMissingNode() && !action.isNull() && !action.isTextual())
                    || (!reason.isMissingNode() && !reason.isNull() && !reason.isTextual())) return invalid(context);
            try {
                return new CommentModeration(action.isMissingNode() || action.isNull() ? null
                        : Action.valueOf(action.textValue()),
                        reason.isMissingNode() || reason.isNull() ? null : reason.textValue());
            } catch (IllegalArgumentException exception) {
                return invalid(context);
            }
        }

        private static CommentModeration invalid(DeserializationContext context) throws IOException {
            return context.reportInputMismatch(CommentModeration.class, "Dữ liệu yêu cầu không hợp lệ.");
        }
    }
}
