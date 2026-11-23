package com.cnhsk.community.follow.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Public community profile; never carries email, roles or ban reasons. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "CommunityProfile", requiredProperties = {"id", "displayName", "avatarUrl", "followerCount",
        "followingCount", "approvedPostCount", "followedByMe", "self"})
public record CommunityProfile(long id, @Schema(types = {"string", "null"}) String displayName,
        @Schema(types = {"string", "null"}) String avatarUrl, long followerCount, long followingCount,
        long approvedPostCount, boolean followedByMe, boolean self) {
}
