package com.cnhsk.shared.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.cnhsk.learning.credit.RedeemService;
import com.cnhsk.learning.credit.web.*;
import com.cnhsk.learning.credit.web.CreateBatchRequest.CardType;
import com.cnhsk.shared.security.JwtService;
import com.cnhsk.shared.security.SessionInvalidationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@WebMvcTest(controllers=WalletController.class)
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SharedConfiguration.class, JwtService.class})
class CreditRedeemSecurityConfigIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;
    @MockitoBean private RedeemService service;
    @MockitoBean private SessionInvalidationService invalidation;
    @MockitoBean private Clock clock;
    private static final String PATH="/api/credits/redeem";

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-08T00:00:00Z"));
        when(invalidation.findMarker(anyLong())).thenReturn(Optional.empty());
        when(service.redeem(any(), anyString())).thenReturn(new RedeemResult(CardType.CREDIT,1,1,
                new PlanStatus(PlanStatus.PlanCode.FREE,"Free",null,true)));
    }

    @Test void unauthenticatedMissingCsrfAndMalformedTokenReturn401() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code").value("UNAUTHENTICATED"));
        mvc.perform(post(PATH).cookie(new Cookie("access_token","invalid-token")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings={"ADMIN","ACCOUNTANT","TEACHER","MODERATOR","CONTENT_EDITOR"})
    void everyNonLearnerIsForbiddenBeforeCsrfAndAllThreeMatcherPatternsRejectRole(String role) throws Exception {
        String token=token(List.of(role));
        mvc.perform(post(PATH).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"fixture\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        mvc.perform(post(PATH).cookie(new Cookie("access_token",token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
        for(String path:List.of("/api/me/credits","/api/me/credit-transactions","/api/credits/other"))
            mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void learnerBearerNeedsNoCsrfOrVerifiedEmailAndUsesRemoteAddress() throws Exception {
        mvc.perform(post(PATH).header("Authorization","Bearer "+token(List.of("LEARNER","ADMIN")))
                .header("X-Forwarded-For","198.51.100.1")
                .with(request->{request.setRemoteAddr("192.0.2.44");return request;})
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"fixture\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.plan.expiresAt").value(org.hamcrest.Matchers.nullValue()));
        verify(service).redeem(new RedeemRequest("fixture"),"192.0.2.44");
    }

    @Test void cookieRequiresMatchingCsrfBeforeRedeeming() throws Exception {
        Cookie access=new Cookie("access_token",token(List.of("LEARNER")));
        mvc.perform(post(PATH).cookie(access).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post(PATH).cookie(access,new Cookie("XSRF-TOKEN","synthetic-csrf"))
                .header("X-XSRF-TOKEN","wrong").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mvc.perform(post(PATH).cookie(access,new Cookie("XSRF-TOKEN","synthetic-csrf"))
                .header("X-XSRF-TOKEN","synthetic-csrf").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"fixture\"}"))
                .andExpect(status().isOk());
        verify(service).redeem(eq(new RedeemRequest("fixture")),anyString());
    }

    @Test void noWalletOrHistoryGetHandlersHaveBeenAdded() {
        handlers.getHandlerMethods().forEach((mapping,handler)-> {
            if(mapping.getPatternValues().stream().anyMatch(path->List.of("/api/me/credits","/api/me/credit-transactions").contains(path)))
                assertThat(mapping.getMethodsCondition().getMethods()).doesNotContain(org.springframework.web.bind.annotation.RequestMethod.GET);
        });
    }

    private String token(List<String> roles) { return jwt.issueAccessToken(42,roles,false,UUID.randomUUID()); }
}

