package com.cnhsk.learning.vocab.web;

import static com.cnhsk.learning.vocab.TopicPrerequisiteModels.*;
import static com.cnhsk.learning.vocab.TopicPrerequisiteService.invalid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.cnhsk.learning.vocab.TopicPrerequisiteService;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/admin/topics", produces = MediaType.APPLICATION_JSON_VALUE)
public class TopicAdminController {
    private final TopicPrerequisiteService service;
    private final ObjectMapper mapper;

    public TopicAdminController(TopicPrerequisiteService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(operationId = "adminListTopics", tags = "topic-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Chủ đề và tiên quyết"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public TopicPage list(
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "9")) String hsk,
            @RequestParam(required = false) @Parameter(schema = @Schema(maxLength = 100)) String q,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "0", defaultValue = "0")) String page,
            @RequestParam(required = false) @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "50", defaultValue = "20")) String size) {
        return service.list(hsk == null ? null : integer(hsk, "hsk"), q,
                page == null ? 0 : integer(page, "page"), size == null ? 20 : integer(size, "size"));
    }

    @PutMapping("/{id}/prerequisites")
    @Operation(operationId = "adminSetTopicPrerequisites", tags = "topic-admin")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Đã lưu tiên quyết"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN hoặc CSRF_TOKEN_INVALID"),
            @ApiResponse(responseCode = "404", description = "TOPIC_NOT_FOUND"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED, PREREQUISITE_CYCLE hoặc PREREQUISITE_NO_ROOT",
                    content = @Content(schema = @Schema(implementation = PrerequisiteError.class)))})
    public AdminTopic update(
            @PathVariable @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) String id,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String xsrf,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = PrerequisiteWrite.class)))
            @RequestBody(required = false) String body) {
        long topicId;
        try { topicId = Long.parseLong(id); }
        catch (NumberFormatException exception) { throw invalid("id"); }
        if (topicId <= 0) throw invalid("id");
        return service.setPrerequisites(topicId, user.userId(), ids(body));
    }

    private List<Long> ids(String body) {
        JsonNode node;
        try { node = body == null ? null : mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body); }
        catch (IOException exception) { throw invalid("body"); }
        if (node == null || !node.isObject()) throw invalid("body");
        JsonNode ids = node.path("prereqIds");
        if (!ids.isArray() || ids.size() > 10) throw invalid("prereqIds");
        List<Long> result = new ArrayList<>();
        for (JsonNode value : ids) {
            if (!value.isIntegralNumber() || !value.canConvertToLong()) throw invalid("prereqIds");
            long id = value.longValue();
            if (result.contains(id)) throw invalid("prereqIds");
            result.add(id);
        }
        return List.copyOf(result);
    }

    private static int integer(String value, String field) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw invalid(field); }
    }
}
