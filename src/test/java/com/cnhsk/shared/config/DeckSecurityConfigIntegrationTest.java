package com.cnhsk.shared.config;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class DeckSecurityConfigIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    private long user;
    private long deck;

    @BeforeEach
    void caller() {
        user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử quyền bộ thẻ') RETURNING id", Long.class, UUID.randomUUID() + "@example.test");
        deck = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'FLASHCARD_DECK','Bộ thử quyền') RETURNING id", Long.class, user);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PATCH", "DELETE"})
    void FR001_guestsReceive401OnRootAndNestedRoutes(String method) throws Exception {
        for (String path : List.of("/api/flashcard-decks", "/api/flashcard-decks/123")) {
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "CONTENT_EDITOR", "MODERATOR"})
    void FR001_nonLearnersReceive403ForEveryEndpoint(String role) throws Exception {
        String bearer = "Bearer " + token(List.of(role));
        for (var request : List.of(get("/api/flashcard-decks"), post("/api/flashcard-decks"),
                get("/api/flashcard-decks/{id}", deck), patch("/api/flashcard-decks/{id}", deck), delete("/api/flashcard-decks/{id}", deck))) {
            mvc.perform(request.header("Authorization", bearer)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test
    void FR001_cookieWritesRequireMatchingCsrfAndKeepOriginalDeckOnRejection() throws Exception {
        Cookie access = new Cookie("access_token", token(List.of("LEARNER")));
        for (String method : List.of("POST", "PATCH", "DELETE")) {
            String path = "/api/flashcard-decks" + (method.equals("POST") ? "" : "/" + deck);
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).cookie(access)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"CSRF\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).cookie(access, new Cookie("XSRF-TOKEN", "deck-token"))
                    .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"CSRF\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        }
        mvc.perform(get("/api/flashcard-decks/{id}", deck).cookie(access)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Bộ thử quyền"));
        mvc.perform(post("/api/flashcard-decks").cookie(access, new Cookie("XSRF-TOKEN", "deck-token"))
                .header("X-XSRF-TOKEN", "deck-token").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cookie hợp lệ\"}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/flashcard-decks/{id}", deck).cookie(access, new Cookie("XSRF-TOKEN", "deck-token"))
                .header("X-XSRF-TOKEN", "deck-token").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Đã sửa\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/flashcard-decks/{id}", deck).cookie(access, new Cookie("XSRF-TOKEN", "deck-token"))
                .header("X-XSRF-TOKEN", "deck-token")).andExpect(status().isNoContent());
    }

    @Test
    void FR001_learnerWithAdditionalRolesCanUseBearerWritesWithoutCsrf() throws Exception {
        String bearer = "Bearer " + token(List.of("LEARNER", "ADMIN"));
        mvc.perform(post("/api/flashcard-decks").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bearer hợp lệ\"}")).andExpect(status().isCreated());
        mvc.perform(patch("/api/flashcard-decks/{id}", deck).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Đã sửa\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/flashcard-decks/{id}", deck).header("Authorization", bearer)).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PATCH", "DELETE"})
    void FR015_FR016_cardGuestsReceive401(String method) throws Exception {
        String path = "/api/flashcard-decks/" + deck + "/cards" + (method.equals("POST") ? "" : "/123");
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "CONTENT_EDITOR", "MODERATOR"})
    void FR015_FR016_cardNonLearnersReceive403(String role) throws Exception {
        String bearer = "Bearer " + token(List.of(role));
        for (var request : List.of(post("/api/flashcard-decks/{id}/cards", deck),
                patch("/api/flashcard-decks/{id}/cards/123", deck), delete("/api/flashcard-decks/{id}/cards/123", deck))) {
            mvc.perform(request.header("Authorization", bearer)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test
    void FR015_FR016_cardCookieWritesRequireMatchingCsrf() throws Exception {
        long card = card();
        Cookie access = new Cookie("access_token", token(List.of("LEARNER")));
        for (String method : List.of("POST", "PATCH", "DELETE")) {
            String path = "/api/flashcard-decks/" + deck + "/cards" + (method.equals("POST") ? "" : "/" + card);
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).cookie(access)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"front\":\"CSRF\",\"back\":\"Mặt sau\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).cookie(access, new Cookie("XSRF-TOKEN", "card-token"))
                    .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content("{\"front\":\"CSRF\",\"back\":\"Mặt sau\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        }
        mvc.perform(get("/api/flashcard-decks/{id}", deck).cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].front").value("Thẻ thử quyền"));
        mvc.perform(post("/api/flashcard-decks/{id}/cards", deck).cookie(access, new Cookie("XSRF-TOKEN", "card-token"))
                .header("X-XSRF-TOKEN", "card-token").contentType(MediaType.APPLICATION_JSON).content("{\"front\":\"Cookie hợp lệ\",\"back\":\"Mặt sau\"}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/flashcard-decks/{id}/cards/{cardId}", deck, card).cookie(access, new Cookie("XSRF-TOKEN", "card-token"))
                .header("X-XSRF-TOKEN", "card-token").contentType(MediaType.APPLICATION_JSON).content("{\"back\":\"Mặt sau mới\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/flashcard-decks/{id}/cards/{cardId}", deck, card).cookie(access, new Cookie("XSRF-TOKEN", "card-token"))
                .header("X-XSRF-TOKEN", "card-token")).andExpect(status().isNoContent());
    }

    @Test
    void FR015_FR016_cardBearerWritesAllowLearnerWithOtherRolesWithoutCsrf() throws Exception {
        long card = card();
        String bearer = "Bearer " + token(List.of("LEARNER", "ADMIN"));
        mvc.perform(post("/api/flashcard-decks/{id}/cards", deck).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"front\":\"Bearer hợp lệ\",\"back\":\"Mặt sau\"}"))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/flashcard-decks/{id}/cards/{cardId}", deck, card).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"back\":\"Mặt sau mới\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/flashcard-decks/{id}/cards/{cardId}", deck, card).header("Authorization", bearer))
                .andExpect(status().isNoContent());
    }

    private long card() { return jdbc.queryForObject("INSERT INTO learning.collection_items(collection_id,title,content) VALUES (?,'Thẻ thử quyền','Mặt sau') RETURNING id", Long.class, deck); }
    private String token(List<String> roles) { return jwt.issueAccessToken(user, roles, false, UUID.randomUUID()); }
}
