package com.cnhsk.shared.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Transactional
class AuditTargetCountIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
    @Autowired private AuditService audit;
    @Autowired private AuditLogRepository repository;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;

    @Test
    void FR016_countsEachTargetFromItsOwnInclusiveCutoffAndReturnsZeroEntries() {
        String action = action();
        seed(action, "REFERENCE", 11, NOW.minusSeconds(1));
        seed(action, "REFERENCE", 11, NOW);
        seed(action, "REFERENCE", 11, NOW.plusSeconds(2));
        seed(action, "REFERENCE", 12, NOW);
        seed(action, "REFERENCE", 12, NOW.plusSeconds(101));
        seed(action + "X", "REFERENCE", 11, NOW.plusSeconds(3));
        seed(action, "OTHER", 11, NOW.plusSeconds(3));
        seed(action, "REFERENCE", 99, NOW.plusSeconds(3));

        assertThat(audit.countByTargets(action, "REFERENCE", Set.of(11L, 12L, 13L),
                Map.of(11L, NOW, 12L, NOW.plusSeconds(100))))
                .containsOnly(Map.entry(11L, new AuditService.ReportStat(2, NOW.plusSeconds(2))),
                        Map.entry(12L, new AuditService.ReportStat(1, NOW.plusSeconds(101))),
                        Map.entry(13L, new AuditService.ReportStat(0, null)));
    }

    @Test
    void FR016_missingAndNullCutoffsCountAllHistoryAndLegacyTargetTypesRemainUsable() {
        String action = action();
        seed(action, "user", 21, NOW.minusSeconds(100));
        seed(action, "user", 22, NOW);
        Map<Long, Instant> since = new HashMap<>();
        since.put(21L, null);
        assertThat(audit.countByTargets(action, "user", Set.of(21L, 22L), since))
                .containsEntry(21L, new AuditService.ReportStat(1, NOW.minusSeconds(100)))
                .containsEntry(22L, new AuditService.ReportStat(1, NOW));
        assertThat(audit.countByTargets(action, "user", Set.of(21L), null))
                .containsEntry(21L, new AuditService.ReportStat(1, NOW.minusSeconds(100)));
    }

    @Test
    void FR016_emptyInputDoesNotQueryAndInvalidIdsAreRejectedBeforeQuerying() {
        var storage = mock(NamedParameterJdbcTemplate.class);
        var records = mock(AuditLogRepository.class);
        var service = new AuditService(records, mapper, storage);
        assertThat(service.countByTargets("REFERENCE_REPORTED", "REFERENCE", Set.of(), Map.of())).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> service.countByTargets("A", "REFERENCE", Set.of(0L), Map.of()));
        Set<Long> nullId = new java.util.HashSet<>();
        nullId.add(null);
        assertThatIllegalArgumentException().isThrownBy(() -> service.countByTargets("A", "REFERENCE", nullId, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.countByTargets(null, "REFERENCE", Set.of(), Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.countByTargets("A", null, Set.of(), Map.of()));
        assertThatNullPointerException().isThrownBy(() -> service.countByTargets("A", "REFERENCE", null, Map.of()));
        verifyNoInteractions(storage, records);
    }

    @Test
    void FR016_oneAggregateQueryServesOneOrFiftyTargetsWithoutReadingJsonb() {
        var storage = spy(named);
        var service = new AuditService(repository, mapper, storage);
        service.countByTargets(action(), "REFERENCE", Set.of(1L), Map.of());
        verify(storage, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        clearInvocations(storage);
        Set<Long> ids = LongStream.rangeClosed(1, 50).boxed().collect(Collectors.toSet());
        assertThat(service.countByTargets(action(), "REFERENCE", ids, Map.of())).hasSize(50)
                .allSatisfy((id, stat) -> assertThat(stat).isEqualTo(new AuditService.ReportStat(0, null)));
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage, times(1)).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("a.target_type = :targetType", "a.target_id = requested.id",
                "a.action = :action", "a.created_at >= requested.since_at", "GROUP BY a.target_id")
                .doesNotContain("detail", "jsonb");
    }

    @Test
    void FR016_existingRecordDetailWritesCanBeCountedInTheSameTransaction() {
        String action = action();
        audit.recordDetail(null, action, "REFERENCE", 31L, Map.of("reason", "OTHER"));
        var stat = audit.countByTargets(action, "REFERENCE", Set.of(31L), Map.of()).get(31L);
        assertThat(stat.count()).isEqualTo(1);
        assertThat(stat.lastAt()).isNotNull();
        assertThat(audit.countByTargets("' OR true --", "REFERENCE", Set.of(31L), Map.of()).get(31L).count()).isZero();
    }

    private String action() {
        return "COUNT_" + UUID.randomUUID();
    }

    private void seed(String action, String type, long id, Instant createdAt) {
        jdbc.update("INSERT INTO shared.audit_logs(action,target_type,target_id,detail,created_at) "
                + "VALUES (?, ?, ?, '{}'::jsonb, ?)", action, type, id, Timestamp.from(createdAt));
    }
}
