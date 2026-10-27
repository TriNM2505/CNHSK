package com.cnhsk.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.cnhsk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class SessionInvalidationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SessionInvalidationService service;
    @Autowired private StringRedisTemplate redisTemplate;

    @Test
    void markerMergeNeverMovesInvalidBeforeBackwardsAndPreservesBanReasonForStaleWrite() {
        long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        long newer = 1_900_000_000L;
        try {
            service.markBanned(userId, newer, "spam");
            service.markRevoked(userId, newer - 1);

            SessionInvalidationMarker marker = service.findMarker(userId).orElseThrow();
            assertThat(marker).isEqualTo(SessionInvalidationMarker.banned(newer, "spam"));
            assertThat(redisTemplate.getExpire("auth:invalid-before:" + userId)).isPositive();
        } finally {
            redisTemplate.delete("auth:invalid-before:" + userId);
        }
    }

    @Test
    void newerRevocationAdvancesTimestampButRetainsCurrentBanPriority() {
        long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        try {
            service.markBanned(userId, 1_900_000_010L, "abuse");
            service.markRevoked(userId, 1_900_000_011L);

            assertThat(service.findMarker(userId))
                    .contains(SessionInvalidationMarker.banned(1_900_000_011L, "abuse"));
        } finally {
            redisTemplate.delete("auth:invalid-before:" + userId);
        }
    }

    @Test
    void unbanAtomicallyChangesBanToRevokedWithoutReducingCutoffOrExtendingTtl() {
        long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        try {
            long cutoff = System.currentTimeMillis() + 5_000;
            service.markBanned(userId, cutoff, "policy violation");
            Long ttlBefore = redisTemplate.getExpire("auth:invalid-before:" + userId, TimeUnit.MILLISECONDS);

            service.markUnbanned(userId);

            assertThat(service.findMarker(userId)).contains(SessionInvalidationMarker.revoked(cutoff));
            Long ttlAfter = redisTemplate.getExpire("auth:invalid-before:" + userId, TimeUnit.MILLISECONDS);
            assertThat(ttlAfter).isPositive().isLessThanOrEqualTo(ttlBefore);
        } finally {
            redisTemplate.delete("auth:invalid-before:" + userId);
        }
    }

    @Test
    void unbanDoesNotRewriteMissingOrAlreadyRevokedMarkers() {
        long missingUserId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        long revokedUserId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        try {
            service.markUnbanned(missingUserId);
            assertThat(service.findMarker(missingUserId)).isEmpty();

            long cutoff = System.currentTimeMillis() + 10_000;
            service.markRevoked(revokedUserId, cutoff);
            Long ttlBefore = redisTemplate.getExpire("auth:invalid-before:" + revokedUserId, TimeUnit.MILLISECONDS);
            service.markUnbanned(revokedUserId);

            assertThat(service.findMarker(revokedUserId))
                    .contains(SessionInvalidationMarker.revoked(cutoff));
            Long ttlAfter = redisTemplate.getExpire("auth:invalid-before:" + revokedUserId, TimeUnit.MILLISECONDS);
            assertThat(ttlAfter).isPositive().isLessThanOrEqualTo(ttlBefore);
        } finally {
            redisTemplate.delete("auth:invalid-before:" + missingUserId);
            redisTemplate.delete("auth:invalid-before:" + revokedUserId);
        }
    }
}
