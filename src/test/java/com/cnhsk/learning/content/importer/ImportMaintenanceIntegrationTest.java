package com.cnhsk.learning.content.importer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.auth.entity.User;
import com.cnhsk.auth.repository.UserRepository;
import com.cnhsk.learning.common.ServiceRequest;
import com.cnhsk.learning.common.ServiceRequestKind;
import com.cnhsk.learning.common.ServiceRequestRepository;
import com.cnhsk.learning.common.ServiceRequestStatus;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.importer.ImportMaintenance.ImportRecoveryRunner;
import com.cnhsk.learning.content.importer.write.TopicRowWriter;
import com.cnhsk.learning.content.repository.TopicRepository;
import com.cnhsk.shared.audit.AuditLogRepository;
import com.cnhsk.shared.audit.AuditService;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "cnhsk.import.recover-on-startup=true",
        "cnhsk.import.maintenance-cron=0 */15 * * * *"
})
class ImportMaintenanceIntegrationTest extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String ROOT = "/api/admin/imports";
    private static final String PRIVATE_MARKER = "private-import-marker";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    @Autowired private ImportMaintenance maintenance;
    @Autowired private ConfigurableApplicationContext context;
    @Autowired private ServiceRequestRepository requests;
    @Autowired private UserRepository users;
    @Autowired private TopicRepository topics;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockitoSpyBean private Clock clock;
    @MockitoSpyBean private TopicRowWriter topicWriter;
    @MockitoSpyBean private AuditService audit;

    private User user;
    private TopicRowWriter writerTarget;
    private AuditService auditTarget;
    private Set<Long> topicBaseline;
    private Logger rootLogger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setup() {
        writerTarget = AopTestUtils.getUltimateTargetObject(topicWriter);
        auditTarget = AopTestUtils.getUltimateTargetObject(audit);
        doReturn(NOW).when(clock).instant();
        user = users.saveAndFlush(new User("maintenance-" + UUID.randomUUID() + "@example.test", PASSWORD_HASH, "T11 Test User"));
        topicBaseline = topics.findAll().stream().map(Topic::getId).collect(Collectors.toSet());
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void cleanup() {
        rootLogger.detachAppender(logs);
        logs.stop();
        reset(writerTarget, auditTarget, clock);
        auditLogs.deleteAllInBatch(auditLogs.findAll().stream().filter(a -> user.getId().equals(a.getActorId())).toList());
        requests.deleteAllInBatch(requests.findAll().stream().filter(r -> user.getId().equals(r.getUserId())).toList());
        topics.deleteAllByIdInBatch(topics.findAll().stream().map(Topic::getId).filter(id -> !topicBaseline.contains(id)).toList());
        users.delete(user);
    }

    @Test
    void FR022_AT27_applicationStartupRecoversAn11MinuteOldRunningSince() {
        ServiceRequest run = running(NOW.minusSeconds(660));
        restartRecovery();
        ServiceRequest stored = stored(run);
        assertThat(stored.getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(stored.getErrorMessage()).isEqualTo("INTERRUPTED");
        assertThat(stored.getPayload()).isEqualTo(run.getPayload());
        assertThat(stored.getCompletedAt()).isNull();
    }

    @Test
    void FR022_startupKeepsFiveMinuteRunEvenThoughPreviewWasCreated25MinutesAgo() {
        ServiceRequest run = running(NOW.minusSeconds(300));
        restartRecovery();
        ServiceRequest stored = stored(run);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW.minusSeconds(1500));
        assertThat(stored.getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);
        assertThat(stored.getErrorMessage()).isNull();
    }

    @Test
    void FR022_startupRecoversLegacyRunWithoutTimestampAndLogsOnlyItsId() {
        ServiceRequest run = running(null);
        restartRecovery();
        assertThat(stored(run).getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(stored(run).getErrorMessage()).isEqualTo("INTERRUPTED");
        assertThat(logs.list.stream().filter(e -> e.getLoggerName().equals(ImportMaintenance.class.getName())).toList())
                .singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getFormattedMessage()).contains("runId=" + run.getId());
                    assertThat(event.getThrowableProxy()).isNull();
                });
        assertPrivateDataAbsentFromLogs();
    }

    @Test
    void FR022_scheduledRecoveryAfterQuickRestartReleasesTheGlobalImportSlot() throws Exception {
        ServiceRequest interrupted = running(NOW.minusSeconds(180));
        restartRecovery();
        assertThat(stored(interrupted).getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);

        doReturn(NOW.plusSeconds(660)).when(clock).instant();
        maintenance.expirePreviews();
        assertThat(stored(interrupted).getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(stored(interrupted).getErrorMessage()).isEqualTo("INTERRUPTED");
        JsonNode result = confirm(upload(), 200);
        assertThat(result.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("result").path("created").asInt()).isEqualTo(1);
    }

    @Test
    void FR022_runAtExactlyTenMinutesIsNotInterrupted() {
        ServiceRequest run = running(NOW.minusSeconds(600));
        maintenance.recoverInterrupted();
        assertThat(stored(run).getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestKind.class, names = "IMPORT", mode = EnumSource.Mode.EXCLUDE)
    void FR022_otherRunningWorkflowsAreNotRecoveredOrWarnedAbout(ServiceRequestKind kind) {
        ServiceRequest other = request(kind, ServiceRequestStatus.RUNNING, NOW.minusSeconds(1));
        maintenance.recoverInterrupted();
        assertThat(stored(other).getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);
        assertThat(stored(other).getErrorMessage()).isNull();
        assertThat(logs.list).noneMatch(e -> e.getLoggerName().equals(ImportMaintenance.class.getName()));
    }

    @ParameterizedTest
    @EnumSource(value = ServiceRequestStatus.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
    void FR022_recoveryLeavesEveryNonRunningImportUnchanged(ServiceRequestStatus status) {
        ServiceRequest run = request(ServiceRequestKind.IMPORT, status, NOW.minusSeconds(1));
        maintenance.recoverInterrupted();
        assertThat(stored(run).getStatus()).isEqualTo(status);
        assertThat(stored(run).getErrorMessage()).isNull();
    }

    @Test
    void FR022_confirmCommitsRunningSinceWithTheClaimBeforeAnyRowIsWritten() throws Exception {
        doReturn(NOW.minusSeconds(1500)).when(clock).instant();
        long id = upload();
        doReturn(NOW).when(clock).instant();
        doAnswer(invocation -> {
            ServiceRequest writing = requests.findById(id).orElseThrow();
            assertThat(writing.getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);
            assertThat(writing.getPayload()).containsEntry("runningSince", NOW.toString()).containsKey("validRows");
            return invocation.callRealMethod();
        }).when(writerTarget).write(any());
        confirm(id, 200);
        assertThat(requests.findById(id).orElseThrow().getPayload()).containsOnlyKeys("dataType", "fileName", "totalRows", "totals");
    }

    @Test
    void FR022_recoveryPreservesRowsThatCommittedBeforeAnInterruptedFinalization() throws Exception {
        long id = upload();
        doThrow(new IllegalStateException("Synthetic audit outage")).when(auditTarget)
                .record(anyLong(), eq("IMPORT_CONFIRM"), eq("import:" + id), isNull(), any());
        confirm(id, 500);
        ServiceRequest interrupted = requests.findById(id).orElseThrow();
        assertThat(interrupted.getStatus()).isEqualTo(ServiceRequestStatus.RUNNING);
        assertThat(interrupted.getPayload()).containsEntry("runningSince", NOW.toString());
        Set<Long> committedTopics = topics.findAll().stream().map(Topic::getId).collect(Collectors.toSet());
        assertThat(committedTopics).hasSize(topicBaseline.size() + 1);
        doReturn(NOW.plusSeconds(660)).when(clock).instant();
        maintenance.recoverInterrupted();
        assertThat(requests.findById(id).orElseThrow().getStatus()).isEqualTo(ServiceRequestStatus.FAILED);
        assertThat(requests.findById(id).orElseThrow().getErrorMessage()).isEqualTo("INTERRUPTED");
        assertThat(topics.findAll().stream().map(Topic::getId).collect(Collectors.toSet())).isEqualTo(committedTopics);
    }

    @Test
    void FR024_AT28_jobExpiresOnlyOverduePendingImportsAndPreservesReportMetadata() {
        ServiceRequest overdue = request(ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING, NOW.minusSeconds(1));
        ServiceRequest atDeadline = request(ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING, NOW);
        ServiceRequest future = request(ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING, NOW.plusSeconds(1));
        ServiceRequest completed = request(ServiceRequestKind.IMPORT, ServiceRequestStatus.COMPLETED, NOW.minusSeconds(1));
        ServiceRequest other = request(ServiceRequestKind.TRANSLATE, ServiceRequestStatus.PENDING, NOW.minusSeconds(1));
        maintenance.expirePreviews();
        ServiceRequest expired = stored(overdue);
        assertThat(expired.getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        assertThat(expired.getPayload()).doesNotContainKey("validRows").containsEntry("fileName", PRIVATE_MARKER + ".csv")
                .containsEntry("dataType", "TOPIC").containsEntry("totalRows", 1);
        assertThat(expired.getErrorRows()).isEqualTo(overdue.getErrorRows());
        assertThat(stored(atDeadline).getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        assertThat(stored(future).getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        assertThat(stored(completed).getStatus()).isEqualTo(ServiceRequestStatus.COMPLETED);
        assertThat(stored(other).getStatus()).isEqualTo(ServiceRequestStatus.PENDING);
        assertThat(stored(atDeadline).getPayload()).containsKey("validRows");
        maintenance.expirePreviews();
        assertThat(stored(overdue).getPayload()).isEqualTo(expired.getPayload());
        assertThat(topics.count()).isEqualTo(topicBaseline.size());
    }

    @Test
    void FR024_lazyExpiryCommitsEvenIfTheCallingTransactionLaterRollsBack() {
        ServiceRequest overdue = request(ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING, NOW.minusSeconds(1));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            maintenance.expireOnAccess(overdue.getId());
            status.setRollbackOnly();
        });
        assertThat(stored(overdue).getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        assertThat(stored(overdue).getPayload()).doesNotContainKey("validRows");
        maintenance.expireOnAccess(overdue.getId());
        assertThat(stored(overdue).getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
    }

    @Test
    void FR024_confirmStillExpiresLazilyAndCommitsBeforeReturningGone() throws Exception {
        long id = upload();
        doReturn(NOW.plusSeconds(1801)).when(clock).instant();
        JsonNode error = confirm(id, 410);
        assertThat(error.path("error_code").asText()).isEqualTo("IMPORT_PREVIEW_EXPIRED");
        ServiceRequest stored = requests.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        assertThat(stored.getPayload()).doesNotContainKeys("validRows", "runningSince");
        assertThat(topics.count()).isEqualTo(topicBaseline.size());
    }

    @Test
    void FR024_expiryJobIsRegisteredWithTheRequiredCronAndVietnamZone() throws Exception {
        Map<String, ScheduledTaskHolder> holders = context.getBeansOfType(ScheduledTaskHolder.class);
        assertThat(holders).as("Scheduled task holders").isNotEmpty();
        ServiceRequest overdue = request(ServiceRequestKind.IMPORT, ServiceRequestStatus.PENDING, NOW.minusSeconds(1));
        assertThat(holders.values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .filter(task -> task.getTask().getRunnable().toString().equals(ImportMaintenance.class.getName() + ".expirePreviews"))
                .toList()).singleElement().satisfies(task -> {
                    assertThat(task.getTask()).isInstanceOfSatisfying(CronTask.class,
                            cron -> assertThat(cron.getExpression()).isEqualTo("0 */15 * * * *"));
                    task.getTask().getRunnable().run();
                });
        assertThat(stored(overdue).getStatus()).isEqualTo(ServiceRequestStatus.EXPIRED);
        Scheduled scheduled = ImportMaintenance.class.getMethod("expirePreviews").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("${cnhsk.import.maintenance-cron:0 */15 * * * *}");
        assertThat(scheduled.zone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void FR025_AT29_uploadAndConfirmNeverLogFileNamesCellsOrWriterExceptionContents(boolean failRow) throws Exception {
        long id = upload();
        if (failRow) doThrow(new DataIntegrityViolationException(PRIVATE_MARKER + " synthetic SQL detail"))
                .when(writerTarget).write(any());
        JsonNode result = confirm(id, 200);
        assertThat(result.path("result").path("failed").asInt()).isEqualTo(failRow ? 1 : 0);
        assertPrivateDataAbsentFromLogs();
        assertThat(requests.findById(id).orElseThrow().getErrorRows().toString()).doesNotContain(PRIVATE_MARKER);
    }

    private ServiceRequest running(Instant since) {
        ServiceRequest run = request(ServiceRequestKind.IMPORT, ServiceRequestStatus.RUNNING, NOW.plusSeconds(300));
        if (since != null) {
            Map<String, Object> payload = new LinkedHashMap<>(run.getPayload());
            payload.put("runningSince", since.toString());
            run.setPayload(payload);
            requests.saveAndFlush(run);
        }
        jdbc.update("UPDATE learning.service_requests SET created_at = ? WHERE id = ?",
                Timestamp.from(NOW.minusSeconds(1500)), run.getId());
        return requests.findById(run.getId()).orElseThrow();
    }

    private ServiceRequest request(ServiceRequestKind kind, ServiceRequestStatus status, Instant expiry) {
        ServiceRequest run = new ServiceRequest(user.getId(), kind);
        run.setStatus(status);
        run.setPayload(Map.of("dataType", "TOPIC", "fileName", PRIVATE_MARKER + ".csv", "totalRows", 1,
                "expiresAt", expiry.toString(), "validRows", List.of(Map.of("lineNo", 2,
                        "values", Map.of("name", PRIVATE_MARKER)))));
        run.setErrorRows(List.of(Map.of("line", 3, "code", "INVALID_VALUE", "message", PRIVATE_MARKER)));
        if (kind == ServiceRequestKind.GRADING) ReflectionTestUtils.setField(run, "deadlineAt", NOW.plusSeconds(3600));
        return requests.saveAndFlush(run);
    }

    private ServiceRequest stored(ServiceRequest run) {
        return requests.findById(run.getId()).orElseThrow();
    }

    private void restartRecovery() {
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(RecoveryStartup.class)
                .web(WebApplicationType.NONE).properties("spring.main.banner-mode=off")
                .initializers(restart -> {
                    // Boot may reset Logback while starting a second application in this JVM.
                    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                    logs.start();
                    rootLogger.addAppender(logs);
                    restart.getBeanFactory().registerSingleton("testRequests", requests);
                    restart.getBeanFactory().registerSingleton("testEntityManager", entityManager);
                    restart.getBeanFactory().registerSingleton("testClock", clock);
                    restart.getBeanFactory().registerSingleton("testTransactionManager", transactionManager);
                }).run()) {
            assertThat(restarted.getBean(ImportRecoveryRunner.class)).isNotNull();
        }
    }

    private long upload() throws Exception {
        String code = "T11_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        String csv = "code,name,hsk_level\n" + code + "," + PRIVATE_MARKER + ",1\n";
        var response = mvc.perform(multipart(ROOT)
                        .file(new MockMultipartFile("file", PRIVATE_MARKER + ".csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("dataType", "TOPIC").header("Authorization", "Bearer " + token()))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("runId").asLong();
    }

    private JsonNode confirm(long id, int expectedStatus) throws Exception {
        var response = mvc.perform(post(ROOT + "/" + id + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{}").header("Authorization", "Bearer " + token()))
                .andExpect(status().is(expectedStatus)).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String token() {
        return jwt.issueAccessToken(user.getId(), List.of("CONTENT_EDITOR"), true, UUID.randomUUID());
    }

    private void assertPrivateDataAbsentFromLogs() {
        String output = logs.list.stream().map(event -> event.getFormattedMessage()
                + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .collect(Collectors.joining("\n"));
        assertThat(output).doesNotContain(PRIVATE_MARKER, user.getEmail(), PASSWORD_HASH);
    }

    @Import({ImportMaintenance.class, ImportRecoveryRunner.class})
    static class RecoveryStartup { }
}
