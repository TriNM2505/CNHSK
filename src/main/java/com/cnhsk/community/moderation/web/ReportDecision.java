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
@JsonDeserialize(using = ReportDecision.Deserializer.class)
public record ReportDecision(
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Decision decision,
        @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 1_000) String note,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) CommentAction commentAction) {
    public ReportDecision { if (note != null) note = note.strip(); }

    public enum Decision { ACTION, REJECT }
    public enum CommentAction { HIDE, DELETE }

    @AssertTrue(message = "Ghi chú phải có từ 1 đến 1000 ký tự.")
    @JsonIgnore
    @Schema(hidden = true)
    public boolean isNoteValid() {
        return note == null || (!note.isBlank() && note.codePointCount(0, note.length()) <= 1_000);
    }

    public void validateIndependent() {
        if (decision == null || note == null || !isNoteValid()) {
            String message = "Quyết định và ghi chú không hợp lệ.";
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message,
                    Map.of("details", List.of(Map.of("field", "decision/note", "message", message))));
        }
    }

    public static final class Deserializer extends JsonDeserializer<ReportDecision> {
        @Override
        public ReportDecision deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = parser.getCodec().readTree(parser);
            if (!body.isObject() || parser.nextToken() != null) return invalid(context);
            JsonNode decision = body.path("decision");
            JsonNode note = body.path("note");
            JsonNode action = body.path("commentAction");
            if ((!decision.isMissingNode() && !decision.isNull() && !decision.isTextual())
                    || (!note.isMissingNode() && !note.isNull() && !note.isTextual())
                    || (!action.isMissingNode() && !action.isTextual())) return invalid(context);
            try {
                return new ReportDecision(decision.isMissingNode() || decision.isNull() ? null : Decision.valueOf(decision.textValue()),
                        note.isMissingNode() || note.isNull() ? null : note.textValue(),
                        action.isMissingNode() ? null : CommentAction.valueOf(action.textValue()));
            } catch (IllegalArgumentException exception) {
                return invalid(context);
            }
        }

        private static ReportDecision invalid(DeserializationContext context) throws IOException {
            return context.reportInputMismatch(ReportDecision.class, "Dữ liệu yêu cầu không hợp lệ.");
        }
    }
}
