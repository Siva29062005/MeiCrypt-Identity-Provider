package com.meicrypt.identity.oauth.service;

import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.application.repository.ClientApplicationRepository;
import com.meicrypt.identity.application.service.ClientApplicationService;
import com.meicrypt.identity.oauth.dto.IntrospectionResponse;
import com.meicrypt.identity.oauth.entity.OAuthAccessToken;
import com.meicrypt.identity.oauth.entity.OAuthRefreshToken;
import com.meicrypt.identity.oauth.entity.OAuthRefreshTokenStatus;
import com.meicrypt.identity.oauth.exception.OAuthException;
import com.meicrypt.identity.oauth.repository.OAuthAccessTokenRepository;
import com.meicrypt.identity.oauth.repository.OAuthRefreshTokenRepository;
import com.meicrypt.identity.user.entity.User;
import com.meicrypt.identity.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RFC 7662 Token Introspection.
 *
 * Confidential clients call this endpoint to determine whether a token they
 * hold (either the JWT access_token or an opaque refresh_token) is still
 * active. Only the client that owns the token receives populated metadata;
 * every other outcome returns {@code {active: false}} per the RFC.
 */
@Service
@Transactional(readOnly = true)
public class OAuthIntrospectionService {

    private final ClientApplicationRepository clientRepository;
    private final ClientApplicationService clientApplicationService;
    private final OAuthAccessTokenRepository accessRepository;
    private final OAuthRefreshTokenRepository refreshRepository;
    private final UserRepository userRepository;
    private final OAuthTokenGenerator tokenGenerator;

    public OAuthIntrospectionService(ClientApplicationRepository clientRepository,
                                     ClientApplicationService clientApplicationService,
                                     OAuthAccessTokenRepository accessRepository,
                                     OAuthRefreshTokenRepository refreshRepository,
                                     UserRepository userRepository,
                                     OAuthTokenGenerator tokenGenerator) {
        this.clientRepository = clientRepository;
        this.clientApplicationService = clientApplicationService;
        this.accessRepository = accessRepository;
        this.refreshRepository = refreshRepository;
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
    }

    public IntrospectionResponse introspect(String token, String clientId, String clientSecret) {
        if (token == null || token.isBlank()) {
            return IntrospectionResponse.inactive();
        }
        ClientApplication client = authenticateClient(clientId, clientSecret);
        String hash = tokenGenerator.sha256(token);

        // Try access token registry first.
        var access = accessRepository.findByTokenHash(hash);
        if (access.isPresent()) {
            OAuthAccessToken a = access.get();
            if (!a.getClientApplicationId().equals(client.getId()) || !a.isActive()) {
                return IntrospectionResponse.inactive();
            }
            User user = userRepository.findById(a.getUserId()).orElse(null);
            return new IntrospectionResponse(
                    true,
                    a.getScopes(),
                    client.getClientId(),
                    user == null ? null : user.getEmail(),
                    "Bearer",
                    a.getExpiresAt().getEpochSecond(),
                    a.getIssuedAt().getEpochSecond(),
                    a.getUserId().toString(),
                    a.getAudience(),
                    tokenGenerator.getIssuer(),
                    a.getJwtId());
        }

        // Fall back to refresh token registry.
        var refresh = refreshRepository.findByTokenHash(hash);
        if (refresh.isPresent()) {
            OAuthRefreshToken r = refresh.get();
            if (!r.getClientApplicationId().equals(client.getId())
                    || r.getStatus() != OAuthRefreshTokenStatus.ACTIVE
                    || r.isExpired()) {
                return IntrospectionResponse.inactive();
            }
            User user = userRepository.findById(r.getUserId()).orElse(null);
            return new IntrospectionResponse(
                    true,
                    r.getScopes(),
                    client.getClientId(),
                    user == null ? null : user.getEmail(),
                    "refresh_token",
                    r.getExpiresAt().getEpochSecond(),
                    r.getIssuedAt().getEpochSecond(),
                    r.getUserId().toString(),
                    client.getClientId(),
                    tokenGenerator.getIssuer(),
                    null);
        }
        return IntrospectionResponse.inactive();
    }

    private ClientApplication authenticateClient(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank()) {
            throw OAuthException.invalidClient("client_id is required");
        }
        ClientApplication client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> OAuthException.invalidClient("Unknown client_id"));
        if (client.isConfidential()) {
            if (clientSecret == null || clientSecret.isBlank()
                    || clientApplicationService.authenticateClient(clientId, clientSecret).isEmpty()) {
                throw OAuthException.invalidClient("Invalid client credentials");
            }
        }
        return client;
    }
}
