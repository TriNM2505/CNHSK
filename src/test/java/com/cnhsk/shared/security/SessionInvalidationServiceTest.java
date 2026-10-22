package com.cnhsk.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class SessionInvalidationServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mockValueOperations();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private SessionInvalidationService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(redisTemplate.execute(any(DefaultRedisScript.class), any(List.class), any(Object[].class)))
                .thenReturn(1L);
        service = new SessionInvalidationService(redisTemplate, objectMapper);
    }

    @Test
    void FR092_writesBannedMarkerUsingSharedJsonKeyAndTtl() {
        service.markBanned(31, 1_798_000_000_123L, "spam");

        verify(redisTemplate).execute(any(DefaultRedisScript.class), org.mockito.ArgumentMatchers.eq(
                        List.of("auth:invalid-before:31")),
                org.mockito.ArgumentMatchers.eq(
                        "{\"invalidBeforeMs\":1798000000123,\"reason\":\"BANNED\",\"banReason\":\"spam\"}"),
                org.mockito.ArgumentMatchers.eq("900"));
    }

    @Test
    void FR092_writesRevokedMarkerWithoutBanReason() {
        service.markRevoked(32, 1_798_000_001_234L);

        verify(redisTemplate).execute(any(DefaultRedisScript.class), org.mockito.ArgumentMatchers.eq(
                        List.of("auth:invalid-before:32")),
                org.mockito.ArgumentMatchers.eq("{\"invalidBeforeMs\":1798000001234,\"reason\":\"REVOKED\"}"),
                org.mockito.ArgumentMatchers.eq("900"));
    }

    @Test
    void FR092_readsBothMarkerReasonsFromOneRedisGet() throws Exception {
        when(values.get("auth:invalid-before:33")).thenReturn(
                "{\"invalidBeforeMs\":1798000002345,\"reason\":\"BANNED\",\"banReason\":\"abuse\"}");

        Optional<SessionInvalidationMarker> result = service.findMarker(33);

        assertEquals(Optional.of(SessionInvalidationMarker.banned(1_798_000_002_345L, "abuse")), result);
        verify(values).get("auth:invalid-before:33");
    }

    @Test
    void FR092_missingKeyReturnsEmpty() {
        when(values.get("auth:invalid-before:34")).thenReturn(null);

        assertTrue(service.findMarker(34).isEmpty());
    }

    @Test
    void FR092_malformedMarkerIsReportedToFilterAsInfrastructureFailure() {
        when(values.get("auth:invalid-before:35")).thenReturn("not-json");

        assertThrows(IllegalStateException.class, () -> service.findMarker(35));
    }

    @Test
    void rejectsInvalidUserIdsAndInvalidBannedMarkers() {
        assertThrows(IllegalArgumentException.class, () -> service.markRevoked(0, 1));
        assertThrows(IllegalArgumentException.class, () -> SessionInvalidationMarker.banned(1, " "));
    }

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> mockValueOperations() {
        return mock(ValueOperations.class);
    }
}
