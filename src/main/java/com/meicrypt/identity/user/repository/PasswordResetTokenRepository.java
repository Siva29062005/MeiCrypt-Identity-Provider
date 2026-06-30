package com.meicrypt.identity.user.repository;

import com.meicrypt.identity.user.entity.PasswordResetToken;
import com.meicrypt.identity.user.entity.VerificationTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for PasswordResetToken entity operations.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Find password reset token by token string
     */
    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Find valid pending token by token string
     */
    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.token = :token AND prt.status = 'PENDING' AND prt.expiresAt > CURRENT_TIMESTAMP")
    Optional<PasswordResetToken> findValidToken(@Param("token") String token);

    /**
     * Find all tokens for a user
     */
    List<PasswordResetToken> findByUserId(UUID userId);

    /**
     * Find tokens by user and status
     */
    List<PasswordResetToken> findByUserIdAndStatus(UUID userId, VerificationTokenStatus status);

    /**
     * Find expired tokens
     */
    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.status = 'PENDING' AND prt.expiresAt < CURRENT_TIMESTAMP")
    List<PasswordResetToken> findExpiredTokens();

    /**
     * Delete expired tokens older than specified date
     */
    void deleteByStatusAndExpiresAtBefore(VerificationTokenStatus status, LocalDateTime date);

    /**
     * Count pending tokens for user
     */
    long countByUserIdAndStatus(UUID userId, VerificationTokenStatus status);

    /**
     * Invalidate all pending tokens for a user
     */
    @Query("UPDATE PasswordResetToken prt SET prt.status = 'REVOKED' WHERE prt.userId = :userId AND prt.status = 'PENDING'")
    void invalidateAllPendingTokensForUser(@Param("userId") UUID userId);
}
