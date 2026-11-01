package com.cnhsk.community.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserRef(long id, String displayName) {
}
