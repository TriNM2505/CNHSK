package com.cnhsk.learning.knowledge.web;

import java.util.List;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tree review data for content editors; access is restricted to CONTENT_EDITOR in SecurityConfig. */
@RestController
@RequestMapping(value = "/api/admin/knowledge-tree", produces = MediaType.APPLICATION_JSON_VALUE)
public class KnowledgeTreeAdminController {

    private final KnowledgeTreeService tree;

    public KnowledgeTreeAdminController(KnowledgeTreeService tree) {
        this.tree = tree;
    }

    @GetMapping("/question-counts")
    @Operation(operationId = "getKnowledgeQuestionCounts", tags = "knowledge-tree")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Số câu hỏi theo từng nút"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ CONTENT_EDITOR")
    })
    public KnowledgeQuestionCounts questionCounts() {
        return new KnowledgeQuestionCounts(tree.questionCounts().stream()
                .map(count -> new KnowledgeQuestionCount(count.kid(), count.total(), count.approved()))
                .toList());
    }

    @Schema(name = "KnowledgeQuestionCounts")
    public record KnowledgeQuestionCounts(List<KnowledgeQuestionCount> counts) { }

    @Schema(name = "KnowledgeQuestionCount", requiredProperties = {"kid", "total", "approved"})
    public record KnowledgeQuestionCount(String kid,
                                         @Schema(minimum = "0", description = "Câu chưa lưu trữ có nhãn PRIMARY là nút này")
                                         long total,
                                         @Schema(minimum = "0", description = "Trong đó đã duyệt (APPROVED)")
                                         long approved) { }
}
