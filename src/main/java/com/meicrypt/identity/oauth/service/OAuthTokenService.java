package com.meicrypt.identity.oauth.service;

import com.meicrypt.identity.application.entity.ApplicationStatus;
import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.application.repository.ClientApplicationRepository;
import com.meicrypt.identity.application.service.ClientApplicationService;
import com.meicrypt.identity.oauth.dto.OAuthTokenResponse;
import com.meicrypt.identity.oauth.entity.OAuthAccessToken;
import com.meicrypt.identity.oauth.entity.OAuthAuthorizationCode;
import com.meicrypt.identity.oauth.entity.OAuthRefreshToken;
import com.meicrypt.identity.oauth.entity.OAuthRefreshTokenStatus;
import com.meicrypt.identity.oauth.exception.OAuthException;
import com.meicrypt.identity.oauth.repository.OAuthAccessTokenRepository;
import com.meicrypt.identity.oauth.repository.OAuthAuthorizationCodeRepository;
import com.meicrypt.identity.oauth.repository.OAuthRefreshTokenRepository;
import com.meicrypt.identity.user.entity.User;
import com.meicrypt.identity.user.entity.UserStatus;
import com.meicrypt.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Module 6.1 / 6.2 - Token endpoint.
 *
 * <p>Handles two grants:
 * <ul>
 *   <li>{@code authorization_code} - exchange a code + PKCE verifier for a
 *       (JWT access_token, opaque refresh_token) pair.</li>
 *   <li>{@code refresh_token} - rotate refresh tokens with reuse detection.</li>
 * </ul>
 *
 * Confidential clients MUST authenticate with client_secret; public clients
 * (SPA/MOBILE) MUST NOT send one but MUST supply PKCE.
 */
@Service
@Transactional
public class OAuthTokenService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTokenService.class);
    private static final Set<String> SUPPORTED_GRANTS = Set.of("authorization_code", "refresh_token");

    private final ClientApplicationRepository clientRepository;
    private final ClientApplicationService clientApplicationService;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final OAuthAccessTokenRepository accessRepository;
    private final OAuthRefreshTokenRepository refreshRepository;
    private final UserRepository userRepository;
    private final PkceValidator pkceValidator;
    private final ScopeService scopeService;
    private final OAuthTokenGenerator tokenGenerator;
    private final OAuthReuseHandler reuseHandler;

    public OAuthTokenService(ClientApplicationRepository clientRepository,
                             ClientApplicationService clientApplicationService,
                             OAuthAuthorizationCodeRepository codeRepository,
                             OAuthAccessTokenRepository accessRepository,
                             OAuthRefreshTokenRepository refreshRepository,
                             UserRepository userRepository,
                             PkceValidator pkceValidator,
                             ScopeService scopeService,
                             OAuthTokenGenerator tokenGenerator,
                             OAuthReuseHandler reuseHandler) {
        this.clientRepository = clientRepository;
        this.clientApplicationService = clientApplicationService;
        this.codeRepository = codeRepository;
        this.accessRepository = accessRepository;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
        this.pkceValidator = pkceValidator;
        this.scopeService = scopeService;
        this.tokenGenerator = tokenGenerator;
        this.reuseHandler = reuseHandler;
    }


    // ---------------------------------------------------------------------
    // Grant dispatch
    // ---------------------------------------------------------------------

    public OAuthTokenResponse issueToken(String grantType,
                                         String clientId, String clientSecret,
                                         String code, String redirectUri, String codeVerifier,
                                         String refreshToken, String scope) {
        if (grantType == null || grantType.isBlank()) {
            throw OAuthException.invalidRequest("grant_type is required");
        }
        if (!SUPPORTED_GRANTS.contains(grantType)) {
            throw OAuthException.unsupportedGrantType("Grant type '" + grantType + "' is not supported");
        }
        ClientApplication client = authenticateClient(clientId, clientSecret);
        return switch (grantType) {
            case "authorization_code" -> handleAuthorizationCode(client, code, redirectUri, codeVerifier);
            case "refresh_token" -> handleRefresh(client, refreshToken, scope);
            default -> throw OAuthException.unsupportedGrantType(grantType);
        };
    }

    // ---------------------------------------------------------------------
    // Client authentication (RFC 6749 §2.3)
    // ---------------------------------------------------------------------

    private ClientApplication authenticateClient(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank()) {
            throw OAuthException.invalidClient("client_id is required");
        }
        ClientApplication client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> OAuthException.invalidClient("Unknown client_id"));
        if (client.getStatus() != ApplicationStatus.ACTIVE) {
            throw OAuthException.invalidClient("Client is " + client.getStatus());
        }
        if (client.isConfidential()) {
            if (clientSecret == null || clientSecret.isBlank()) {
                throw OAuthException.invalidClient("client_secret is required for confidential clients");
            }
            var authenticated = clientApplicationService.authenticateClient(clientId, clientSecret);
            if (authenticated.isEmpty()) {
                throw OAuthException.invalidClient("Invalid client credentials");
            }
        } else {
            // Public clients MUST NOT send a secret.
            if (clientSecret != null && !clientSecret.isBlank()) {
                throw OAuthException.invalidClient(
                        "Public clients must not present a client_secret");
            }
        }
        return client;
    }

    // ---------------------------------------------------------------------
    // authorization_code grant (Module 6.1)
    // ---------------------------------------------------------------------

    private OAuthTokenResponse handleAuthorizationCode(ClientApplication client, String code,
                                                      String redirectUri, String codeVerifier) {
        if (code == null || code.isBlank()) {
            throw OAuthException.invalidRequest("code is required");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw OAuthException.invalidRequest("redirect_uri is required");
        }
        String codeHash = tokenGenerator.sha256(code);
        OAuthAuthorizationCode stored = codeRepository.findByCodeHash(codeHash)
                .orElseThrow(() -> OAuthException.invalidGrant("Authorization code not found"));

        if (!stored.getClientApplicationId().equals(client.getId())) {
            // Immediately invalidate to prevent any brute-force probe.
            stored.setConsumedAt(Instant.now());
            codeRepository.save(stored);
            throw OAuthException.invalidGrant("Authorization code was issued to a different client");
        }
        if (!redirectUri.equals(stored.getRedirectUri())) {
            throw OAuthException.invalidGrant("redirect_uri does not match the authorization request");
        }
        if (stored.isConsumed()) {
            // Replay - revoke everything from this session for safety.
            revokeSessionArtifacts(stored, "AUTHORIZATION_CODE_REPLAY");
            throw OAuthException.invalidGrant("Authorization code has already been used");
        }
        if (stored.isExpired()) {
            throw OAuthException.invalidGrant("Authorization code has expired");
        }

        // PKCE verification (mandatory).
        pkceValidator.verify(codeVerifier, stored.getCodeChallenge(), stored.getCodeChallengeMethod());

        // Consume the code atomically.
        stored.setConsumedAt(Instant.now());
        codeRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> OAuthException.invalidGrant("End-user account no longer exists"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw OAuthException.invalidGrant("End-user account is not active");
        }

        return issueTokenPair(client, user, stored.getSessionId(), stored.getScopes(), stored.getNonce());
    }

    // ---------------------------------------------------------------------
    // refresh_token grant (Module 6.1 rotation)
    // ---------------------------------------------------------------------

    private OAuthTokenResponse handleRefresh(ClientApplication client, String refreshToken, String requestedScope) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw OAuthException.invalidRequest("refresh_token is required");
        }
        String refreshHash = tokenGenerator.sha256(refreshToken);
        OAuthRefreshToken presented = refreshRepository.findByTokenHash(refreshHash)
                .orElseThrow(() -> OAuthException.invalidGrant("Unknown refresh token"));

        if (!presented.getClientApplicationId().equals(client.getId())) {
            throw OAuthException.invalidGrant("Refresh token was issued to a different client");
        }
        if (presented.getStatus() != OAuthRefreshTokenStatus.ACTIVE) {
            // Reuse detected - mark the whole chain compromised in a *separate*
            // transaction so the family revocation survives the invalidGrant
            // rollback below.
            reuseHandler.compromiseFamily(presented.getUserId(), client.getId());
            throw OAuthException.invalidGrant("Refresh token reuse detected - all tokens revoked");
        }

        if (presented.isExpired()) {
            presented.setStatus(OAuthRefreshTokenStatus.EXPIRED);
            refreshRepository.save(presented);
            throw OAuthException.invalidGrant("Refresh token has expired");
        }

        User user = userRepository.findById(presented.getUserId())
                .orElseThrow(() -> OAuthException.invalidGrant("End-user account no longer exists"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw OAuthException.invalidGrant("End-user account is not active");
        }

        // Narrow scope on refresh (Module 6.2 - never broaden).
        String scopes = scopeService.narrowOnRefresh(presented.getScopes(), requestedScope);

        // Mark old as ROTATED.
        presented.setStatus(OAuthRefreshTokenStatus.ROTATED);
        presented.setRevokedAt(Instant.now());
        presented.setRevokedReason("ROTATED");
        refreshRepository.save(presented);

        return issueTokenPair(client, user, presented.getSessionId(), scopes, null,
                presented.getTokenHash());
    }

    // ---------------------------------------------------------------------
    // Revocation (RFC 7009)
    // ---------------------------------------------------------------------

    public void revokeToken(String token, String tokenTypeHint, String clientId, String clientSecret) {
        ClientApplication client = authenticateClient(clientId, clientSecret);
        String hash = tokenGenerator.sha256(token);
        // Try refresh first (most common).
        var refresh = refreshRepository.findByTokenHash(hash);
        if (refresh.isPresent()) {
            OAuthRefreshToken t = refresh.get();
            if (!t.getClientApplicationId().equals(client.getId())) return;
            if (t.getStatus() == OAuthRefreshTokenStatus.ACTIVE) {
                t.setStatus(OAuthRefreshTokenStatus.REVOKED);
                t.setRevokedAt(Instant.now());
                t.setRevokedReason("CLIENT_REVOKE");
                refreshRepository.save(t);
            }
            return;
        }
        // Access token
        var access = accessRepository.findByTokenHash(hash);
        access.ifPresent(a -> {
            if (a.getClientApplicationId().equals(client.getId()) && !a.isRevoked()) {
                a.revoke("CLIENT_REVOKE");
                accessRepository.save(a);
            }
        });
    }

    // ---------------------------------------------------------------------
    // Shared issuance
    // ---------------------------------------------------------------------

    private OAuthTokenResponse issueTokenPair(ClientApplication client, User user, UUID sessionId,
                                              String scopes, String nonce) {
        return issueTokenPair(client, user, sessionId, scopes, nonce, null);
    }

    private OAuthTokenResponse issueTokenPair(ClientApplication client, User user, UUID sessionId,
                                              String scopes, String nonce, String parentRefreshHash) {
        var access = tokenGenerator.issueAccessToken(
                user.getId(), user.getOrganizationId(), client.getId(),
                client.getClientId(), sessionId, user.getEmail(), scopes,
                client.getAccessTokenTtlSeconds());
        String accessHash = tokenGenerator.sha256(access.jwt());
        OAuthAccessToken accessEntity = new OAuthAccessToken(
                accessHash, access.jti(), user.getOrganizationId(), client.getId(),
                user.getId(), sessionId, scopes, client.getClientId(), access.expiresAt());
        accessRepository.save(accessEntity);

        String refreshPlain = tokenGenerator.generateOpaqueToken();
        String refreshHash = tokenGenerator.sha256(refreshPlain);
        OAuthRefreshToken refreshEntity = new OAuthRefreshToken(
                refreshHash, user.getOrganizationId(), client.getId(),
                user.getId(), sessionId, scopes,
                Instant.now().plusSeconds(client.getRefreshTokenTtlSeconds()));
        if (parentRefreshHash != null) {
            refreshEntity.setParentTokenHash(parentRefreshHash);
        }
        refreshRepository.save(refreshEntity);

        String idToken = null;
        if (scopeService.parse(scopes).contains("openid")) {
            var id = tokenGenerator.issueIdToken(
                    user.getId(), user.getOrganizationId(), client.getClientId(),
                    user.getEmail(), nonce, client.getAccessTokenTtlSeconds());
            idToken = id.jwt();
        }

        logger.info("Issued OAuth token pair client={} user={} scopes={} openid={}",
                client.getClientId(), user.getId(), scopes, idToken != null);
        return OAuthTokenResponse.bearer(
                access.jwt(),
                client.getAccessTokenTtlSeconds(),
                refreshPlain,
                scopes,
                idToken);
    }

    private void revokeSessionArtifacts(OAuthAuthorizationCode stored, String reason) {
        if (stored.getSessionId() == null) return;
        Instant now = Instant.now();
        refreshRepository.revokeAllForSession(
                stored.getSessionId(), OAuthRefreshTokenStatus.COMPROMISED, now, reason);
    }
}
