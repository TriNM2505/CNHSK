package com.cnhsk.auth.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.cnhsk.auth.event.EmailVerificationRequestedEvent;
import com.cnhsk.auth.event.PasswordResetRequestedEvent;
import com.cnhsk.shared.config.CnhskProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/** Sends authentication emails only after the transaction that requested them commits. */
@Service
public class MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);
    private static final String VERIFY_PATH = "/verify-email";
    private static final String RESET_PATH = "/reset-password";

    private final JavaMailSender mailSender;
    private final URI webBaseUrl;
    private final String fromAddress;

    public MailService(JavaMailSender mailSender, CnhskProperties properties,
            @Value("${MAIL_FROM:noreply@cnhsk.com}") String fromAddress) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null.");
        this.webBaseUrl = Objects.requireNonNull(properties, "properties must not be null.")
                .getApp().getWebBaseUrl();
        if (webBaseUrl == null || !webBaseUrl.isAbsolute()) {
            throw new IllegalArgumentException("APP_WEB_BASE_URL must be an absolute URL.");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException("MAIL_FROM must not be blank.");
        }
        this.fromAddress = fromAddress;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(EmailVerificationRequestedEvent event) {
        send(event.getRecipient(), "Xác thực email CNHSK",
                "Xin chào,\n\n"
                        + "Vui lòng xác thực địa chỉ email cho tài khoản CNHSK bằng liên kết sau:\n"
                        + link(VERIFY_PATH, event.getToken()) + "\n\n"
                        + "Liên kết có hiệu lực trong 24 giờ. Nếu bạn không tạo tài khoản, vui lòng bỏ qua email này.\n\n"
                        + "CNHSK");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendPasswordResetEmail(PasswordResetRequestedEvent event) {
        send(event.getRecipient(), "Đặt lại mật khẩu CNHSK",
                "Xin chào,\n\n"
                        + "Chúng tôi nhận được yêu cầu đặt lại mật khẩu tài khoản CNHSK. Mở liên kết sau để tiếp tục:\n"
                        + link(RESET_PATH, event.getToken()) + "\n\n"
                        + "Liên kết có hiệu lực trong 1 giờ. Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.\n\n"
                        + "CNHSK");
    }

    private void send(String recipient, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
        } catch (MessagingException exception) {
            LOGGER.warn("Could not prepare authentication email; mail error type={}",
                    exception.getClass().getSimpleName());
        } catch (MailException exception) {
            // Do not log the exception itself: provider messages can contain recipient or message details.
            LOGGER.warn("Could not send authentication email; mail error type={}",
                    exception.getClass().getSimpleName());
        }
    }

    private String link(String path, String token) {
        String base = webBaseUrl.toString().replaceFirst("/+$", "");
        return UriComponentsBuilder.fromUriString(base + path)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }
}
