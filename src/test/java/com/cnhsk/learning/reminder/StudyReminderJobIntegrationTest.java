package com.cnhsk.learning.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.review.ReviewSessionService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class StudyReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-14T13:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final LocalTime HOUR = LocalTime.of(20, 0);
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(12).encode("ReminderJobFixture123");
    private static final String PRIVATE_TOKEN = "private-job-token";

    @Autowired private StudyReminderJob job;
    @Autowired private StudyReminderClaims claims;
    @Autowired private ReviewSessionService reviews;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ObjectMapper mapper;
    @Autowired private ApplicationContext context;
    @Autowired private Environment environment;
    @MockitoSpyBean private AuthApi auth;
    @MockitoSpyBean private JavaMailSender sender;
    @MockitoBean private Clock clock;
    @MockitoBean private ReminderSleeper sleeper;
    @Value("${test.mailpit.base-url}") private String mailpitBaseUrl;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Learner> ownedUsers = new ArrayList<>();
    private final List<Long> ownedLexemes = new ArrayList<>();
    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        clearInbox();
        logger = (Logger) LoggerFactory.getLogger(StudyReminderJob.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void removeOwnedFixturesAndClearOwnInterrupt() throws Exception {
        Thread.interrupted();
        logger.detachAppender(logs);
        logs.stop();
        for (Learner user : ownedUsers) {
            jdbc.update("DELETE FROM learning.study_events WHERE user_id = ?", user.id());
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id = ?", user.id());
            jdbc.update("DELETE FROM auth.users WHERE id = ?", user.id());
        }
        for (long lexemeId : ownedLexemes) {
            jdbc.update("DELETE FROM learning.lexemes WHERE id = ?", lexemeId);
        }
        clearInbox();
    }

    @Test
    void AT06_realSummarySendsFiveDueCardsAfterClaimIsVisibleOnAnotherConnection() throws Exception {
        Learner user = learner();
        seedCards(user, 5, NOW.minusSeconds(60));
        seedCards(user, 2, NOW.plusSeconds(60));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(visibleClaimCount(user)).isEqualTo(1);
            return invocation.callRealMethod();
        }).when(auth).sendStudyReminder(user.id(), 5);

        job.run();

        JsonNode message = onlyMessageDetail();
        assertThat(message.path("Subject").asText()).isEqualTo("CNHSK · Bạn có 5 thẻ cần ôn hôm nay");
        assertThat(message.path("Text").asText()).contains(user.displayName(), "5 thẻ");
        MimeMessage raw = rawMessage(message.path("ID").asText());
        assertThat(((InternetAddress) raw.getRecipients(MimeMessage.RecipientType.TO)[0]).getAddress())
                .isEqualTo(user.email());
        assertThat(claimCount(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT local_date FROM learning.study_events WHERE user_id = ? "
                + "AND event_type = 'REMINDER_SENT'", LocalDate.class, user.id())).isEqualTo(TODAY);
        assertSummary("hour=20:00, candidates=1, sent=1, skippedNoCards=0, duplicate=0, skippedIneligible=0, failed=0");
        verifyNoInteractions(sleeper);
        assertLogPrivacy();
    }

    @Test
    void AT07_noCardsCreatesNeitherMailNorClaim() throws Exception {
        Learner user = learner();

        job.run();

        assertThat(messages()).isEmpty();
        assertThat(claimCount(user)).isZero();
        verify(auth, never()).sendStudyReminder(eq(user.id()), anyInt());
        assertSummary("hour=20:00, candidates=1, sent=0, skippedNoCards=1, duplicate=0, skippedIneligible=0, failed=0");
    }

    @Test
    void AT08_studyEarlierTodayDoesNotSuppressADueReminder() throws Exception {
        Learner user = learner();
        seedCards(user, 1, NOW.minusSeconds(60));
        jdbc.update("INSERT INTO learning.study_events(user_id,event_type,local_date) VALUES (?,'STUDY',?)",
                user.id(), TODAY);

        job.run();

        assertThat(messages()).hasSize(1);
        assertThat(claimCount(user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id = ? "
                + "AND event_type = 'STUDY'", Long.class, user.id())).isEqualTo(1);
    }

    @Test
    void AT09_repeatRunsAndChangedHourStillSendOnlyOnceToday() throws Exception {
        Learner user = learner();
        seedCards(user, 1, NOW.minusSeconds(60));

        job.run();
        job.run();
        jdbc.update("UPDATE auth.users SET reminder_time = TIME '21:00' WHERE id = ?", user.id());
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        job.run();

        assertThat(messages()).hasSize(1);
        assertThat(claimCount(user)).isEqualTo(1);
        verify(auth).sendStudyReminder(user.id(), 1);
        assertThat(logs.list.stream().filter(event -> event.getFormattedMessage().contains("duplicate=1")))
                .hasSize(2);
    }

    @Test
    void AT09_twoInstancesSeeTheCommittedClaimBeforeFirstSmtpAndSendOneMail() throws Exception {
        Learner user = learner();
        seedCards(user, 1, NOW.minusSeconds(60));
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            sending.countDown();
            assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(auth).sendStudyReminder(user.id(), 1);
        StudyReminderJob secondInstance = new StudyReminderJob(auth, reviews, claims, clock, sleeper);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(job::run);
            assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(secondInstance::run);
            second.get(10, TimeUnit.SECONDS);
            assertThat(messages()).isEmpty();
            assertThat(claimCount(user)).isEqualTo(1);
            release.countDown();
            first.get(15, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(messages()).hasSize(1);
        verify(auth).sendStudyReminder(user.id(), 1);
        assertThat(logs.list.stream().filter(event -> event.getFormattedMessage().contains("duplicate=1")))
                .hasSize(1);
    }

    @Test
    void AT10_smtpFailureRetriesAThenReleasesItsClaimAndStillDeliversToB() throws Exception {
        Learner a = learner();
        Learner b = learner();
        seedCards(a, 1, NOW.minusSeconds(60));
        seedCards(b, 1, NOW.minusSeconds(60));
        AtomicInteger failedAttempts = new AtomicInteger();
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            String recipient = ((InternetAddress) message.getRecipients(MimeMessage.RecipientType.TO)[0]).getAddress();
            if (recipient.equals(a.email())) {
                failedAttempts.incrementAndGet();
                throw new MailSendException(a.email() + " " + a.displayName() + " " + PRIVATE_TOKEN + " "
                        + message.getHeader("List-Unsubscribe", null));
            }
            return invocation.callRealMethod();
        }).when(sender).send(any(MimeMessage.class));

        job.run();

        assertThat(failedAttempts.get()).isEqualTo(3);
        verify(sleeper, times(2)).sleep(Duration.ofSeconds(2));
        assertThat(claimCount(a)).isZero();
        assertThat(claimCount(b)).isEqualTo(1);
        MimeMessage raw = rawMessage(onlyMessageDetail().path("ID").asText());
        assertThat(((InternetAddress) raw.getRecipients(MimeMessage.RecipientType.TO)[0]).getAddress())
                .isEqualTo(b.email());
        assertSummary("hour=20:00, candidates=2, sent=1, skippedNoCards=0, duplicate=0, skippedIneligible=0, failed=1");
        assertLogPrivacy();
    }

    @ParameterizedTest
    @ValueSource(strings = {"banned", "unverified", "suspended", "web"})
    void AT11_authExcludesIneligibleTargetsWithoutLearningReadingAccountState(String condition) throws Exception {
        Learner user = learner();
        seedCards(user, 1, NOW.minusSeconds(60));
        switch (condition) {
            case "banned" -> jdbc.update("UPDATE auth.users SET banned_at = ?, ban_reason = ? WHERE id = ?",
                    Timestamp.from(NOW), "Test ban", user.id());
            case "unverified" -> jdbc.update("UPDATE auth.users SET email_verified_at = NULL WHERE id = ?", user.id());
            case "suspended" -> jdbc.update("UPDATE auth.users SET suspended_until = ? WHERE id = ?",
                    Timestamp.from(NOW.plusSeconds(60)), user.id());
            default -> jdbc.update("UPDATE auth.users SET reminder_channels = 'WEB' WHERE id = ?", user.id());
        }

        job.run();

        assertThat(messages()).isEmpty();
        assertThat(claimCount(user)).isZero();
        verify(auth, never()).sendStudyReminder(eq(user.id()), anyInt());
        assertSummary("hour=20:00, candidates=0, sent=0, skippedNoCards=0, duplicate=0, skippedIneligible=0, failed=0");
    }

    @Test
    void Q_T04_01_accountLosesEmailChannelAfterClaimAndIsSkippedWithoutDelivery() throws Exception {
        Learner user = learner();
        seedCards(user, 1, NOW.minusSeconds(60));
        doAnswer(invocation -> {
            assertThat(visibleClaimCount(user)).isEqualTo(1);
            jdbc.update("UPDATE auth.users SET reminder_channels = 'WEB' WHERE id = ?", user.id());
            return invocation.callRealMethod();
        }).when(auth).sendStudyReminder(user.id(), 1);

        job.run();

        assertThat(messages()).isEmpty();
        assertThat(claimCount(user)).isZero();
        verify(auth).sendStudyReminder(user.id(), 1);
        verifyNoInteractions(sleeper);
        assertSummary("hour=20:00, candidates=1, sent=0, skippedNoCards=0, duplicate=0, skippedIneligible=1, failed=0");
    }

    @Test
    void Q_T04_02_interruptedRetryReleasesRealClaimStopsAndRestoresFlag() throws Exception {
        Learner a = learner();
        Learner b = learner();
        seedCards(a, 1, NOW.minusSeconds(60));
        seedCards(b, 1, NOW.minusSeconds(60));
        doThrow(new MailSendException(PRIVATE_TOKEN)).when(sender).send(any(MimeMessage.class));
        doThrow(new InterruptedException(PRIVATE_TOKEN)).when(sleeper).sleep(Duration.ofSeconds(2));

        job.run();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
        assertThat(claimCount(a)).isZero();
        assertThat(claimCount(b)).isZero();
        assertThat(messages()).isEmpty();
        verify(auth, never()).sendStudyReminder(eq(b.id()), anyInt());
        assertSummary("hour=20:00, candidates=2, sent=0, skippedNoCards=0, duplicate=0, skippedIneligible=0, failed=1");
        assertLogPrivacy();
    }

    @Test
    void FR007_midnightUsesVietnamDateAndPreviousDayClaimDoesNotBlock() throws Exception {
        Learner user = learner();
        seedCards(user, 1, NOW.minusSeconds(86_400));
        jdbc.update("UPDATE auth.users SET reminder_time = TIME '00:00' WHERE id = ?", user.id());
        assertThat(claims.claim(user.id(), TODAY.minusDays(1))).isPresent();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-13T17:00:00Z"));

        job.run();

        assertThat(messages()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id = ? "
                + "AND local_date = ? AND channel = 'EMAIL'", Long.class, user.id(), TODAY)).isEqualTo(1);
        assertSummary("hour=00:00, candidates=1, sent=1, skippedNoCards=0, duplicate=0, skippedIneligible=0, failed=0");
    }

    @Test
    void FR010_claimAndReleaseCommitIndependentlyOfCallingTransactionRollback() {
        Learner user = learner();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        OptionalLong claim = outer.execute(status -> {
            OptionalLong result = claims.claim(user.id(), TODAY);
            status.setRollbackOnly();
            return result;
        });
        assertThat(claim).isPresent();
        assertThat(claimCount(user)).isEqualTo(1);

        outer.executeWithoutResult(status -> {
            claims.release(claim.getAsLong());
            status.setRollbackOnly();
        });

        assertThat(claimCount(user)).isZero();
    }

    @Test
    void FR010_releasingStaleClaimIdPreservesNewClaimOtherUsersDatesAndChannels() {
        Learner a = learner();
        Learner b = learner();
        long stale = claims.claim(a.id(), TODAY).orElseThrow();
        claims.release(stale);
        long replacement = claims.claim(a.id(), TODAY).orElseThrow();
        long otherUser = claims.claim(b.id(), TODAY).orElseThrow();
        long otherDate = claims.claim(a.id(), TODAY.plusDays(1)).orElseThrow();
        long web = jdbc.queryForObject("INSERT INTO learning.study_events(user_id,event_type,channel,local_date) "
                + "VALUES (?,'REMINDER_SENT','WEB',?) RETURNING id", Long.class, a.id(), TODAY);
        long study = jdbc.queryForObject("INSERT INTO learning.study_events(user_id,event_type,local_date) "
                + "VALUES (?,'STUDY',?) RETURNING id", Long.class, a.id(), TODAY);

        claims.release(stale);
        claims.release(web);
        claims.release(study);

        assertThat(jdbc.queryForList("SELECT id FROM learning.study_events WHERE user_id IN (?,?) ORDER BY id",
                Long.class, a.id(), b.id())).containsExactly(replacement, otherUser, otherDate, web, study);
    }

    @Test
    void AT19_productionDefaultsAndDisabledTestCronKeepJobBeanWithoutExtraScheduler() throws Exception {
        var production = new YamlPropertySourceLoader().load("reminder-production",
                new ClassPathResource("application.yaml")).get(0);
        assertThat(production.getProperty("cnhsk.reminder.cron")).isEqualTo("0 0 * * * *");
        assertThat(production.getProperty("cnhsk.reminder.batch-size")).isEqualTo(500);
        assertThat(environment.getProperty("cnhsk.reminder.cron")).isEqualTo("-");
        assertThat(context.getBean(StudyReminderJob.class)).isSameAs(job);
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).hasSize(1);
        assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
    }

    private Learner learner() {
        String email = "reminder-job-" + UUID.randomUUID() + "@example.test";
        String displayName = "Reminder Job Learner " + UUID.randomUUID();
        long id = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name,email_verified_at,"
                        + "reminder_time,reminder_channels) VALUES (?,?,?,?,?,'WEB,EMAIL') RETURNING id",
                Long.class, email, PASSWORD_HASH, displayName, Timestamp.from(NOW.minusSeconds(60)), HOUR);
        Learner user = new Learner(id, email, displayName);
        ownedUsers.add(user);
        return user;
    }

    private void seedCards(Learner user, int count, Instant dueAt) {
        for (int index = 0; index < count; index++) {
            long lexeme = jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,meaning_vi,hsk_level) "
                    + "VALUES ('WORD',?,'Nghĩa kiểm thử tự soạn',1) RETURNING id", Long.class,
                    "reminder-fixture-" + UUID.randomUUID());
            ownedLexemes.add(lexeme);
            jdbc.update("INSERT INTO learning.user_progress(user_id,target_type,target_id,stability,difficulty,"
                            + "review_count,lapse_count,last_seen_at,next_review_at) VALUES (?,'LEXEME',?,1,5,0,0,?,?)",
                    user.id(), lexeme, Timestamp.from(dueAt.minusSeconds(86_400)), Timestamp.from(dueAt));
        }
    }

    private long claimCount(Learner user) {
        return jdbc.queryForObject("SELECT count(*) FROM learning.study_events WHERE user_id = ? "
                + "AND event_type = 'REMINDER_SENT' AND channel = 'EMAIL'", Long.class, user.id());
    }

    private long visibleClaimCount(Learner user) throws Exception {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT count(*) FROM learning.study_events "
                        + "WHERE user_id = ? AND event_type = 'REMINDER_SENT' AND channel = 'EMAIL'")) {
            statement.setLong(1, user.id());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private JsonNode messages() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/messages")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).path("messages");
    }

    private JsonNode onlyMessageDetail() throws Exception {
        JsonNode inbox = messages();
        assertThat(inbox).hasSize(1);
        String id = inbox.get(0).path("ID").asText();
        HttpResponse<String> response = http.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + id)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    private MimeMessage rawMessage(String id) throws Exception {
        assertThat(id).isNotBlank();
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/message/" + id + "/raw")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(response.body()));
    }

    private void clearInbox() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/messages")).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    private void assertSummary(String fields) {
        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("Study reminder run ")).toList())
                .containsExactly("Study reminder run " + fields);
    }

    private void assertLogPrivacy() {
        assertThat(logs.list).allSatisfy(event -> {
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage()).doesNotContain(PRIVATE_TOKEN, "token=");
            for (Learner user : ownedUsers) {
                assertThat(event.getFormattedMessage()).doesNotContain(user.email(), user.displayName());
            }
        });
    }

    private record Learner(long id, String email, String displayName) { }
}
