package com.cnhsk.shared.api;

import java.util.Map;
import java.util.Set;

/** Resolves public display names without a dependency from shared to an identity module. */
public interface ActorNameLookup {
    Map<Long, String> displayNames(Set<Long> actorIds);
}
