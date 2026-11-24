package com.cnhsk.learning.mastery.skills;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The learner's competency by skill (listening, speaking, reading, writing) and by domain, for the nodes up to
 * the chosen HSK level (spec 5.11). A node counts as good in the consolidate or extend state; a node without
 * enough evidence is reported apart and never pulls the percentage down.
 */
@RestController
@Transactional(readOnly = true)
public class SkillMasteryController {

    static final List<String> SKILLS = List.of("LISTENING", "SPEAKING", "READING", "WRITING");
    static final List<String> DOMAINS = List.of("NA", "CH", "TV", "NP", "NL", "VB");

    private static final String BY_SKILL = """
            SELECT skill.code, count(*) AS nodes,
                   count(*) FILTER (WHERE progress.mp_state IN ('consolidate', 'extend')) AS good,
                   count(*) FILTER (WHERE progress.mp_state IS NULL OR progress.mp_state IN ('notyet', 'insufficient')) AS missing,
                   avg(progress.mastery) FILTER (WHERE progress.mp_state IN ('review', 'consolidate', 'extend')) AS mastery
            FROM learning.knowledge_points point
            CROSS JOIN LATERAL unnest(point.skills) AS skill(code)
            LEFT JOIN learning.user_progress progress ON progress.user_id = :userId
                 AND progress.target_type = 'KNOWLEDGE_POINT' AND progress.target_id = point.id
            WHERE point.hsk_level <= :level
            GROUP BY skill.code
            """;
    private static final String BY_DOMAIN = """
            SELECT point.domain, count(*) AS nodes,
                   count(*) FILTER (WHERE progress.mp_state IN ('consolidate', 'extend')) AS good,
                   count(*) FILTER (WHERE progress.mp_state IS NULL OR progress.mp_state IN ('notyet', 'insufficient')) AS missing,
                   avg(progress.mastery) FILTER (WHERE progress.mp_state IN ('review', 'consolidate', 'extend')) AS mastery
            FROM learning.knowledge_points point
            LEFT JOIN learning.user_progress progress ON progress.user_id = :userId
                 AND progress.target_type = 'KNOWLEDGE_POINT' AND progress.target_id = point.id
            WHERE point.hsk_level <= :level
            GROUP BY point.domain
            """;

    private final EntityManager entityManager;
    private final AuthApi auth;

    public SkillMasteryController(EntityManager entityManager, AuthApi auth) {
        this.entityManager = entityManager;
        this.auth = auth;
    }

    /** {@code percent} is null while no node of the group has enough evidence. */
    @Schema(name = "MasteryGroup")
    public record Group(String code, int nodes, int good, int missing, Integer percent) { }

    @Schema(name = "SkillMastery")
    public record SkillMastery(int hskLevel, List<Group> skills, List<Group> domains) { }

    @GetMapping("/api/mastery/skills")
    @Operation(operationId = "getMySkillMastery", tags = "mastery")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Năng lực theo 4 kỹ năng và theo miền"),
            @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED"),
            @ApiResponse(responseCode = "403", description = "FORBIDDEN"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_FAILED")})
    public SkillMastery skills(@RequestParam(defaultValue = "3") int hskLevel) {
        if (hskLevel < 1 || hskLevel > 9) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cấp HSK không hợp lệ.", Map.of());
        }
        long userId = auth.currentUser().userId();
        return new SkillMastery(hskLevel, groups(BY_SKILL, userId, hskLevel, SKILLS),
                groups(BY_DOMAIN, userId, hskLevel, DOMAINS));
    }

    private List<Group> groups(String sql, long userId, int level, List<String> order) {
        Map<String, Group> found = new LinkedHashMap<>();
        for (Object row : entityManager.createNativeQuery(sql).setParameter("userId", userId)
                .setParameter("level", level).getResultList()) {
            Object[] columns = (Object[]) row;
            Integer percent = columns[4] == null ? null
                    : new BigDecimal(columns[4].toString()).multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP).intValue();
            String code = (String) columns[0];
            found.put(code, new Group(code, ((Number) columns[1]).intValue(), ((Number) columns[2]).intValue(),
                    ((Number) columns[3]).intValue(), percent));
        }
        List<Group> result = new ArrayList<>();
        // Every skill and domain is listed, even one the imported tree does not cover yet.
        for (String code : order) {
            Group group = found.get(code);
            if (group != null || !code.equals("VB")) result.add(group != null ? group : new Group(code, 0, 0, 0, null));
        }
        return result;
    }
}
