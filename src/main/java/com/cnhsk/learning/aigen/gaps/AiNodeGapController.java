package com.cnhsk.learning.aigen.gaps;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Knowledge nodes the generation job always skips (spec 024 E4): no can-do statement, or no word linked to
 * the node. Content editors use it to see which nodes need data before AI can write questions for them.
 */
@RestController
public class AiNodeGapController {
    private final JdbcTemplate jdbc;

    public AiNodeGapController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/api/admin/ai-node-gaps")
    @Operation(operationId = "listAiNodeGaps", tags = "ai-node-gaps")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @ApiResponse(responseCode = "403", description = "Chỉ CONTENT_EDITOR, TEACHER")
    })
    @Transactional(readOnly = true)
    public NodeGaps list() {
        List<NodeGap> nodes = jdbc.query("""
                SELECT kp.code, kp.title, kp.hsk_level,
                       (kp.can_do IS NULL OR btrim(kp.can_do, E' \\t\\r\\n') = '') AS missing_can_do,
                       (SELECT count(*) FROM learning.lexemes l WHERE l.knowledge_point_id = kp.id) AS lexemes
                FROM learning.knowledge_points kp
                WHERE kp.can_do IS NULL OR btrim(kp.can_do, E' \\t\\r\\n') = ''
                   OR NOT EXISTS (SELECT 1 FROM learning.lexemes l WHERE l.knowledge_point_id = kp.id)
                ORDER BY kp.code
                """, (rows, index) -> new NodeGap(rows.getString("code"), rows.getString("title"),
                rows.getInt("hsk_level"), rows.getBoolean("missing_can_do"), rows.getLong("lexemes")));
        Long total = jdbc.queryForObject("SELECT count(*) FROM learning.knowledge_points", Long.class);
        return new NodeGaps(total == null ? 0 : total, nodes);
    }

    public record NodeGaps(long totalNodes, List<NodeGap> nodes) { }

    /** {@code lexemeCount} counts words linked directly to the node, the same rule the job applies. */
    public record NodeGap(String kid, String nameVi, int hskLevel, boolean missingCanDo, long lexemeCount) { }
}
