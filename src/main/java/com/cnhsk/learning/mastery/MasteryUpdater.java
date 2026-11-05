package com.cnhsk.learning.mastery;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cnhsk.learning.knowledge.KnowledgeTreeService;
import com.cnhsk.learning.mastery.EvidenceAllocator.AllocatedEvidence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes MP-1.0 updates by the progress primary key in the finish transaction. */
@Component
public class MasteryUpdater {

    private static final String INSERT = "INSERT INTO learning.user_progress "
            + "(user_id, target_type, target_id, mp_state) "
            + "VALUES (?, 'KNOWLEDGE_POINT', ?, 'notyet') "
            + "ON CONFLICT (user_id, target_type, target_id) DO NOTHING";
    private static final String LOCK = "SELECT mp_state, evidence_item_ids FROM learning.user_progress "
            + "WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=? FOR UPDATE";
    private static final String UPDATE = "UPDATE learning.user_progress SET mastery=?, confidence=?, mp_state=?, "
            + "evidence_item_ids=?, next_review_at=?, updated_at=? "
            + "WHERE user_id=? AND target_type='KNOWLEDGE_POINT' AND target_id=?";

    private final JdbcTemplate jdbc;
    private final KnowledgeTreeService tree;
    private final EvidenceAllocator allocator;
    private final MasteryConfig config;
    private final Clock clock;
    private final MasteryCalculator calculator = new MasteryCalculator();
    private final MasteryStateMachine states = new MasteryStateMachine();

    public MasteryUpdater(JdbcTemplate jdbc, KnowledgeTreeService tree, EvidenceAllocator allocator,
            MasteryConfig config, Clock clock) {
        this.jdbc = jdbc;
        this.tree = tree;
        this.allocator = allocator;
        this.config = config;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(long userId, List<AllocatedEvidence> fresh) {
        if (fresh.isEmpty()) {
            return;
        }
        Map<String, Long> ids = tree.nodes().stream().collect(Collectors.toMap(
                KnowledgeTreeService.KnowledgeNode::kid, KnowledgeTreeService.KnowledgeNode::id));
        Map<Long, List<AllocatedEvidence>> grouped = new HashMap<>();
        for (AllocatedEvidence item : fresh) {
            Long id = ids.get(item.kid());
            if (id != null) {
                grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(item);
            }
        }
        List<Long> ordered = grouped.keySet().stream().sorted().toList();
        Map<Long, ProgressRow> locked = new LinkedHashMap<>();
        Set<Long> historicalIds = new HashSet<>();
        for (Long id : ordered) {
            jdbc.update(INSERT, userId, id);
            ProgressRow row = jdbc.queryForObject(LOCK, (result, number) -> {
                try {
                    Array array = result.getArray("evidence_item_ids");
                    Long[] evidence = array == null ? new Long[0] : (Long[]) array.getArray();
                    return new ProgressRow(MasteryState.fromCode(result.getString("mp_state")),
                            Arrays.asList(evidence));
                } catch (SQLException exception) {
                    throw new IllegalStateException("Cannot read evidence IDs", exception);
                }
            }, userId, id);
            locked.put(id, row);
            historicalIds.addAll(row.evidenceIds());
        }
        Map<String, List<AllocatedEvidence>> previous = allocator.reallocate(userId, historicalIds).stream()
                .collect(Collectors.groupingBy(AllocatedEvidence::kid));
        Instant now = clock.instant();
        for (Long id : ordered) {
            String kid = ids.entrySet().stream().filter(entry -> entry.getValue().equals(id))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            List<MasteryCalculator.Evidence> candidates = new ArrayList<>();
            previous.getOrDefault(kid, List.of()).stream()
                    .filter(item -> locked.get(id).evidenceIds().contains(item.itemId()))
                    .map(AllocatedEvidence::asCalculatorEvidence).forEach(candidates::add);
            grouped.get(id).stream().map(AllocatedEvidence::asCalculatorEvidence).forEach(candidates::add);
            MasteryCalculator.Result result = calculator.calculate(candidates, now, config.settings());
            MasteryStateMachine.Decision decision = states.transition(locked.get(id).state(), result,
                    now, config.settings());
            BigDecimal mastery = result.value() == null ? BigDecimal.ZERO
                    : result.value().movePointLeft(2);
            Long[] evidenceIds = result.evidence().stream().map(MasteryCalculator.Evidence::itemId)
                    .toArray(Long[]::new);
            int changed = jdbc.update(connection -> {
                var statement = connection.prepareStatement(UPDATE);
                statement.setBigDecimal(1, mastery);
                statement.setBigDecimal(2, result.confidence());
                statement.setString(3, decision.state().code());
                statement.setArray(4, connection.createArrayOf("bigint", evidenceIds));
                statement.setTimestamp(5, decision.nextReviewAt() == null ? null
                        : Timestamp.from(decision.nextReviewAt()));
                statement.setTimestamp(6, Timestamp.from(now));
                statement.setLong(7, userId);
                statement.setLong(8, id);
                return statement;
            });
            if (changed != 1) {
                throw new IllegalStateException("Locked progress row disappeared");
            }
        }
    }

    private record ProgressRow(MasteryState state, List<Long> evidenceIds) { }
}
