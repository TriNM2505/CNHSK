package com.cnhsk.learning.vocab.web;

import static com.cnhsk.learning.vocab.TopicPrerequisiteModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.cnhsk.learning.vocab.TopicPrerequisiteService;
import com.cnhsk.shared.config.RequestIdFilter;
import com.cnhsk.shared.exception.GlobalExceptionHandler;
import com.cnhsk.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TopicAdminControllerTest {
    private final TopicPrerequisiteService service = mock(TopicPrerequisiteService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new TopicAdminController(service, new ObjectMapper()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RequestIdFilter()).build();
        var principal = new AuthenticatedUser(42, Set.of("CONTENT_EDITOR"), true, "fixture", null, Instant.EPOCH);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        when(service.setPrerequisites(anyLong(), anyLong(), any())).thenReturn(new AdminTopic(7, "Chủ đề", 1, List.of(), true, List.of()));
        when(service.list(any(), any(), anyInt(), anyInt())).thenReturn(new TopicPage(List.of(), 0, 20, 0));
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void identityComesFromPrincipalAndInputOrderIsPreserved() throws Exception {
        mvc.perform(put("/api/admin/topics/7/prerequisites").contentType(MediaType.APPLICATION_JSON)
                .content("{\"prereqIds\":[9,8],\"actorId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isRoot").value(true)).andExpect(jsonPath("$.warnings").isArray());
        verify(service).setPrerequisites(7, 42, List.of(9L, 8L));
    }

    @Test void emptyListAndTenDistinctInt64IdsAreAcceptedWithoutNumericCoercion() throws Exception {
        mvc.perform(put("/api/admin/topics/7/prerequisites").contentType(MediaType.APPLICATION_JSON).content("{\"prereqIds\":[]}"))
                .andExpect(status().isOk());
        verify(service).setPrerequisites(7, 42, List.of());
        mvc.perform(put("/api/admin/topics/7/prerequisites").contentType(MediaType.APPLICATION_JSON)
                .content("{\"prereqIds\":[1,2,3,4,5,6,7,8,9,9223372036854775807]}"))
                .andExpect(status().isOk());
        verify(service).setPrerequisites(7, 42, List.of(1L,2L,3L,4L,5L,6L,7L,8L,9L,Long.MAX_VALUE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{", "{} {}", "{}", "{\"prereqIds\":null}", "{\"prereqIds\":1}",
            "{\"prereqIds\":[null]}", "{\"prereqIds\":[true]}", "{\"prereqIds\":[\"1\"]}", "{\"prereqIds\":[1.0]}",
            "{\"prereqIds\":[9223372036854775808]}", "{\"prereqIds\":[1,1]}", "{\"prereqIds\":[1,2,3,4,5,6,7,8,9,10,11]}"})
    void invalidBodiesReturnValidationDetailsAndNeverCallTheService(String body) throws Exception {
        mvc.perform(put("/api/admin/topics/7/prerequisites").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error_code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").isString()).andExpect(jsonPath("$.request_id").isNotEmpty());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "bad", "9223372036854775808"})
    void malformedTargetIdsAreRejected(String id) throws Exception {
        mvc.perform(put("/api/admin/topics/{id}/prerequisites", id).contentType(MediaType.APPLICATION_JSON).content("{\"prereqIds\":[]}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"hsk", "page", "size"})
    void queryNumbersAreParsedStrictly(String parameter) throws Exception {
        mvc.perform(get("/api/admin/topics").param(parameter, "invalid")).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test void filtersAndDefaultsArePassedToTheService() throws Exception {
        mvc.perform(get("/api/admin/topics")).andExpect(status().isOk());
        verify(service).list(null, null, 0, 20);
        mvc.perform(get("/api/admin/topics").param("hsk", "9").param("q", "%_!").param("page", "2").param("size", "50"))
                .andExpect(status().isOk());
        verify(service).list(9, "%_!", 2, 50);
    }
}
