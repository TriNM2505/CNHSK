package com.cnhsk.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSourceHandler.Answer;
import com.cnhsk.learning.practice.entity.Attempt;
import com.cnhsk.learning.practice.entity.AttemptItemRepository;
import com.cnhsk.learning.practice.entity.AttemptRepository;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@Import(PracticeFinishIntegrationTest.EventConfiguration.class)
class PracticeMaintenanceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = PracticeServiceTest.NOW;
    @Autowired private PracticeMaintenance maintenance;
    @Autowired private PracticeService practice;
    @Autowired private AttemptRepository attempts;
    @Autowired private AttemptItemRepository items;
    @Autowired private LexemeRepository lexemes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ApplicationContext context;
    @Autowired private Environment environment;
    @Autowired private PracticeFinishIntegrationTest.EventProbe probe;
    @MockitoBean private Clock clock;
    @MockitoBean(name = "stubPracticeSourceHandler") private PracticeSourceHandler handler;
    private long owner;

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(handler.source()).thenReturn(PracticeSource.TOPIC_STUDY);
        when(handler.enrichMeta(any(), any())).thenReturn(Map.of());
        probe.clear();
        String password = "Aa1" + UUID.randomUUID();
        owner = mapper.readTree(mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.test",
                                "password", password, "confirmPassword", password,
                                "displayName", "Maintenance test user", "acceptTerms", true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("userId").asLong();
    }

    @ParameterizedTest
    @ValueSource(strings = {"job", "view", "answer", "finish", "abandon"})
    void FR008_AT16_expirationPreservesFourLearnedLexemesAndEveryRecordedField(String path) {
        List<ServedItem> served = new ArrayList<>();
        for (int no = 1; no <= 10; no++) {
            String text = "测" + (char) (0x4e00 + no);
            Lexeme lexeme = lexemes.findByKindAndText(LexemeKind.WORD, text)
                    .orElseGet(() -> {
                        Lexeme fixture = new Lexeme(LexemeKind.WORD, text, "Từ kiểm thử");
                        fixture.setHskLevel((short) 1);
                        return lexemes.saveAndFlush(fixture);
                    });
            ServedItem card = PracticeServiceTest.card(no);
            served.add(new ServedItem(no, card.type(), card.skill(), lexeme.getId(), card.prompt(),
                    card.options(), card.answer(), card.accept(), card.explanation(), card.rules()));
        }
        long id = practice.start(new StartPracticeCommand(owner, PracticeSource.TOPIC_STUDY, 1L, null,
                served, null, null, null)).sessionId();
        for (int no = 1; no <= 4; no++) {
            practice.answer(owner, id, new Answer(no, "LEARNED", 1000, 0, false, false, null));
        }
        Attempt attempt = attempts.findById(id).orElseThrow();
        JsonNode snapshot = attempt.getServedItems().deepCopy();
        List<Map<String, Object>> before = jdbc.queryForList("select id, answer, is_correct, duration_ms, "
                + "meta::text, lexeme_id, answered_at from learning.attempt_items where attempt_id=? order by order_index", id);
        attempt.setExpiresAt(NOW.minusSeconds(1));
        attempts.saveAndFlush(attempt);
        probe.clear();
        clearInvocations(handler);
        assertThat(learnedWords()).isEqualTo(4);
        switch (path) {
            case "job" -> maintenance.expireSessions();
            case "view" -> assertThat(practice.view(owner, id).status()).isEqualTo("EXPIRED");
            case "answer" -> assertThatThrownBy(() -> practice.answer(owner, id,
                    new Answer(5, "LEARNED", 1000, 0, false, false, null))).isInstanceOf(ApiException.class)
                    .hasMessage("Lượt luyện đã hết hạn.");
            case "finish" -> assertThatThrownBy(() -> practice.finish(owner, id, List.of())).isInstanceOf(ApiException.class)
                    .hasMessage("Lượt luyện đã hết hạn.");
            case "abandon" -> assertThatThrownBy(() -> practice.abandon(owner, id)).isInstanceOf(ApiException.class)
                    .hasMessage("Lượt luyện đã hết hạn.");
            default -> throw new AssertionError(path);
        }
        Attempt expired = attempts.findById(id).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo("EXPIRED");
        assertThat(expired.getServedItems()).isEqualTo(snapshot);
        assertThat(expired.getSubmittedAt()).isNull();
        assertThat(expired.getScore()).isNull();
        assertThat(expired.getMaxScore()).isNull();
        assertThat(learnedWords()).isEqualTo(4);
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).hasSize(4);
        assertThat(jdbc.queryForList("select id, answer, is_correct, duration_ms, meta::text, lexeme_id, answered_at "
                + "from learning.attempt_items where attempt_id=? order by order_index", id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from learning.study_events where user_id=?", Long.class, owner)).isZero();
        assertThat(probe.graded).isEmpty();
        assertThat(probe.finished).isEmpty();
        verifyNoInteractions(handler);
    }

    @Test
    void FR001_jobExpiresOnlyStrictlyPastInProgressSessionsAndIsIdempotent() {
        long past = session("IN_PROGRESS", NOW.minusSeconds(1));
        long boundary = session("IN_PROGRESS", NOW);
        long future = session("IN_PROGRESS", NOW.plusSeconds(1));
        long noExpiry = session("IN_PROGRESS", null);
        long submitted = session("SUBMITTED", NOW.minusSeconds(1));
        long abandoned = session("ABANDONED", NOW.minusSeconds(1));
        long expired = session("EXPIRED", NOW.minusSeconds(1));
        probe.clear();
        maintenance.expireSessions();
        maintenance.expireSessions();
        assertThat(attempts.findById(past).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        for (long id : List.of(boundary, future, noExpiry)) {
            assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("IN_PROGRESS");
        }
        assertThat(attempts.findById(submitted).orElseThrow().getStatus()).isEqualTo("SUBMITTED");
        assertThat(attempts.findById(abandoned).orElseThrow().getStatus()).isEqualTo("ABANDONED");
        assertThat(attempts.findById(expired).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        assertThat(probe.graded).isEmpty();
        assertThat(probe.finished).isEmpty();
    }

    @Test
    void FR008_jobPreservesDeferredDraftIncludingAudioEvidenceAndSnapshot() {
        ServedItem base = PracticeServiceTest.choice(1);
        ServedItem audio = new ServedItem(1, ItemType.AUDIO_WORD_CHOICE, Skill.LISTENING, null,
                base.prompt(), base.options(), base.answer(), base.accept(), base.explanation(),
                new ServedItem.Rules(800, 3, true));
        long id = practice.start(new StartPracticeCommand(owner, PracticeSource.TOPIC_TEST, 1L, null,
                List.of(audio), null, null, null)).sessionId();
        practice.answer(owner, id, new Answer(1, null, 1100, 1, false, true, null));
        Attempt before = attempts.findById(id).orElseThrow();
        JsonNode draft = before.getDraftAnswers().deepCopy();
        before.setExpiresAt(NOW.minusSeconds(1));
        attempts.saveAndFlush(before);
        maintenance.expireSessions();
        Attempt after = attempts.findById(id).orElseThrow();
        assertThat(after.getStatus()).isEqualTo("EXPIRED");
        assertThat(after.getDraftAnswers()).isEqualTo(draft);
        assertThat(after.getDraftSavedAt()).isEqualTo(before.getDraftSavedAt());
        assertThat(after.getServedItems()).isEqualTo(before.getServedItems());
        assertThat(items.findByAttemptIdOrderByOrderIndexAsc(id)).isEmpty();
    }

    @Test
    void FR001_bulkExpirationFlushesPendingChangesAndClearsStaleManagedSessions() {
        long id = session("IN_PROGRESS", NOW.plusSeconds(60));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Attempt managed = entityManager.find(Attempt.class, id);
            assertThat(entityManager.contains(managed)).isTrue();
            managed.setExpiresAt(NOW.minusSeconds(1));
            maintenance.expireSessions();
            assertThat(entityManager.contains(managed)).isFalse();
            assertThat(managed.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(entityManager.find(Attempt.class, id).getStatus()).isEqualTo("EXPIRED");
        });
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo("EXPIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "ABANDONED"})
    void FR001_jobWaitsForSessionLockAndRechecksTerminalStateAfterWriterCommits(String terminal) throws Exception {
        long id = session("IN_PROGRESS", NOW.minusSeconds(1));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var writer = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Attempt attempt = attempts.findByIdForUpdate(id).orElseThrow();
                attempt.setStatus(terminal);
                if ("SUBMITTED".equals(terminal)) {
                    attempt.setSubmittedAt(NOW);
                }
                locked.countDown();
                try {
                    assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var cleanup = executor.submit(maintenance::expireSessions);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean waiting = false;
            while (System.nanoTime() < deadline) {
                Long waiters = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() "
                        + "and query like 'update learning.attempts set status%' and wait_event_type='Lock'", Long.class);
                if (waiters != null && waiters > 0) {
                    waiting = true;
                    break;
                }
                Thread.sleep(20);
            }
            assertThat(waiting).as("cleanup must wait for the locked session before testing its predicate again").isTrue();
            assertThat(cleanup.isDone()).isFalse();
            release.countDown();
            writer.get(20, TimeUnit.SECONDS);
            cleanup.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(attempts.findById(id).orElseThrow().getStatus()).isEqualTo(terminal);
        assertThat(probe.graded).isEmpty();
        assertThat(probe.finished).isEmpty();
    }

    @Test
    void FR001_schedulerUsesVietnamZoneAndDisabledTestPropertyWithoutRemovingMaintenanceBean() throws Exception {
        Scheduled scheduled = PracticeMaintenance.class.getMethod("expireSessions").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("${cnhsk.practice.expire-cron:0 5 * * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(environment.getProperty("cnhsk.practice.expire-cron")).isEqualTo("-");
        assertThat(environment.resolveRequiredPlaceholders(scheduled.cron())).isEqualTo(Scheduled.CRON_DISABLED);
        assertThat(context.getBean(PracticeMaintenance.class)).isSameAs(maintenance);
        assertThat(PracticeSchedulingConfiguration.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).hasSize(1);
        assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
    }

    private long session(String state, Instant expiry) {
        long id = practice.start(new StartPracticeCommand(owner, PracticeSource.TOPIC_STUDY, 1L, null,
                List.of(PracticeServiceTest.card(1)), null, null, null)).sessionId();
        Attempt attempt = attempts.findById(id).orElseThrow();
        attempt.setExpiresAt(expiry);
        attempt.setStatus(state);
        if ("SUBMITTED".equals(state)) {
            attempt.setSubmittedAt(NOW);
        }
        attempts.saveAndFlush(attempt);
        return id;
    }

    private long learnedWords() {
        return jdbc.queryForObject("select count(distinct ai.lexeme_id) from learning.attempt_items ai "
                + "join learning.attempts a on a.id=ai.attempt_id where a.user_id=? "
                + "and a.ref_type='TOPIC_STUDY' and ai.item_type='STUDY_CARD'", Long.class, owner);
    }
}
