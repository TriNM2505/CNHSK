package com.cnhsk.learning.practice;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Public item fields only; never serialize ServedItem as a response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServedItemView(
        int no,
        ItemType type,
        Skill skill,
        Long lexemeId,
        ServedItem.Prompt prompt,
        List<ServedItem.Option> options,
        ServedItem.Rules rules) {

    public ServedItemView {
        options = options == null ? null : List.copyOf(options);
    }

    public static ServedItemView from(ServedItem item) {
        return new ServedItemView(item.no(), item.type(), item.skill(), item.lexemeId(),
                item.prompt(), item.options(), item.rules());
    }
}
