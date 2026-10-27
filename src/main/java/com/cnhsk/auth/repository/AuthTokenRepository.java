package com.cnhsk.auth.repository;

import com.cnhsk.auth.entity.AuthToken;
import com.cnhsk.auth.entity.TokenType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    @Query("select token.userId from AuthToken token where token.tokenHash = :tokenHash "
            + "and token.tokenType = :tokenType")
    Optional<Long> findUserIdByTokenHashAndTokenType(
            @Param("tokenHash") String tokenHash,
            @Param("tokenType") TokenType tokenType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AuthToken token where token.tokenHash = :tokenHash "
            + "and token.tokenType = :tokenType")
    Optional<AuthToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash,
            @Param("tokenType") TokenType tokenType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AuthToken token where token.userId = :userId "
            + "and token.tokenType = :tokenType and token.revokedAt is null order by token.id asc")
    List<AuthToken> findActiveByUserIdAndTokenTypeForUpdate(
            @Param("userId") Long userId,
            @Param("tokenType") TokenType tokenType);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM auth.auth_tokens "
            + "WHERE user_id = :userId AND token_type = 'REFRESH' "
            + "AND session_id = :sessionId AND id > :tokenId)", nativeQuery = true)
    boolean existsNewerRefreshInSession(
            @Param("userId") Long userId,
            @Param("sessionId") UUID sessionId,
            @Param("tokenId") Long tokenId);

    @Modifying(flushAutomatically = true)
    @Query("update AuthToken token set token.revokedAt = :revokedAt "
            + "where token.userId = :userId and token.tokenType = :tokenType and token.revokedAt is null")
    int revokeAllActiveByUserIdAndTokenType(
            @Param("userId") Long userId,
            @Param("tokenType") TokenType tokenType,
            @Param("revokedAt") Instant revokedAt);
}
