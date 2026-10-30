package com.cnhsk.learning.practice.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.Skill;

/** Published synchronously in the transaction that records the item, including RECORD evidence. */
public record ItemGradedEvent(long userId, long sessionId, PracticeSource source, Long refId,
        int no, Long lexemeId, Skill skill, ItemType itemType, Boolean correct, Map<String, Object> meta) {

    public ItemGradedEvent {
        meta = Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }
}
