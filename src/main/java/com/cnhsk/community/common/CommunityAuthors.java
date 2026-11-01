package com.cnhsk.community.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import org.springframework.stereotype.Component;

@Component
public class CommunityAuthors {

    private final AuthApi authApi;

    public CommunityAuthors(AuthApi authApi) {
        this.authApi = authApi;
    }

    public Map<Long, UserRef> resolve(Set<Long> userIds) {
        Set<Long> requestedIds = Set.copyOf(userIds);
        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> displayNames = authApi.displayNames(requestedIds);
        Map<Long, UserRef> authors = new HashMap<>();
        for (Long id : requestedIds) {
            authors.put(id, new UserRef(id, displayNames.get(id)));
        }
        return Map.copyOf(authors);
    }
}
