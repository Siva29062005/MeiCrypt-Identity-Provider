package com.meicrypt.identity.oauth.repository;

import com.meicrypt.identity.oauth.entity.OAuthAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthAccessTokenRepository extends JpaRepository<OAuthAccessToken, UUID> {

    Optional<OAuthAccessToken> findByTokenHash(String tokenHash);

    Optional<OAuthAccessToken> findByJwtId(String jwtId);

    List<OAuthAccessToken> findByUserIdAndClientApplicationId(UUID userId, UUID clientApplicationId);

    @Modifying
    @Query("UPDATE OAuthAccessToken a SET a.revokedAt = :now, a.revokedReason = :reason " +
           "WHERE a.jwtId = :jti AND a.revokedAt IS NULL")
    int revokeByJti(@Param("jti") String jti,
                    @Param("now") Instant now,
                    @Param("reason") String reason);

    @Modifying
    @Query("UPDATE OAuthAccessToken a SET a.revokedAt = :now, a.revokedReason = :reason " +
           "WHERE a.userId = :userId AND a.clientApplicationId = :clientId AND a.revokedAt IS NULL")
    int revokeAllForUserAndClient(@Param("userId") UUID userId,
                                  @Param("clientId") UUID clientId,
                                  @Param("now") Instant now,
                                  @Param("reason") String reason);
}
