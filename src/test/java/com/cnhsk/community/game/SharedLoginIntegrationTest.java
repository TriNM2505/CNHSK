package com.cnhsk.community.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cnhsk.learning.api.GameCode;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class SharedLoginIntegrationTest extends AbstractIntegrationTest {
    private static final String WEB = "http://localhost:5173";
    private static final String GAME = "http://localhost:5174";
    private static final String OTHER_ORIGIN = "https://unapproved.example.test";
    private static final String GAMES = "/api/community/games";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void AT18_reusesWebLoginCookieAcrossGameOriginAndRequiresXsrfForRoundMutations() throws Exception {
        String id = UUID.randomUUID().toString();
        String email = "shared-login-" + id + "@example.test";
        String password = "Fixture-" + id + "!";
        long userId = jdbc.queryForObject(
                "INSERT INTO auth.users(email,password_hash,display_name,email_verified_at) "
                        + "VALUES (?,?,'Shared login fixture',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, email, new BCryptPasswordEncoder(12).encode(password));
        jdbc.update("INSERT INTO auth.user_roles(user_id,role_code) VALUES (?,'LEARNER')", userId);
        List<Long> lexemes = new ArrayList<>();
        try {
            var radicals = List.of("氵", "冫", "木", "日");
            for (int index = 0; index < 16; index++) {
                lexemes.add(jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi,hsk_level,"
                                + "stroke_count,radical) VALUES ('CHAR',?,'zì','chữ mẫu',1,4,?) RETURNING id",
                        Long.class, "字" + id + index, radicals.get(index % radicals.size())));
            }
            var pronunciations = List.of("nǐ hǎo", "xué xí", "zǎo ān", "hē shuǐ", "kàn shū", "chī fàn");
            for (String pinyin : pronunciations) {
                lexemes.add(jdbc.queryForObject("INSERT INTO learning.lexemes(kind,text,pinyin,meaning_vi,hsk_level) "
                                + "VALUES ('WORD',?,?,'từ mẫu',1) RETURNING id",
                        Long.class, "詞組" + UUID.randomUUID(), pinyin));
            }
            MvcResult login = mvc.perform(post("/api/auth/login")
                            .header("Origin", WEB)
                            .header("X-Client-Type", "WEB")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie access = requiredCookie(login, "access_token");
            Cookie xsrf = requiredCookie(login, "XSRF-TOKEN");
            assertThat(access.isHttpOnly()).isTrue();
            assertThat(access.getPath()).isEqualTo("/");
            assertThat(access.getDomain()).isEqualTo(xsrf.getDomain()).isNotBlank();

            var guest = mvc.perform(get(GAMES).header("Origin", GAME))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", GAME))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                    .andReturn();
            var catalog = mapper.readTree(guest.getResponse().getContentAsByteArray());
            assertThat(catalog.size()).isEqualTo(GameCode.values().length);
            catalog.forEach(game -> assertThat(game.path("myStats").isNull()).isTrue());
            mvc.perform(get(GAMES).header("Origin", GAME).cookie(access))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", GAME))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                    .andExpect(jsonPath("$[0].myStats.roundsPlayed").value(0));

            mvc.perform(options(GAMES).header("Origin", GAME)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", GAME))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
            mvc.perform(options(GAMES).header("Origin", OTHER_ORIGIN)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            mvc.perform(get(GAMES).header("Origin", OTHER_ORIGIN).cookie(access))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

            long answerRound = startRound(GameCode.CHAR_RAIN, access, xsrf);
            long finishRound = startRound(GameCode.PINYIN_MATCH, access, xsrf);
            long abandonRound = startRound(GameCode.RADICAL_MATCH, access, xsrf);
            String answer = mapper.writeValueAsString(Map.of("no", 1, "answer", "PASS", "durationMs", 1500));
            assertMutation(answerRound, "answers", answer, 200, "IN_PROGRESS", 1, access, xsrf);
            assertMutation(finishRound, "finish", "{}", 200, "SUBMITTED", 0, access, xsrf);
            assertMutation(abandonRound, "abandon", "{}", 204, "ABANDONED", 0, access, xsrf);
        } finally {
            jdbc.update("DELETE FROM learning.study_events WHERE user_id=?", userId);
            jdbc.update("DELETE FROM learning.user_progress WHERE user_id=?", userId);
            jdbc.update("DELETE FROM learning.attempt_items WHERE attempt_id IN "
                    + "(SELECT id FROM learning.attempts WHERE user_id=?)", userId);
            jdbc.update("DELETE FROM learning.attempts WHERE user_id=?", userId);
            for (long lexeme : lexemes) {
                jdbc.update("DELETE FROM learning.lexemes WHERE id=?", lexeme);
            }
            jdbc.update("DELETE FROM shared.audit_logs WHERE actor_id=?", userId);
            jdbc.update("DELETE FROM auth.auth_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM auth.user_roles WHERE user_id=?", userId);
            jdbc.update("DELETE FROM auth.users WHERE id=?", userId);
        }
    }

    private long startRound(GameCode code, Cookie access, Cookie xsrf) throws Exception {
        MvcResult result = mvc.perform(gamePost(GAMES + "/" + code.name() + "/rounds", "{\"hskLevel\":1}", access, xsrf)
                        .header(XSRF_HEADER, xsrf.getValue()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundId").isNumber())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray()).path("roundId").asLong();
    }

    private void assertMutation(long id, String operation, String body, int expectedStatus,
            String expectedState, int expectedItems, Cookie access, Cookie xsrf) throws Exception {
        String path = GAMES + "/rounds/" + id + "/" + operation;
        mvc.perform(gamePost(path, body, access, xsrf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        assertRound(id, "IN_PROGRESS", 0);
        mvc.perform(gamePost(path, body, access, xsrf).header(XSRF_HEADER, "wrong"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        assertRound(id, "IN_PROGRESS", 0);
        mvc.perform(gamePost(path, body, access, xsrf).header(XSRF_HEADER, xsrf.getValue()))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string("Access-Control-Allow-Origin", GAME))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        assertRound(id, expectedState, expectedItems);
    }

    private void assertRound(long id, String expectedState, int expectedItems) {
        assertThat(jdbc.queryForObject("SELECT status FROM learning.attempts WHERE id=?", String.class, id))
                .isEqualTo(expectedState);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM learning.attempt_items WHERE attempt_id=?", Long.class, id))
                .isEqualTo(expectedItems);
    }

    private static MockHttpServletRequestBuilder gamePost(String path, String body, Cookie access, Cookie xsrf) {
        return post(path).header("Origin", GAME).cookie(access, xsrf)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static Cookie requiredCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("web login sets %s", name).isNotNull();
        return cookie;
    }
}
