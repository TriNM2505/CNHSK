package com.cnhsk.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import com.cnhsk.auth.event.EmailVerificationRequestedEvent;
import com.cnhsk.auth.event.PasswordResetRequestedEvent;
import com.cnhsk.auth.event.RegistrationCodeRequestedEvent;
import com.cnhsk.shared.config.CnhskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class MailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private MailService mailService;

    @BeforeEach
    void setUp() {
        CnhskProperties properties = new CnhskProperties();
        properties.getApp().setWebBaseUrl(java.net.URI.create("https://cnhsk.test/"));
        properties.getApp().setApiBaseUrl(java.net.URI.create("https://api.cnhsk.test/"));
        mailService = new MailService(mailSender, properties, "noreply@cnhsk.test");
    }

    @Test
    void buildsVietnameseVerificationEmailWithLinkAndExpiry() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        mailService.sendVerificationEmail(new EmailVerificationRequestedEvent("learner@cnhsk.test", "verify-token"));

        MimeMessage message = sentMessage();
        assertEquals("CNHSK <noreply@cnhsk.test>", message.getFrom()[0].toString());
        assertEquals("learner@cnhsk.test", message.getRecipients(MimeMessage.RecipientType.TO)[0].toString());
        assertEquals("Xác thực email CNHSK", message.getSubject());
        message.saveChanges();
        for (String part : java.util.List.of("text/plain", "text/html")) {
            org.assertj.core.api.Assertions.assertThat(contentOf(message, part))
                    .contains("https://cnhsk.test/verify-email?token=verify-token", "24 giờ");
        }
        org.assertj.core.api.Assertions.assertThat(contentOf(message, "text/html"))
                .contains(">Xác thực email</a>", "cid:hoc-ba-logo");
    }

    @Test
    void buildsVietnameseRegistrationCodeEmailWithExpiry() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        mailService.sendRegistrationCode(new RegistrationCodeRequestedEvent("learner@cnhsk.test", "042917"));

        MimeMessage message = sentMessage();
        assertEquals("learner@cnhsk.test", message.getRecipients(MimeMessage.RecipientType.TO)[0].toString());
        assertEquals("042917 là mã xác minh CNHSK", message.getSubject());
        message.saveChanges();
        for (String part : java.util.List.of("text/plain", "text/html")) {
            org.assertj.core.api.Assertions.assertThat(contentOf(message, part)).contains("042917", "10 phút");
        }
        org.assertj.core.api.Assertions.assertThat(contentOf(message, "text/html")).doesNotContain("<a ");
        org.assertj.core.api.Assertions.assertThat(contentOf(message, "image/png")).isNotNull();
    }

    @Test
    void buildsVietnamesePasswordResetEmailWithLinkAndExpiry() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        mailService.sendPasswordResetEmail(new PasswordResetRequestedEvent("learner@cnhsk.test", "reset-token"));

        MimeMessage message = sentMessage();
        assertEquals("Đặt lại mật khẩu CNHSK", message.getSubject());
        message.saveChanges();
        for (String part : java.util.List.of("text/plain", "text/html")) {
            org.assertj.core.api.Assertions.assertThat(contentOf(message, part))
                    .contains("https://cnhsk.test/reset-password?token=reset-token", "1 giờ");
        }
    }

    @Test
    void registrationCodeFailureReachesTheCallerWithoutLoggingTheCodeOrAddress() {
        String email = "learner.private@cnhsk.test";
        MailSendException failure = new MailSendException("SMTP unavailable for " + email + " and 042917");
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        doThrow(failure).when(mailSender).send(any(MimeMessage.class));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> mailService.sendRegistrationCode(
                    new RegistrationCodeRequestedEvent(email, "042917"))).isSameAs(failure);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        org.assertj.core.api.Assertions.assertThat(appender.list).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(appender.list.get(0).getThrowableProxy()).isNull();
        org.assertj.core.api.Assertions.assertThat(appender.list.get(0).getFormattedMessage())
                .contains("l***@cnhsk.test").doesNotContain(email, "042917");
    }

    @Test
    void smtpFailureIsSwallowedSoItCannotFailTheCommittedBusinessOperation() {
        String email = "learner.private@cnhsk.test";
        String secret = "secret-token";
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        doThrow(new MailSendException("SMTP unavailable for " + email + " and " + secret))
                .when(mailSender).send(any(MimeMessage.class));

        try {
            mailService.sendVerificationEmail(new EmailVerificationRequestedEvent(email, secret));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        verify(mailSender).send(any(MimeMessage.class));
        String logOutput = appender.list.toString();
        org.junit.jupiter.api.Assertions.assertTrue(logOutput.contains("MailSendException"));
        org.junit.jupiter.api.Assertions.assertTrue(logOutput.contains("l***@cnhsk.test"));
        org.junit.jupiter.api.Assertions.assertFalse(logOutput.contains(email));
        org.junit.jupiter.api.Assertions.assertFalse(logOutput.contains(secret));
        org.junit.jupiter.api.Assertions.assertFalse(appender.list.get(0).getThrowableProxy() != null);
    }

    @Test
    void backgroundSendingReturnsBeforeASlowMailServerAnswersAndFinishesOnShutdown() throws Exception {
        java.util.concurrent.CountDownLatch slowServer = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Thread> sender = new java.util.concurrent.atomic.AtomicReference<>();
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        org.mockito.Mockito.doAnswer(invocation -> {
            sender.set(Thread.currentThread());
            slowServer.await(20, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
        CnhskProperties properties = new CnhskProperties();
        properties.getApp().setWebBaseUrl(java.net.URI.create("https://cnhsk.test/"));
        properties.getApp().setApiBaseUrl(java.net.URI.create("https://api.cnhsk.test/"));
        MailService background = new MailService(mailSender, properties, "noreply@cnhsk.test", true);

        background.sendVerificationEmail(new EmailVerificationRequestedEvent("learner@cnhsk.test", "verify-token"));

        org.assertj.core.api.Assertions.assertThat(slowServer.getCount()).isEqualTo(1);
        verify(mailSender, org.mockito.Mockito.timeout(5000)).send(any(MimeMessage.class));
        org.assertj.core.api.Assertions.assertThat(sender.get()).isNotNull().isNotSameAs(Thread.currentThread());
        slowServer.countDown();
        background.stop();
    }

    @Test
    void fullBackgroundQueueDropsTheEmailInsteadOfFailingTheRequest() throws Exception {
        java.util.concurrent.CountDownLatch stuckServer = new java.util.concurrent.CountDownLatch(1);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> newMimeMessage());
        org.mockito.Mockito.doAnswer(invocation -> {
            stuckServer.await(20, java.util.concurrent.TimeUnit.SECONDS);
            return null;
        }).when(mailSender).send(any(MimeMessage.class));
        CnhskProperties properties = new CnhskProperties();
        properties.getApp().setWebBaseUrl(java.net.URI.create("https://cnhsk.test/"));
        properties.getApp().setApiBaseUrl(java.net.URI.create("https://api.cnhsk.test/"));
        MailService background = new MailService(mailSender, properties, "noreply@cnhsk.test", true);
        try {
            for (int index = 0; index < 210; index++) {
                background.sendPasswordResetEmail(new PasswordResetRequestedEvent("learner@cnhsk.test", "reset-token"));
            }
        } finally {
            stuckServer.countDown();
            background.stop();
        }
    }

    @Test
    void buildsMultipartStudyReminderAndEscapesDisplayName() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());

        mailService.sendStudyReminder("learner@cnhsk.test", "<script>alert('x')</script> & Learner", 7, "signed-token");

        MimeMessage message = sentMessage();
        message.saveChanges();
        assertEquals("CNHSK <noreply@cnhsk.test>", message.getFrom()[0].toString());
        assertEquals("CNHSK · Bạn có 7 thẻ cần ôn hôm nay", message.getSubject());
        assertEquals("<https://api.cnhsk.test/api/public/reminder/unsubscribe?token=signed-token>",
                message.getHeader("List-Unsubscribe", null));
        assertEquals("List-Unsubscribe=One-Click", message.getHeader("List-Unsubscribe-Post", null));
        org.assertj.core.api.Assertions.assertThat(message.isMimeType("multipart/*")).isTrue();
        String text = contentOf(message, "text/plain");
        String html = contentOf(message, "text/html");
        org.assertj.core.api.Assertions.assertThat(text)
                .contains("<script>alert('x')</script> & Learner", "7 thẻ", "https://cnhsk.test/learn/review",
                        "https://cnhsk.test/reminder/unsubscribe?token=signed-token");
        org.assertj.core.api.Assertions.assertThat(html)
                .contains("&lt;script&gt;", "&lt;/script&gt; &amp; Learner", "7 thẻ",
                        "href=\"https://cnhsk.test/learn/review\"", "Ôn ngay",
                        "href=\"https://cnhsk.test/reminder/unsubscribe?token=signed-token\"", "Tắt nhắc qua email")
                .doesNotContain("<script>");
    }

    @Test
    void studyReminderSmtpFailurePropagatesWithoutLoggingRecipientOrToken() {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        MailSendException failure = new MailSendException("simulated SMTP failure");
        doThrow(failure).when(mailSender).send(any(MimeMessage.class));
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MailService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> mailService.sendStudyReminder(
                            "private@cnhsk.test", "Private Learner", 4, "private-token"))
                    .isSameAs(failure);
            org.assertj.core.api.Assertions.assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void passwordResetSmtpFailureIsStillSwallowed() {
        when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
        doThrow(new MailSendException("simulated SMTP failure")).when(mailSender).send(any(MimeMessage.class));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> mailService.sendPasswordResetEmail(
                new PasswordResetRequestedEvent("reset@cnhsk.test", "reset-token")));
    }

    @Test
    void rejectsInvalidMailConfiguration() {
        CnhskProperties properties = new CnhskProperties();
        properties.getApp().setWebBaseUrl(java.net.URI.create("relative/path"));
        assertThrows(IllegalArgumentException.class,
                () -> new MailService(mailSender, properties, "noreply@cnhsk.test"));

        properties.getApp().setWebBaseUrl(java.net.URI.create("https://cnhsk.test"));
        assertThrows(IllegalArgumentException.class,
                () -> new MailService(mailSender, properties, " "));

        properties.getApp().setApiBaseUrl(java.net.URI.create("relative/api"));
        assertThrows(IllegalArgumentException.class,
                () -> new MailService(mailSender, properties, "noreply@cnhsk.test"));
    }

    private static String contentOf(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return part.getContent().toString();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                String content = contentOf(multipart.getBodyPart(index), mimeType);
                if (content != null) {
                    return content;
                }
            }
        }
        return null;
    }

    private MimeMessage sentMessage() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }
}
