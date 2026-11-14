package com.cnhsk.auth.service;

import java.util.Map;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.shared.api.ActorNameLookup;
import org.springframework.stereotype.Component;

@Component
public class SettingsActorNameLookup implements ActorNameLookup {
    private final AuthApi auth;

    public SettingsActorNameLookup(AuthApi auth) { this.auth = auth; }

    @Override
    public Map<Long, String> displayNames(Set<Long> actorIds) {
        return actorIds.isEmpty() ? Map.of() : auth.displayNames(actorIds);
    }
}
