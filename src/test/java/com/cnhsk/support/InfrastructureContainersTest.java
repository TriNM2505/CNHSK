package com.cnhsk.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InfrastructureContainersTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${test.mailpit.base-url}")
    private String mailpitBaseUrl;

    @Test
    void connectsToPostgresRedisAndMailpit() throws Exception {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        assertThat(sessionFactory.getJdbcServices().getDialect()).isInstanceOf(PostgreSQLDialect.class);

        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);

        String redisReply = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
        assertThat(redisReply).isEqualTo("PONG");

        String subject = "T-02 integration check " + UUID.randomUUID();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@cnhsk.test");
        message.setTo("integration@cnhsk.test");
        message.setSubject(subject);
        message.setText("Mailpit SMTP connectivity check.");
        mailSender.send(message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailpitBaseUrl + "/api/v1/messages"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(subject);
    }
}
