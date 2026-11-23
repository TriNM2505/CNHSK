package com.cnhsk.community.post.web;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Pin a post until the given moment, or unpin it with {@code null}. */
@Schema(name = "PinWrite")
public record PinWrite(@Schema(types = {"string", "null"}, format = "date-time") Instant pinnedUntil) {
}
