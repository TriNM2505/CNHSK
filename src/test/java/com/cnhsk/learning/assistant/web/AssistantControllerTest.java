package com.cnhsk.learning.assistant.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.learning.assistant.AssistantService;
import com.cnhsk.learning.assistant.AssistantService.AskResult;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssistantControllerTest {
    private AssistantService assistant;
    private AuthApi auth;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        assistant = mock(AssistantService.class);
        auth = mock(AuthApi.class);
        when(auth.currentUser()).thenReturn(new CurrentUser(51L, Set.of(RoleCode.LEARNER), true));
        mvc = MockMvcBuilders.standaloneSetup(new AssistantController(assistant, auth, new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void FR002_rejectsMalformedJsonAndWrongBodyShapeWith422() throws Exception {
        mvc.perform(post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        verifyNoInteractions(assistant);
    }

    @Test
    void FR002_countsCodePointsAfterTrimmingAndPassesValidatedCommand() throws Exception {
        when(assistant.ask(eq(51L), any())).thenReturn(new AskResult("ok", false));
        String question = "😀".repeat(2000);
        String history = "🙂".repeat(4000);
        String body = "{\"question\":\"  " + question + "  \",\"history\":[{\"role\":\"user\",\"text\":\""
                + history + "\"}],\"pageContext\":\"SCR-030\"}";
        mvc.perform(post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        ArgumentCaptor<AssistantService.AskCommand> captor =
                ArgumentCaptor.forClass(AssistantService.AskCommand.class);
        verify(assistant).ask(eq(51L), captor.capture());
        assertThat(captor.getValue().question()).isEqualTo(question);
        assertThat(captor.getValue().history()).singleElement().satisfies(turn -> assertThat(turn.text()).isEqualTo(history));
        assertThat(captor.getValue().pageContext()).isEqualTo("SCR-030");
    }

    @Test
    void FR002_rejectsNonStringFieldsBeforeCallingService() throws Exception {
        mvc.perform(post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        verifyNoInteractions(assistant);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"question\":\"  \"}", "{\"question\":\"ok\",\"history\":null}",
            "{\"question\":\"ok\",\"history\":[{\"role\":\"system\",\"text\":\"x\"}]}",
            "{\"question\":\"ok\",\"pageContext\":\"abc\"}", "{\"question\":\"ok\",\"pageContext\":\"\"}",
            "{\"question\":\"ok\",\"pageContext\":\" SCR-030 \"}", "{\"question\":\"ok\"} {}"})
    void FR002_AT02_invalidValuesAndTrailingJsonDoNotReachService(String body) throws Exception {
        mvc.perform(post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"));
        verifyNoInteractions(assistant);
    }

    @Test
    void FR002_AT02_rejectsOverflowingUnicodeQuestionOrHistory() throws Exception {
        String turn = "{\"role\":\"user\",\"text\":\"x\"}";
        for (String body : List.of(
                "{\"question\":\"" + "😀".repeat(2001) + "\"}",
                "{\"question\":\"x\",\"history\":[{\"role\":\"user\",\"text\":\"" + "🙂".repeat(4001) + "\"}]}",
                "{\"question\":\"x\",\"history\":[" + String.join(",", java.util.Collections.nCopies(11, turn)) + "]}")) {
            mvc.perform(post("/api/assistant/ask").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity());
        }
        verifyNoInteractions(assistant);
    }
}
