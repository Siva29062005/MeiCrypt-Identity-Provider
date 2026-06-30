package com.meicrypt.identity.user.repository;

import com.meicrypt.identity.user.entity.VerificationToken;
import com.meicrypt.identity.user.entity.VerificationTokenStatus;
import com.meicrypt.identity.user.entity.VerificationTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for VerificationToken entity operations.
 */
@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    /**
     * Find verification token by token string
     */
    Optional<VerificationToken> findByToken(String token);

    /**
     * Find pending tokens for a user by type
     */
    @Query("SELECT vt FROM VerificationToken vt WHERE vt.userId = :userId AND vt.tokenType = :type AND vt.status = 'PENDING' AND vt.expiresAt > CURRENT_TIMESTAMP")
    List<VerificationToken> findValidTokensByUserAndType(@Param("userId") UUID userId, @Param("type") VerificationTokenType type);

    /**
     * Find all tokens for a user
     */
    List<VerificationToken> findByUserId(UUID userId);

    /**
     * Find tokens by user and status
     */
    List<VerificationToken> findByUserIdAndStatus(UUID userId, VerificationTokenStatus status);

    /**
     * Find expired tokens
     */
    @Query("SELECT vt FROM VerificationToken vt WHERE vt.status = 'PENDING' AND vt.expiresAt < CURRENT_TIMESTAMP")
    List<VerificationToken> findExpiredTokens();

    /**
     * Delete expired tokens older than specified date
     */
    void deleteByStatusAndExpiresAtBefore(VerificationTokenStatus status, LocalDateTime date);

    /**
     * Count pending tokens for user
     */
    long countByUserIdAndStatus(UUID userId, VerificationTokenStatus status);
}
