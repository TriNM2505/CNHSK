package com.cnhsk.learning.aigen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.aigen.WeakKnowledgeSelector.Candidate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans which nodes of one HSK level the AI drafts placement questions for: every node that has a can-do
 * statement and words to ask about, taken in turn across knowledge domains so no domain or node dominates.
 */
@Component
public class PlacementDraftPlanner {
    private final NamedParameterJdbcTemplate jdbc;

    public PlacementDraftPlanner(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AiGenerationQuota.Allocation> plan(int level, int count, int batchSize) {
        List<Node> nodes = jdbc.query("""
                SELECT kp.id, kp.code, kp.domain FROM learning.knowledge_points kp
                WHERE kp.hsk_level = :level AND coalesce(btrim(kp.can_do), '') <> ''
                  AND EXISTS (SELECT 1 FROM learning.lexemes l WHERE l.knowledge_point_id = kp.id)
                ORDER BY kp.domain, kp.code
                """, new MapSqlParameterSource("level", level),
                (rs, row) -> new Node(rs.getLong("id"), rs.getString("code"), rs.getString("domain")));
        return spread(nodes, count, batchSize);
    }

    /**
     * Splits {@code count} questions into batches of at most {@code batchSize}, one node per batch, cycling
     * through the domains and, inside each domain, through its nodes; when there are more batches than nodes
     * the nodes are visited again.
     */
    static List<AiGenerationQuota.Allocation> spread(List<Node> nodes, int count, int batchSize) {
        if (nodes.isEmpty() || count <= 0 || batchSize <= 0) return List.of();
        Map<String, List<Node>> byDomain = new LinkedHashMap<>();
        for (Node node : nodes) byDomain.computeIfAbsent(node.domain(), ignored -> new ArrayList<>()).add(node);
        List<Node> order = new ArrayList<>();
        List<Deque<Node>> queues = byDomain.values().stream().map(list -> (Deque<Node>) new ArrayDeque<>(list)).toList();
        boolean added = true;
        while (added) {
            added = false;
            for (Deque<Node> queue : queues) {
                if (!queue.isEmpty()) {
                    order.add(queue.removeFirst());
                    added = true;
                }
            }
        }
        List<AiGenerationQuota.Allocation> plan = new ArrayList<>();
        int left = count;
        for (int index = 0; left > 0; index++) {
            Node node = order.get(index % order.size());
            int take = Math.min(batchSize, left);
            plan.add(new AiGenerationQuota.Allocation(new Candidate(node.id(), node.kid(), 0, 0, 0), take));
            left -= take;
        }
        return List.copyOf(plan);
    }

    record Node(long id, String kid, String domain) {}
}
