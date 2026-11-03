package com.cnhsk.learning.knowledge.web;

import java.util.List;
import java.util.Map;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.KnowledgeNode;
import com.cnhsk.learning.knowledge.KnowledgeTreeService.LinkedTopic;
import com.cnhsk.learning.knowledge.entity.KnowledgeDomain;
import com.cnhsk.learning.knowledge.entity.KnowledgeSkillType;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/knowledge-tree", produces = MediaType.APPLICATION_JSON_VALUE)
public class KnowledgeTreeController {

    private final KnowledgeTreeService tree;

    public KnowledgeTreeController(KnowledgeTreeService tree) {
        this.tree = tree;
    }

    @GetMapping
    @Operation(operationId = "getKnowledgeTree", tags = "knowledge-tree")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cây năng lực"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER, CONTENT_EDITOR, TEACHER"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")
    })
    public KnowledgeTree list(
            @Parameter(schema = @Schema(implementation = KnowledgeDomain.class))
            @RequestParam(required = false) String domain,
            @Parameter(schema = @Schema(type = "integer", minimum = "1", maximum = "7"))
            @RequestParam(required = false) String hskLevel) {
        KnowledgeDomain requestedDomain = domain(domain);
        Integer requestedLevel = level(hskLevel);
        return new KnowledgeTree(tree.nodes().stream()
                .filter(node -> requestedDomain == null || node.domain() == requestedDomain)
                .filter(node -> requestedLevel == null || node.hskLevel() == requestedLevel)
                .map(KnowledgeTreeController::response).toList());
    }

    @GetMapping("/nodes/{kid}")
    @Operation(operationId = "getKnowledgeNode", tags = "knowledge-tree")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chi tiết nút năng lực"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ LEARNER, CONTENT_EDITOR, TEACHER"),
            @ApiResponse(responseCode = "404", description = "KNOWLEDGE_NODE_NOT_FOUND")
    })
    public KnowledgeNodeDetail detail(@PathVariable String kid) {
        var detail = tree.detail(kid);
        KnowledgeNode node = detail.node();
        return new KnowledgeNodeDetail(node.kid(), node.domain(), node.hskLevel(), node.nameVi(), node.nameZh(),
                node.canDo(), node.skillType(), node.prereqKids(), node.supportKids(), detail.dependentKids(),
                detail.topics(), detail.lexemeCount(), detail.approvedQuestionCount());
    }

    private static KnowledgeDomain domain(String value) {
        if (value == null) return null;
        try {
            return KnowledgeDomain.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw validation("domain");
        }
    }

    private static Integer level(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed >= 1 && parsed <= 7) return parsed;
        } catch (NumberFormatException exception) {
            throw validation("hskLevel");
        }
        throw validation("hskLevel");
    }

    private static ApiException validation(String field) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu không hợp lệ.", Map.of("details", List.of(Map.of(
                        "field", field, "message", "Giá trị không hợp lệ."))));
    }

    private static KnowledgeNodeResponse response(KnowledgeNode node) {
        return new KnowledgeNodeResponse(node.kid(), node.domain(), node.hskLevel(), node.nameVi(), node.nameZh(),
                node.canDo(), node.skillType(), node.prereqKids(), node.supportKids());
    }

    @Schema(name = "KnowledgeTree")
    public record KnowledgeTree(List<KnowledgeNodeResponse> nodes) { }

    @Schema(name = "KnowledgeNode")
    public record KnowledgeNodeResponse(String kid, KnowledgeDomain domain,
                                         @Schema(minimum = "1", maximum = "7") int hskLevel,
                                         String nameVi, String nameZh, String canDo, KnowledgeSkillType skillType,
                                         List<String> prereqKids, List<String> supportKids) { }

    @Schema(name = "KnowledgeNodeDetail")
    public record KnowledgeNodeDetail(String kid, KnowledgeDomain domain,
                                      @Schema(minimum = "1", maximum = "7") int hskLevel,
                                      String nameVi, String nameZh, String canDo, KnowledgeSkillType skillType,
                                      List<String> prereqKids, List<String> supportKids, List<String> dependentKids,
                                      List<LinkedTopic> topics, long lexemeCount, long approvedQuestionCount) { }
}
