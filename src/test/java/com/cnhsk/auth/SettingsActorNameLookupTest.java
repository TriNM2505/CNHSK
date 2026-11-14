package com.cnhsk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.service.SettingsActorNameLookup;
import org.junit.jupiter.api.Test;

class SettingsActorNameLookupTest {
    @Test
    void delegatesOneBatchAndReturnsOnlyPublicNames() {
        AuthApi auth = mock(AuthApi.class);
        when(auth.displayNames(Set.of(1L, 2L))).thenReturn(Map.of(1L, "Quản trị một", 2L, "Quản trị hai"));
        var lookup = new SettingsActorNameLookup(auth);
        assertThat(lookup.displayNames(Set.of(1L, 2L))).containsExactlyInAnyOrderEntriesOf(Map.of(1L, "Quản trị một", 2L, "Quản trị hai"));
        verify(auth).displayNames(Set.of(1L, 2L));
        verifyNoMoreInteractions(auth);
    }

    @Test
    void emptyBatchDoesNotCallAuth() {
        AuthApi auth = mock(AuthApi.class);
        assertThat(new SettingsActorNameLookup(auth).displayNames(Set.of())).isEmpty();
        verifyNoInteractions(auth);
    }
}
