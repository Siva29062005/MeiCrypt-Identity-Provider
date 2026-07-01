package com.meicrypt.identity.auth.repository;

import com.meicrypt.identity.auth.entity.RefreshToken;
import com.meicrypt.identity.auth.entity.RefreshTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndStatus(UUID userId, RefreshTokenStatus status);

    List<RefreshToken> findBySessionId(UUID sessionId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.status = :status, r.revokedAt = :now, r.revokedReason = :reason " +
           "WHERE r.sessionId = :sessionId AND r.status = com.meicrypt.identity.auth.entity.RefreshTokenStatus.ACTIVE")
    int revokeAllBySession(@Param("sessionId") UUID sessionId,
                           @Param("status") RefreshTokenStatus status,
                           @Param("now") LocalDateTime now,
                           @Param("reason") String reason);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.status = :status, r.revokedAt = :now, r.revokedReason = :reason " +
           "WHERE r.userId = :userId AND r.status = com.meicrypt.identity.auth.entity.RefreshTokenStatus.ACTIVE")
    int revokeAllByUser(@Param("userId") UUID userId,
                        @Param("status") RefreshTokenStatus status,
                        @Param("now") LocalDateTime now,
                        @Param("reason") String reason);
}
