package com.cnhsk.community.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CommunityAuthorsTest {

    private final AuthApi authApi = mock(AuthApi.class);
    private final CommunityAuthors authors = new CommunityAuthors(authApi);

    @Test
    void FR006_emptyIdsRequireNoAuthLookup() {
        assertThat(authors.resolve(Set.of())).isEmpty();
        verifyNoInteractions(authApi);
    }

    @Test
    void FR006_batchesDistinctIdsAndPreservesMissingAndNullNames() {
        Set<Long> ids = new HashSet<>(List.of(11L, 12L, 11L, 13L));
        Map<Long, String> names = new HashMap<>();
        names.put(11L, "Minh");
        names.put(12L, null);
        names.put(99L, "Ngoài danh sách");
        when(authApi.displayNames(ids)).thenReturn(names);

        Map<Long, UserRef> resolved = authors.resolve(ids);

        assertThat(resolved).containsOnly(
                Map.entry(11L, new UserRef(11L, "Minh")),
                Map.entry(12L, new UserRef(12L, null)),
                Map.entry(13L, new UserRef(13L, null)));
        verify(authApi).displayNames(Set.of(11L, 12L, 13L));
        verifyNoMoreInteractions(authApi);
    }

    @Test
    void FR006_resolvesFreshNamesOnEachCall() {
        Set<Long> ids = Set.of(11L);
        when(authApi.displayNames(ids)).thenReturn(Map.of(11L, "Minh"), Map.of(11L, "Tên mới"));

        assertThat(authors.resolve(ids)).containsEntry(11L, new UserRef(11L, "Minh"));
        assertThat(authors.resolve(ids)).containsEntry(11L, new UserRef(11L, "Tên mới"));

        verify(authApi, times(2)).displayNames(ids);
        verifyNoMoreInteractions(authApi);
    }

    @Test
    void FR006_jsonContainsOnlyPublicFieldsIncludingNullDisplayName() throws Exception {
        ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

        JsonNode missingName = mapper.readTree(mapper.writeValueAsString(new UserRef(11L, null)));
        JsonNode named = mapper.readTree(mapper.writeValueAsString(new UserRef(12L, "Minh")));

        assertThat(missingName.size()).isEqualTo(2);
        assertThat(missingName.has("id")).isTrue();
        assertThat(missingName.get("id").longValue()).isEqualTo(11L);
        assertThat(missingName.has("displayName")).isTrue();
        assertThat(missingName.get("displayName").isNull()).isTrue();
        assertThat(named.size()).isEqualTo(2);
        assertThat(named.get("id").longValue()).isEqualTo(12L);
        assertThat(named.get("displayName").textValue()).isEqualTo("Minh");
    }
}
