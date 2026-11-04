package com.cnhsk.learning.library.web;

import com.cnhsk.learning.library.DictionaryService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.swagger.v3.oas.annotations.media.Schema;

/** Retains field presence; values are checked after the service has verified ownership. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "CardRequest")
public class CardWrite {
    @Schema(type = "integer", format = "int64", nullable = true)
    private JsonNode lexemeId;
    @Schema(type = "string", nullable = true, minLength = 1, maxLength = 200)
    private JsonNode front;
    @Schema(type = "string", nullable = true, minLength = 1, maxLength = 2000)
    private JsonNode back;

    @JsonSetter("lexemeId")
    public void setLexemeId(JsonNode value) { lexemeId = present(value); }
    @JsonSetter("front")
    public void setFront(JsonNode value) { front = present(value); }
    @JsonSetter("back")
    public void setBack(JsonNode value) { back = present(value); }

    public boolean hasLexemeId() { return lexemeId != null; }
    public boolean hasFront() { return front != null; }
    public boolean hasBack() { return back != null; }

    public long lexemeId() {
        if (lexemeId == null || !lexemeId.isIntegralNumber() || !lexemeId.canConvertToLong()) {
            throw DictionaryService.invalid("lexemeId", "Mã mục từ phải là số nguyên hợp lệ.");
        }
        return lexemeId.longValue();
    }

    public String front() { return text(front, "front"); }
    public String back() { return text(back, "back"); }

    private static JsonNode present(JsonNode value) { return value == null ? NullNode.getInstance() : value; }

    private static String text(JsonNode value, String field) {
        if (value == null || !value.isTextual()) {
            throw DictionaryService.invalid(field, "Mặt thẻ phải là chuỗi, không được null.");
        }
        return value.textValue();
    }
}
