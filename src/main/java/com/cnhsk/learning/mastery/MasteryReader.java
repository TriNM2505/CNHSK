package com.cnhsk.learning.mastery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Public batched mastery view used by other learning features. */
@Component
public class MasteryReader {

    private final MasteryQueryService queries;

    public MasteryReader(MasteryQueryService queries) {
        this.queries = queries;
    }

    public Map<String, MasteryView> valuesOf(long userId, Set<String> kids) {
        if (kids.isEmpty()) {
            return Map.of();
        }
        Map<String, MasteryView> result = new LinkedHashMap<>();
        queries.valuesOf(userId, kids).forEach((kid, value) ->
                result.put(kid, new MasteryView(value.value(), value.state())));
        return java.util.Collections.unmodifiableMap(result);
    }

    public record MasteryView(Integer value, String state) { }
}
