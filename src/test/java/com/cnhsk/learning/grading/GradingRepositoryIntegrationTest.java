package com.cnhsk.learning.grading;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.grading.GradingRepository.RequestRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import({GradingRepository.class, GradingRepositoryIntegrationTest.JsonConfiguration.class})
class GradingRepositoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("grading_repository_test").withUsername("grading_repository_test")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired private GradingRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NamedParameterJdbcTemplate named;
    @Autowired private ObjectMapper mapper;
    @Autowired private PlatformTransactionManager transactions;
    private final List<Long> users = new ArrayList<>();
    private long owner;
    private long teacher;
    private long other;

    @BeforeEach
    void fixture() { owner = user(); teacher = user(); other = user(); }

    @AfterEach
    void cleanupOwnFixtures() {
        for (long id : users) jdbc.update("DELETE FROM learning.service_requests WHERE user_id=?", id);
        for (long id : users) jdbc.update("DELETE FROM auth.users WHERE id=?", id);
    }

    @Test
    void section03_pendingSnapshotAndActiveHashAreScopedToOwnerKindAndStatus() {
        Instant deadline = NOW.plusSeconds(48 * 3600);
        ObjectNode payload = payload();
        long id = repository.createPending(owner, payload, NOW, deadline);
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.userId()).isEqualTo(owner);
        assertThat(row.payload()).isEqualTo(payload);
        assertThat(row.createdAt()).isEqualTo(NOW);
        assertThat(row.deadline()).isEqualTo(deadline);
        assertThat(row.assigneeId()).isNull(); assertThat(row.assignedAt()).isNull();
        assertThat(row.completedAt()).isNull(); assertThat(row.result()).isNull(); assertThat(row.score()).isNull();
        assertThat(row.feedback()).isNull(); assertThat(row.payout()).isNull(); assertThat(row.errorMessage()).isNull();
        assertThat(row.toString()).doesNotContain("字", "consumption", HASH);
        assertThat(repository.find(Long.MAX_VALUE)).isEmpty();
        assertThat(repository.hasActiveEssay(owner, HASH)).isTrue();
        assertThat(repository.hasActiveEssay(other, HASH)).isFalse();
        assertThat(repository.hasActiveEssay(owner, "' OR true --")).isFalse();
        assertThat(repository.claim(id, teacher, NOW, NOW.plusSeconds(24 * 3600))).isTrue();
        assertThat(repository.hasActiveEssay(owner, HASH)).isTrue();
        assertThat(repository.complete(id, teacher, NOW, 80, "Nhận xét tự soạn", result(), 0)).isTrue();
        assertThat(repository.hasActiveEssay(owner, HASH)).isFalse();
        assertNoBilling();
    }

    @Test
    void section02_cancelOnlyWinsForTheOwnerWhilePendingAndDoesNotRecordMoney() {
        long id = pending(NOW.plusSeconds(48 * 3600));
        assertThat(repository.cancel(id, other, NOW)).isFalse();
        assertThat(repository.cancel(id, owner, NOW)).isTrue();
        assertThat(repository.cancel(id, owner, NOW.plusSeconds(1))).isFalse();
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("FAILED");
        assertThat(row.errorMessage()).isEqualTo("CANCELLED_BY_LEARNER");
        assertThat(row.completedAt()).isEqualTo(NOW);
        assertThat(row.payload()).isEqualTo(payload());
        assertThat(repository.hasActiveEssay(owner, HASH)).isFalse();
        assertNoBilling();
    }

    @Test
    void section02_claimStoresTheOriginalDeadlineAndRejectsSelfWrongStateAndElapsedDeadline() {
        Instant original = NOW.plusSeconds(48 * 3600);
        Instant grading = NOW.plusSeconds(24 * 3600);
        long id = pending(original);
        assertThat(repository.claim(id, owner, NOW, grading)).isFalse();
        assertThat(repository.claim(id, teacher, NOW, grading)).isTrue();
        assertThat(repository.claim(id, other, NOW, grading)).isFalse();
        assertThat(repository.cancel(id, owner, NOW)).isFalse();
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("ASSIGNED");
        assertThat(row.assigneeId()).isEqualTo(teacher); assertThat(row.assignedAt()).isEqualTo(NOW);
        assertThat(row.deadline()).isEqualTo(grading);
        assertThat(Instant.parse(row.payload().path("claimDeadline").asText())).isEqualTo(original);
        assertThat(row.payload().path("essay")).isEqualTo(payload().path("essay"));
        assertThat(row.payload().path("consumption")).isEqualTo(payload().path("consumption"));
        assertThat(repository.countActiveClaims(teacher)).isEqualTo(1);
        assertThat(repository.countActiveClaims(other)).isZero();
        assertThat(repository.claim(pending(NOW), teacher, NOW, grading)).isFalse();
        assertThat(repository.claim(pending(NOW.minusSeconds(1)), teacher, NOW, grading)).isFalse();
    }

    @Test
    void section02_releaseToPendingClearsAssignmentAndRestoresTheOriginalDeadline() {
        Instant original = NOW.plusSeconds(3600);
        long id = pending(original);
        assertThat(repository.claim(id, teacher, NOW, NOW.plusSeconds(24 * 3600))).isTrue();
        assertThat(repository.release(id, other, NOW.plusSeconds(10))).isFalse();
        assertThat(repository.release(id, teacher, NOW.plusSeconds(1800))).isTrue();
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.assigneeId()).isNull(); assertThat(row.assignedAt()).isNull(); assertThat(row.completedAt()).isNull();
        assertThat(row.deadline()).isEqualTo(original);
        assertThat(row.payload().path("consumption")).isEqualTo(payload().path("consumption"));
        assertThat(repository.release(id, teacher, NOW.plusSeconds(1800))).isFalse();
        Instant secondClaim = NOW.plusSeconds(2000);
        Instant secondDeadline = secondClaim.plusSeconds(24 * 3600);
        assertThat(repository.claim(id, other, secondClaim, secondDeadline)).isTrue();
        assertThat(repository.release(id, other, original.plusSeconds(1))).isTrue();
        row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("EXPIRED");
        assertThat(row.assigneeId()).isEqualTo(other); assertThat(row.assignedAt()).isEqualTo(secondClaim);
        assertThat(row.deadline()).isEqualTo(secondDeadline);
        assertThat(row.completedAt()).isEqualTo(original.plusSeconds(1));
        assertNoBilling();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void section02_releaseAtOrAfterClaimDeadlineExpiresAndPreservesTheAssignee(long secondsAfter) {
        Instant original = NOW.plusSeconds(3600);
        Instant grading = NOW.plusSeconds(24 * 3600);
        long id = pending(original);
        repository.claim(id, teacher, NOW, grading);
        Instant released = original.plusSeconds(secondsAfter);
        assertThat(repository.release(id, teacher, released)).isTrue();
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("EXPIRED");
        assertThat(row.assigneeId()).isEqualTo(teacher); assertThat(row.assignedAt()).isEqualTo(NOW);
        assertThat(row.deadline()).isEqualTo(grading); assertThat(row.completedAt()).isEqualTo(released);
        assertThat(repository.hasActiveEssay(owner, HASH)).isFalse();
    }

    @Test
    void section02_completeAcceptsTheExactDeadlineAndOnlyTheAssigneeOnce() {
        long id = pending(NOW.plusSeconds(48 * 3600));
        assertThat(repository.complete(id, teacher, NOW, 80, "Tự soạn", result(), 0)).isFalse();
        repository.claim(id, teacher, NOW, NOW.plusSeconds(24 * 3600));
        Instant deadline = NOW.plusSeconds(24 * 3600);
        assertThat(repository.complete(id, other, NOW, 80, "Tự soạn", result(), 0)).isFalse();
        assertThat(repository.complete(id, teacher, deadline.plusMillis(1), 80, "Tự soạn", result(), 0)).isFalse();
        assertThat(repository.complete(id, teacher, deadline, 80, "Tự soạn", result(), 2)).isTrue();
        assertThat(repository.complete(id, teacher, deadline, 99, "Lặp", result(), 9)).isFalse();
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("COMPLETED"); assertThat(row.score()).isEqualTo(80);
        assertThat(row.feedback()).isEqualTo("Tự soạn"); assertThat(row.result()).isEqualTo(result());
        assertThat(row.payout()).isEqualTo(2); assertThat(row.completedAt()).isEqualTo(deadline);
        assertThat(row.assigneeId()).isEqualTo(teacher);
        assertNoBilling();
    }

    @Test
    void section02_expireIsStrictlyPastDeadlineMatchesTheExpectedStateAndPreservesAssignment() {
        long pending = pending(NOW);
        assertThat(repository.expire(pending, "PENDING", NOW)).isFalse();
        assertThat(repository.expire(pending, "ASSIGNED", NOW.plusMillis(1))).isFalse();
        assertThat(repository.expire(pending, "PENDING", NOW.plusMillis(1))).isTrue();
        assertThat(repository.expire(pending, "EXPIRED", NOW.plusSeconds(1))).isFalse();
        long assigned = pending(NOW.plusSeconds(48 * 3600));
        repository.claim(assigned, teacher, NOW.minusSeconds(10), NOW);
        assertThat(repository.expire(assigned, "ASSIGNED", NOW)).isFalse();
        assertThat(repository.expire(assigned, "ASSIGNED", NOW.plusMillis(1))).isTrue();
        var row = repository.find(assigned).orElseThrow();
        assertThat(row.assigneeId()).isEqualTo(teacher); assertThat(row.assignedAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(row.deadline()).isEqualTo(NOW); assertThat(repository.countActiveClaims(teacher)).isZero();
        assertNoBilling();
    }

    @Test
    void section02_everyOperationIgnoresOtherRequestKindsAndMissingIds() {
        long foreign = jdbc.queryForObject("INSERT INTO learning.service_requests(user_id,kind,status,payload,deadline_at) "
                        + "VALUES (?,'IMPORT','PENDING',CAST(? AS jsonb),?) RETURNING id", Long.class,
                owner, payload().toString(), Timestamp.from(NOW.minusSeconds(1)));
        var before = jdbc.queryForMap("SELECT * FROM learning.service_requests WHERE id=?", foreign);
        for (long id : new long[]{foreign, Long.MAX_VALUE}) {
            assertThat(repository.find(id)).isEmpty();
            assertThat(repository.claim(id, teacher, NOW, NOW.plusSeconds(3600))).isFalse();
            assertThat(repository.cancel(id, owner, NOW)).isFalse();
            assertThat(repository.release(id, teacher, NOW)).isFalse();
            assertThat(repository.complete(id, teacher, NOW, 80, "Tự soạn", result(), 0)).isFalse();
            assertThat(repository.expire(id, "PENDING", NOW)).isFalse();
        }
        assertThat(repository.hasActiveEssay(owner, HASH)).isFalse();
        assertThat(repository.findOverdue(NOW)).isEmpty();
        assertThat(jdbc.queryForMap("SELECT * FROM learning.service_requests WHERE id=?", foreign)).isEqualTo(before);
    }

    @Test
    void FR017_overdueBatchIsOrderedBoundedAndExcludesExactDeadlineAndClosedRows() {
        long closed = pending(NOW.minusSeconds(1)); repository.cancel(closed, owner, NOW);
        long expired = pending(NOW.minusSeconds(1)); repository.expire(expired, "PENDING", NOW);
        pending(NOW); pending(NOW.plusSeconds(1));
        List<Long> expected = new ArrayList<>();
        for (int index = 0; index < 105; index++) expected.add(pending(NOW.minusSeconds(1)));
        assertThat(repository.findOverdue(NOW)).extracting(GradingRepository.RequestRow::id)
                .containsExactlyElementsOf(expected.subList(0, 100));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void section02_writesRequireTheCallersTransaction() {
        assertThatThrownBy(() -> pending(NOW.plusSeconds(3600))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.lockSubmit(owner)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.claim(1, teacher, NOW, NOW.plusSeconds(3600))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.cancel(1, owner, NOW)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.release(1, teacher, NOW)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.complete(1, teacher, NOW, 80, "Tự soạn", result(), 0)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.expire(1, "PENDING", NOW)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void section02_submitAdvisoryLockUsesTheUserKeyAndIsReleasedAtCommit() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            tx().executeWithoutResult(status -> {
                repository.lockSubmit(owner);
                try {
                    boolean same = executor.submit(() -> tx().execute(inner -> tryLock(owner))).get(20, TimeUnit.SECONDS);
                    boolean different = executor.submit(() -> tx().execute(inner -> tryLock(other))).get(20, TimeUnit.SECONDS);
                    assertThat(same).isFalse(); assertThat(different).isTrue();
                } catch (Exception exception) { throw new IllegalStateException("Advisory lock test failed.", exception); }
            });
            boolean acquiredAfterCommit = tx().execute(status -> tryLock(owner));
            assertThat(acquiredAfterCommit).isTrue();
        } finally { executor.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"claim", "cancel", "release", "complete", "expire"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void section02_callerRollbackRestoresEveryTransition(String operation) {
        long id = tx().execute(status -> pending(NOW.plusSeconds(3600)));
        if (Set.of("release", "complete", "expire").contains(operation)) {
            tx().executeWithoutResult(status -> repository.claim(id, teacher, NOW.minusSeconds(10), NOW));
        }
        var before = repository.find(id).orElseThrow();
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
            boolean won = switch (operation) {
                case "claim" -> repository.claim(id, teacher, NOW, NOW.plusSeconds(3600));
                case "cancel" -> repository.cancel(id, owner, NOW);
                case "release" -> repository.release(id, teacher, NOW);
                case "complete" -> repository.complete(id, teacher, NOW, 80, "Tự soạn", result(), 0);
                default -> repository.expire(id, "ASSIGNED", NOW.plusSeconds(1));
            };
            assertThat(won).isTrue();
            throw new IllegalStateException("rollback-fixture");
        })).isInstanceOf(IllegalStateException.class).hasMessage("rollback-fixture");
        assertThat(repository.find(id).orElseThrow()).isEqualTo(before);
        assertNoBilling();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void section02_tenTeachersCanOnlyProduceOneSuccessfulClaim() throws Exception {
        long id = tx().execute(status -> pending(NOW.plusSeconds(3600)));
        List<Callable<Boolean>> calls = new ArrayList<>();
        Set<Long> teachers = new java.util.HashSet<>();
        for (int index = 0; index < 10; index++) {
            long candidate = user(); teachers.add(candidate);
            calls.add(() -> tx().execute(status -> repository.claim(id, candidate, NOW, NOW.plusSeconds(24 * 3600))));
        }
        assertThat(race(calls)).containsOnlyOnce(true).filteredOn(Boolean::booleanValue).hasSize(1);
        var row = repository.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("ASSIGNED"); assertThat(teachers).contains(row.assigneeId());
        assertNoBilling();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void section02_cancelAndClaimHaveOnlyOneWinner() throws Exception {
        long id = tx().execute(status -> pending(NOW.plusSeconds(3600)));
        var winners = race(List.of(() -> tx().execute(status -> repository.cancel(id, owner, NOW)),
                () -> tx().execute(status -> repository.claim(id, teacher, NOW, NOW.plusSeconds(24 * 3600)))));
        assertThat(winners).containsOnlyOnce(true).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(repository.find(id).orElseThrow().status()).isIn("FAILED", "ASSIGNED");
        assertNoBilling();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void section02_expiryAndEarlierClaimCannotBothWin() throws Exception {
        long id = tx().execute(status -> pending(NOW.minusSeconds(1)));
        var winners = race(List.of(() -> tx().execute(status -> repository.expire(id, "PENDING", NOW)),
                () -> tx().execute(status -> repository.claim(id, teacher, NOW.minusSeconds(2), NOW.plusSeconds(24 * 3600)))));
        assertThat(winners).containsOnlyOnce(true).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(repository.find(id).orElseThrow().status()).isIn("EXPIRED", "ASSIGNED");
        assertNoBilling();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void section02_repeatedCompletionHasOnlyOneWinnerWithoutWritingTheLedger() throws Exception {
        long id = tx().execute(status -> pending(NOW.plusSeconds(3600)));
        tx().executeWithoutResult(status -> repository.claim(id, teacher, NOW, NOW.plusSeconds(24 * 3600)));
        Callable<Boolean> complete = () -> tx().execute(status -> repository.complete(id, teacher, NOW, 80, "Tự soạn", result(), 2));
        assertThat(race(List.of(complete, complete))).containsOnlyOnce(true).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(repository.find(id).orElseThrow().status()).isEqualTo("COMPLETED");
        assertNoBilling();
    }

    @Test
    void FR004_mineQueriesFilterBeforePagingAndUseADeterministicTieBreak() {
        long first = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(3600));
        long second = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(3600));
        long newest = repository.createPending(owner, payload(), NOW.plusSeconds(1), NOW.plusSeconds(3600));
        repository.createPending(other, payload(), NOW.plusSeconds(2), NOW.plusSeconds(3600));
        assertThat(repository.cancel(second, owner, NOW.plusSeconds(3))).isTrue();
        assertThat(repository.countMine(owner, null)).isEqualTo(3);
        assertThat(repository.findMine(owner, null, 0, 2)).extracting(RequestRow::id).containsExactly(newest, second);
        assertThat(repository.findMine(owner, null, 1, 2)).extracting(RequestRow::id).containsExactly(first);
        assertThat(repository.countMine(owner, "PENDING")).isEqualTo(2);
        assertThat(repository.findMine(owner, "PENDING", 1, 1)).extracting(RequestRow::id).containsExactly(first);
        assertThat(repository.findMine(owner, null, Integer.MAX_VALUE, 50)).isEmpty();
    }

    @Test
    void AT06_queueExcludesSelfExpiredAndOtherKindsBeforePaging() {
        repository.createPending(teacher, payload(), NOW, NOW.plusSeconds(1));
        repository.createPending(owner, payload(), NOW, NOW);
        repository.createPending(owner, payload(), NOW, NOW.minusSeconds(1));
        long late = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(30));
        long first = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(10));
        long second = repository.createPending(other, payload(), NOW, NOW.plusSeconds(10));
        long assigned = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(2));
        assertThat(repository.claim(assigned, teacher, NOW, NOW.plusSeconds(40))).isTrue();
        jdbc.update("INSERT INTO learning.service_requests(user_id,kind,status,payload,deadline_at) VALUES (?,'IMPORT','PENDING','{}',?)", owner, Timestamp.from(NOW.plusSeconds(1)));
        assertThat(repository.countQueue(teacher, NOW)).isEqualTo(3);
        assertThat(repository.findQueue(teacher, NOW, 0, 2)).extracting(GradingRepository.QueueRow::id).containsExactly(first, second);
        assertThat(repository.findQueue(teacher, NOW, 1, 2)).extracting(GradingRepository.QueueRow::id).containsExactly(late);
        assertThat(repository.findQueue(teacher, NOW, 0, 2)).allSatisfy(row -> {
            assertThat(row.hanziCount()).isEqualTo(50); assertThat(row.hskLevel()).isNull();
        });
    }

    @Test
    void Q_T03_03_mineIncludesOnlyItsTeacherAndKeepsOverdueAssignments() {
        long later = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(3600));
        long first = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(3600));
        long second = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(3600));
        long foreign = repository.createPending(owner, payload(), NOW, NOW.plusSeconds(3600));
        assertThat(repository.claim(later, teacher, NOW, NOW.plusSeconds(30))).isTrue();
        assertThat(repository.claim(first, teacher, NOW.minusSeconds(2), NOW.minusSeconds(1))).isTrue();
        assertThat(repository.claim(second, teacher, NOW.minusSeconds(2), NOW.minusSeconds(1))).isTrue();
        assertThat(repository.claim(foreign, other, NOW, NOW.plusSeconds(30))).isTrue();
        assertThat(repository.findAssigned(teacher)).extracting(GradingRepository.AssignedRow::id).containsExactly(first, second, later);
        assertThat(repository.countActiveClaims(teacher)).isEqualTo(3);
    }

    @Test
    void NFR_P01_queueUsesTwoQueriesWithOneThousandRequestsAndOnlyMeasuresP95() {
        var counted = spy(named);
        var queries = new GradingRepository(counted, mapper);
        var config = new GradingConfig(); config.setTeacherPayoutCredits(0);
        var service = new TeacherGradingService(queries, config, mock(com.cnhsk.learning.api.EntitlementApi.class),
                mapper, java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        repository.createPending(owner, payload(), NOW, NOW.plusSeconds(3600));
        assertThat(service.queue(teacher, 0, 20).totalElements()).isEqualTo(1);
        verify(counted, times(1)).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));
        verify(counted, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        for (int index = 1; index < 1000; index++) repository.createPending(owner, payload(), NOW, NOW.plusSeconds(3600));
        clearInvocations(counted);
        var page = service.queue(teacher, 0, 20);
        assertThat(page.totalElements()).isEqualTo(1000); assertThat(page.items()).hasSize(20);
        verify(counted, times(1)).queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class));
        verify(counted, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
        for (int warmup = 0; warmup < 3; warmup++) service.queue(teacher, 0, 20);
        double[] milliseconds = new double[20];
        for (int sample = 0; sample < milliseconds.length; sample++) {
            long start = System.nanoTime(); service.queue(teacher, 0, 20);
            milliseconds[sample] = (System.nanoTime() - start) / 1_000_000.0;
        }
        java.util.Arrays.sort(milliseconds);
        System.out.printf(java.util.Locale.ROOT, "NFR-P01 grading queue rows=1000 queries=2 p95Ms=%.3f (service+DB, measurement only)%n", milliseconds[18]);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void Q_T03_01_claimLockRequiresAndLivesInsideTheCallersTransaction() throws Exception {
        assertThatThrownBy(() -> repository.lockTeacherClaim(teacher)).isInstanceOf(IllegalTransactionStateException.class);
        var executor = Executors.newSingleThreadExecutor();
        try {
            tx().executeWithoutResult(status -> {
                repository.lockTeacherClaim(teacher);
                try {
                    boolean same = executor.submit(() -> tx().execute(inner -> tryTeacherLock(teacher))).get(20, TimeUnit.SECONDS);
                    boolean different = executor.submit(() -> tx().execute(inner -> tryTeacherLock(other))).get(20, TimeUnit.SECONDS);
                    boolean submit = executor.submit(() -> tx().execute(inner -> tryLock(teacher))).get(20, TimeUnit.SECONDS);
                    assertThat(same).isFalse(); assertThat(different).isTrue(); assertThat(submit).isTrue();
                } catch (Exception exception) { throw new IllegalStateException("Claim lock fixture failed", exception); }
            });
            boolean afterCommit = tx().execute(status -> tryTeacherLock(teacher));
            assertThat(afterCommit).isTrue();
        } finally { executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }

    private boolean tryTeacherLock(long teacherId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended(?,0))",
                Boolean.class, "grading:claim:" + teacherId));
    }

    private List<Boolean> race(List<Callable<Boolean>> calls) throws Exception {
        var executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch ready = new CountDownLatch(calls.size()); CountDownLatch go = new CountDownLatch(1);
        try {
            var futures = calls.stream().map(call -> executor.submit(() -> {
                ready.countDown(); if (!go.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out.");
                return call.call();
            })).toList();
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue(); go.countDown();
            List<Boolean> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { go.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }

    private boolean tryLock(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtextextended(?,0))",
                Boolean.class, "grading:submit:" + userId));
    }

    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }

    private long pending(Instant deadline) { return repository.createPending(owner, payload(), NOW.minusSeconds(5 * 86400), deadline); }

    private ObjectNode payload() {
        ObjectNode payload = mapper.createObjectNode().put("essay", "字".repeat(50)).put("essayHash", HASH).put("hanziCount", 50);
        payload.putObject("consumption").put("source", "QUOTA").put("feature", "GRADING").put("idempotencyKey", "fixture-key")
                .putNull("transactionId").put("periodStart", NOW.toString());
        return payload;
    }

    private ObjectNode result() {
        ObjectNode result = mapper.createObjectNode();
        result.putObject("criteria").put("grammar", 20).put("vocabulary", 20).put("coherence", 20).put("writing", 20);
        result.putArray("corrections"); return result;
    }

    private long user() {
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test", PASSWORD_HASH, "Grading fixture");
        users.add(id); return id;
    }

    private void assertNoBilling() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.credit_transactions", Long.class)).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JsonConfiguration {
        @Bean ObjectMapper gradingTestMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }
}
