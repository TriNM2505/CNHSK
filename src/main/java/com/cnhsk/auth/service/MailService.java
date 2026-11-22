package com.cnhsk.auth.service;

import java.net.URI;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.cnhsk.auth.event.EmailVerificationRequestedEvent;
import com.cnhsk.auth.event.PasswordResetRequestedEvent;
import com.cnhsk.auth.event.RegistrationCodeRequestedEvent;
import com.cnhsk.shared.config.CnhskProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;
import jakarta.annotation.PreDestroy;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends authentication links after commit on background threads, so a slow mail server neither delays the
 * response nor holds its database connection. Registration codes are sent on the calling thread because the
 * screen asks for the code next and must know when it never left. Study reminders stay synchronous for job
 * retries.
 */
@Service
public class MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);
    private static final String VERIFY_PATH = "/verify-email";
    private static final String RESET_PATH = "/reset-password";
    private static final int SENDER_THREADS = 2;
    private static final int QUEUE_CAPACITY = 200;
    private static final long SHUTDOWN_SECONDS = 30;
    private static final ClassPathResource LOGO = new ClassPathResource("email/hoc-ba-logo.png");

    private final JavaMailSender mailSender;
    private final URI webBaseUrl;
    private final URI apiBaseUrl;
    private final String fromAddress;
    private final ExecutorService senders;

    /** Sends on the calling thread. */
    public MailService(JavaMailSender mailSender, CnhskProperties properties, String fromAddress) {
        this(mailSender, properties, fromAddress, false);
    }

    @Autowired
    public MailService(JavaMailSender mailSender, CnhskProperties properties,
            @Value("${MAIL_FROM:noreply@cnhsk.com}") String fromAddress,
            @Value("${cnhsk.mail.background:true}") boolean background) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null.");
        this.webBaseUrl = Objects.requireNonNull(properties, "properties must not be null.")
                .getApp().getWebBaseUrl();
        if (webBaseUrl == null || !webBaseUrl.isAbsolute()) {
            throw new IllegalArgumentException("APP_WEB_BASE_URL must be an absolute URL.");
        }
        this.apiBaseUrl = properties.getApp().getApiBaseUrl();
        if (apiBaseUrl == null || !apiBaseUrl.isAbsolute()) {
            throw new IllegalArgumentException("API_BASE_URL must be an absolute URL.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("MAIL_FROM must not be blank.");
        }
        this.fromAddress = fromAddress;
        this.senders = background ? new ThreadPoolExecutor(SENDER_THREADS, SENDER_THREADS, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> new Thread(task, "auth-mail")) : null;
    }

    /** Lets queued emails leave before the application stops. */
    @PreDestroy
    void stop() throws InterruptedException {
        if (senders != null) {
            senders.shutdown();
            if (!senders.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                senders.shutdownNow();
            }
        }
    }

    /** Throws when the mail server refuses the code, so the caller can report it. */
    @EventListener
    public void sendRegistrationCode(RegistrationCodeRequestedEvent event) {
        deliver(event.getRecipient(), AuthEmail.withCode(event.getCode() + " là mã xác minh CNHSK",
                "Mã xác minh của bạn",
                "Nhập mã dưới đây ở trang đăng ký để tạo tài khoản CNHSK.",
                event.getCode(),
                "Mã có hiệu lực trong 10 phút và chỉ dùng một lần. Đừng đưa mã này cho người khác. "
                        + "Nếu bạn không tạo tài khoản, hãy bỏ qua email này."));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(EmailVerificationRequestedEvent event) {
        dispatch(event.getRecipient(), AuthEmail.withLink("Xác thực email CNHSK",
                "Xác thực email của bạn",
                "Mở liên kết dưới đây để xác thực địa chỉ email của tài khoản CNHSK.",
                "Xác thực email", link(VERIFY_PATH, event.getToken()),
                "Liên kết có hiệu lực trong 24 giờ. Nếu bạn không tạo tài khoản, hãy bỏ qua email này."));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordResetEmail(PasswordResetRequestedEvent event) {
        dispatch(event.getRecipient(), AuthEmail.withLink("Đặt lại mật khẩu CNHSK",
                "Đặt lại mật khẩu",
                "Có yêu cầu đặt lại mật khẩu cho tài khoản CNHSK của bạn. Mở liên kết dưới đây để chọn mật khẩu mới.",
                "Chọn mật khẩu mới", link(RESET_PATH, event.getToken()),
                "Liên kết có hiệu lực trong 1 giờ và chỉ dùng một lần. Nếu bạn không yêu cầu, hãy bỏ qua email này; "
                        + "mật khẩu hiện tại không thay đổi."));
    }

    public void sendStudyReminder(String recipient, String displayName, int dueCount, String token) {
        String reviewUrl = webBaseUrl.toString().replaceFirst("/+$", "") + "/learn/review";
        String unsubscribeUrl = link("/reminder/unsubscribe", token);
        String text = "Xin chào " + displayName + ",\n\n"
                + "Bạn có " + dueCount + " thẻ cần ôn hôm nay.\n\n"
                + "Ôn ngay: " + reviewUrl + "\n\n"
                + "Tắt nhắc qua email: " + unsubscribeUrl + "\n\nCNHSK";
        String html = "<html><body><p>Xin chào " + HtmlUtils.htmlEscape(displayName) + ",</p>"
                + "<p>Bạn có " + dueCount + " thẻ cần ôn hôm nay.</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(reviewUrl) + "\">Ôn ngay</a></p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(unsubscribeUrl) + "\">Tắt nhắc qua email</a></p>"
                + "<p>CNHSK</p></body></html>";
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, "CNHSK", StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject("CNHSK · Bạn có " + dueCount + " thẻ cần ôn hôm nay");
            helper.setText(text, html);
            message.setHeader("List-Unsubscribe", "<" + link(apiBaseUrl,
                    "/api/public/reminder/unsubscribe", token) + ">");
            message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new IllegalStateException("Could not prepare study reminder email.", exception);
        }
        // Let SMTP failures reach the caller so the reminder job can retry delivery.
        mailSender.send(message);
    }

    private void dispatch(String recipient, AuthEmail email) {
        if (senders == null) {
            send(recipient, email);
            return;
        }
        try {
            senders.execute(() -> send(recipient, email));
        } catch (RejectedExecutionException exception) {
            LOGGER.warn("Authentication email queue is full; recipient={}", maskEmail(recipient));
        }
    }

    private void send(String recipient, AuthEmail email) {
        try {
            deliver(recipient, email);
        } catch (MailException exception) {
            // Failed links are requested again by the user; the failure is already logged.
        }
    }

    private void deliver(String recipient, AuthEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, "CNHSK", StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            helper.addInline(AuthEmail.LOGO_CONTENT_ID, LOGO, "image/png");
            mailSender.send(message);
        } catch (MailException exception) {
            logFailure(exception, recipient);
            throw exception;
        } catch (MessagingException | UnsupportedEncodingException exception) {
            logFailure(exception, recipient);
            throw new MailPreparationException("Could not prepare authentication email.", exception);
        }
    }

    private static void logFailure(Exception exception, String recipient) {
        // Do not log the exception itself: provider messages can contain recipient or message details.
        LOGGER.warn("Could not send authentication email; mail error type={}, recipient={}",
                exception.getClass().getSimpleName(), maskEmail(recipient));
    }

    private static String maskEmail(String email) {
        int atIndex = email == null ? -1 : email.lastIndexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String link(String path, String token) {
        return link(webBaseUrl, path, token);
    }

    private static String link(URI baseUrl, String path, String token) {
        String base = baseUrl.toString().replaceFirst("/+$", "");
        return UriComponentsBuilder.fromUriString(base + path)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }
}
