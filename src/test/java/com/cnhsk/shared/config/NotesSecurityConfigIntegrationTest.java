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

@AutoConfigureMockMvc
class NotesSecurityConfigIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    private long user;

    @BeforeEach
    void caller() {
        user = jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,'fixture-hash-unused','Người thử quyền') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    void FR001_guestsReceive401(String method) throws Exception {
        mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/notes/123"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "CONTENT_EDITOR", "MODERATOR"})
    void FR001_nonLearnersReceive403ForRootAndNestedRoutes(String role) throws Exception {
        String bearer = "Bearer " + token(role);
        for (var req : List.of(get("/api/notes"), get("/api/notes/123"), post("/api/notes"), put("/api/notes/123"))) {
            mvc.perform(req.header("Authorization", bearer)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test
    void FR001_cookieWritesNeedMatchingCsrfWhileBearerOnlyDoesNot() throws Exception {
        Cookie access = new Cookie("access_token", token("LEARNER"));
        for (var req : List.of(post("/api/notes"), put("/api/notes/123"))) {
            mvc.perform(req.cookie(access).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Thử CSRF\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        }
        mvc.perform(post("/api/notes").cookie(access, new Cookie("XSRF-TOKEN", "notes-token"))
                .header("X-XSRF-TOKEN", "bad").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Thử\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/notes").cookie(access, new Cookie("XSRF-TOKEN", "notes-token"))
                .header("X-XSRF-TOKEN", "notes-token").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Thử cookie\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/notes").header("Authorization", "Bearer " + token("LEARNER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Thử bearer\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/notes").cookie(access)).andExpect(status().isOk());
    }

    @Test
    void FR012_guestsCannotReadTrashOrRestore() throws Exception {
        for (var request : List.of(get("/api/notes/trash"), post("/api/notes/123/restore"))) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "TEACHER", "CONTENT_EDITOR", "MODERATOR"})
    void FR012_lifecycleRoutesRequireLearnerRole(String role) throws Exception {
        for (var request : List.of(get("/api/notes/trash"), delete("/api/notes/123"), post("/api/notes/123/restore"))) {
            mvc.perform(request.header("Authorization", "Bearer " + token(role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        }
    }

    @Test
    void FR012_deleteAndRestoreRequireMatchingCookieCsrfAndAllowBearerOnly() throws Exception {
        long book = jdbc.queryForObject("INSERT INTO learning.collections(user_id,kind,name) VALUES (?,'NOTEBOOK','Sổ thử CSRF') RETURNING id", Long.class, user);
        long id = jdbc.queryForObject("INSERT INTO learning.collection_items(collection_id,content) VALUES (?,'Ghi chú tự soạn CSRF') RETURNING id", Long.class, book);
        Cookie access = new Cookie("access_token", token("LEARNER"));
        for (var request : List.of(delete("/api/notes/{id}", id), post("/api/notes/{id}/restore", id))) {
            mvc.perform(request.cookie(access)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        }
        for (var request : List.of(delete("/api/notes/{id}", id), post("/api/notes/{id}/restore", id))) {
            mvc.perform(request.cookie(access, new Cookie("XSRF-TOKEN", "notes-token")).header("X-XSRF-TOKEN", "wrong"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        }
        mvc.perform(delete("/api/notes/{id}", id).cookie(access, new Cookie("XSRF-TOKEN", "notes-token"))
                .header("X-XSRF-TOKEN", "notes-token")).andExpect(status().isNoContent());
        mvc.perform(get("/api/notes/trash").cookie(access)).andExpect(status().isOk());
        mvc.perform(post("/api/notes/{id}/restore", id).cookie(access, new Cookie("XSRF-TOKEN", "notes-token"))
                .header("X-XSRF-TOKEN", "notes-token")).andExpect(status().isOk());
        String bearer = "Bearer " + token("LEARNER");
        mvc.perform(delete("/api/notes/{id}", id).header("Authorization", bearer)).andExpect(status().isNoContent());
        mvc.perform(post("/api/notes/{id}/restore", id).header("Authorization", bearer)).andExpect(status().isOk());
    }

    private String token(String role) { return jwt.issueAccessToken(user, List.of(role), false, UUID.randomUUID()); }
}
