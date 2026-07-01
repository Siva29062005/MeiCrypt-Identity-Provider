package com.meicrypt.identity.oauth.service;

import com.meicrypt.identity.oauth.entity.OAuthRefreshTokenStatus;
import com.meicrypt.identity.oauth.repository.OAuthAccessTokenRepository;
import com.meicrypt.identity.oauth.repository.OAuthRefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh-token-reuse handler.
 *
 * <p>The revocation runs in a REQUIRES_NEW transaction so that when the
 * calling {@link OAuthTokenService} subsequently throws
 * {@code invalid_grant}, the outer transaction rollback does <b>not</b>
 * revert the family revocation. Without this indirection the newly-rotated
 * refresh token would remain ACTIVE, defeating the reuse detector.
 */
@Service
public class OAuthReuseHandler {

    private final OAuthRefreshTokenRepository refreshRepository;
    private final OAuthAccessTokenRepository accessRepository;

    public OAuthReuseHandler(OAuthRefreshTokenRepository refreshRepository,
                             OAuthAccessTokenRepository accessRepository) {
        this.refreshRepository = refreshRepository;
        this.accessRepository = accessRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compromiseFamily(UUID userId, UUID clientApplicationId) {
        Instant now = Instant.now();
        refreshRepository.revokeAllForUserAndClient(
                userId, clientApplicationId,
                OAuthRefreshTokenStatus.COMPROMISED, now, "REFRESH_REUSE");
        accessRepository.revokeAllForUserAndClient(
                userId, clientApplicationId, now, "REFRESH_REUSE");
    }
}
