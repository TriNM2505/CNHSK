package com.cnhsk.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class FlashcardReviewSecurityConfigIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    private long user;
    private long deck;
    private long card;

    @BeforeEach
    void caller() {
        user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử quyền ôn') RETURNING id", Long.class, UUID.randomUUID() + "@example.test");
        deck = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'FLASHCARD_DECK','Bộ ôn') RETURNING id", Long.class, user);
        card = jdbc.queryForObject("INSERT INTO learning.collection_items(collection_id,title,content) VALUES (?,'Thẻ ôn','Mặt sau') RETURNING id", Long.class, deck);
    }

    @Test
    void guestsAre401AndDoNotCount() throws Exception {
        mvc.perform(get("/api/flashcard-decks/{id}/due", deck)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/flashcards/{id}/review", card).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        assertNoCounter();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "CONTENT_EDITOR", "MODERATOR"})
    void nonLearnersAre403AndDoNotCount(String role) throws Exception {
        String bearer = "Bearer " + token(List.of(role));
        mvc.perform(get("/api/flashcard-decks/{id}/due", deck).header("Authorization", bearer)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mvc.perform(post("/api/flashcards/{id}/review", card).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        assertNoCounter();
    }

    @Test
    void cookieReviewRequiresCsrfBeforeCountingAndBearerDoesNot() throws Exception {
        Cookie access = new Cookie("access_token", token(List.of("LEARNER")));
        mvc.perform(get("/api/flashcard-decks/{id}/due", deck).cookie(access)).andExpect(status().isOk());
        mvc.perform(post("/api/flashcards/{id}/review", card).cookie(access).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post("/api/flashcards/{id}/review", card).cookie(access, new Cookie("XSRF-TOKEN", "review-token"))
                .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isForbidden());
        assertNoCounter();
        mvc.perform(post("/api/flashcards/{id}/review", card).cookie(access, new Cookie("XSRF-TOKEN", "review-token"))
                .header("X-XSRF-TOKEN", "review-token").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/flashcards/{id}/review", card).header("Authorization", "Bearer " + token(List.of("LEARNER", "ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"HARD\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT review_count FROM learning.user_progress WHERE user_id=? AND target_type='FLASHCARD' AND target_id=?", Integer.class, user, card)).isEqualTo(2);
    }

    private String token(List<String> roles) { return jwt.issueAccessToken(user, roles, false, UUID.randomUUID()); }
    private void assertNoCounter() { assertThat(redis.keys("library:flashcards:review:user:" + user + ":*")).isEmpty(); }
}
