package com.meicrypt.identity.oauth.repository;

import com.meicrypt.identity.oauth.entity.OAuthRefreshToken;
import com.meicrypt.identity.oauth.entity.OAuthRefreshTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthRefreshTokenRepository extends JpaRepository<OAuthRefreshToken, UUID> {

    Optional<OAuthRefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE OAuthRefreshToken t SET t.status = :status, t.revokedAt = :now, t.revokedReason = :reason " +
           "WHERE t.userId = :userId AND t.clientApplicationId = :clientId AND t.status = 'ACTIVE'")
    int revokeAllForUserAndClient(@Param("userId") UUID userId,
                                  @Param("clientId") UUID clientId,
                                  @Param("status") OAuthRefreshTokenStatus status,
                                  @Param("now") Instant now,
                                  @Param("reason") String reason);

    @Modifying
    @Query("UPDATE OAuthRefreshToken t SET t.status = :status, t.revokedAt = :now, t.revokedReason = :reason " +
           "WHERE t.sessionId = :sessionId AND t.status = 'ACTIVE'")
    int revokeAllForSession(@Param("sessionId") UUID sessionId,
                            @Param("status") OAuthRefreshTokenStatus status,
                            @Param("now") Instant now,
                            @Param("reason") String reason);
}
