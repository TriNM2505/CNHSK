package com.cnhsk.learning.assistant.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.ai.ClaudeClient.ChatMessage;
import com.cnhsk.learning.ai.ClaudeClient.Role;
import com.cnhsk.learning.assistant.AssistantService;
import com.cnhsk.learning.assistant.AssistantService.AskCommand;
import com.cnhsk.learning.assistant.AssistantService.AskResult;
import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistant;
    private final AuthApi auth;
    private final ObjectReader jsonReader;

    public AssistantController(AssistantService assistant, AuthApi auth, ObjectMapper mapper) {
        this.assistant = assistant;
        this.auth = auth;
        this.jsonReader = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping("/ask")
    @Operation(operationId = "askAssistant", summary = "Hỏi trợ lý về cách dùng CNHSK")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Câu trả lời"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN · CSRF_TOKEN_INVALID · ASSISTANT_BLOCKED_DURING_TEST"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED"),
            @ApiResponse(responseCode = "429", description = "RATE_LIMITED kèm retryAfterSeconds"),
            @ApiResponse(responseCode = "503", description = "ASSISTANT_UNAVAILABLE")
    })
    public AskResult ask(@RequestBody(required = false) String body) {
        if (body == null) {
            throw validation("request");
        }
        AskCommand validated;
        try {
            validated = command(jsonReader.readTree(body));
        } catch (JsonProcessingException exception) {
            throw validation("request");
        }
        return assistant.ask(auth.currentUser().userId(), validated);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedBody() {
        return ResponseEntity.unprocessableEntity().body(Map.of(
                "error_code", ErrorCode.VALIDATION_FAILED.name(),
                "message", "Dữ liệu không hợp lệ.",
                "request_id", RequestIdFilter.currentRequestId(),
                "details", List.of(Map.of("field", "request", "message", "Dữ liệu không hợp lệ."))));
    }

    private AskCommand command(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw validation("request");
        }
        JsonNode questionNode = body.get("question");
        if (questionNode == null || !questionNode.isTextual()) {
            throw validation("question");
        }
        String question = questionNode.asText().strip();
        int questionLength = question.codePointCount(0, question.length());
        if (questionLength < 1 || questionLength > 2000) {
            throw validation("question");
        }

        List<ChatMessage> history = new ArrayList<>();
        JsonNode historyNode = body.get("history");
        if (historyNode != null) {
            if (!historyNode.isArray() || historyNode.size() > 10) {
                throw validation("history");
            }
            for (JsonNode turn : historyNode) {
                if (!turn.isObject() || !turn.path("role").isTextual() || !turn.path("text").isTextual()) {
                    throw validation("history");
                }
                String role = turn.path("role").asText();
                String text = turn.path("text").asText();
                int length = text.codePointCount(0, text.length());
                if (length < 1 || length > 4000
                        || (!"user".equals(role) && !"assistant".equals(role))) {
                    throw validation("history");
                }
                history.add(new ChatMessage("user".equals(role) ? Role.USER : Role.ASSISTANT, text));
            }
        }

        String pageContext = null;
        JsonNode pageNode = body.get("pageContext");
        if (pageNode != null && !pageNode.isNull()) {
            if (!pageNode.isTextual()) {
                throw validation("pageContext");
            }
            String value = pageNode.textValue();
            if (!value.matches("^SCR-0[0-7][0-9]$")) {
                throw validation("pageContext");
            }
            pageContext = value;
        }
        return new AskCommand(question, history, pageContext);
    }

    private static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details",
                        List.of(Map.of("field", field, "message", "Giá trị không hợp lệ."))));
    }
}
