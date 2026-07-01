package com.meicrypt.identity.oauth.service;

import com.meicrypt.identity.application.entity.ApplicationStatus;
import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.application.entity.ClientApplicationRedirectUri;
import com.meicrypt.identity.application.repository.ClientApplicationRedirectUriRepository;
import com.meicrypt.identity.application.repository.ClientApplicationRepository;
import com.meicrypt.identity.oauth.dto.AuthorizationCodeResponse;
import com.meicrypt.identity.oauth.entity.OAuthAuthorizationCode;
import com.meicrypt.identity.oauth.exception.OAuthException;
import com.meicrypt.identity.oauth.repository.OAuthAuthorizationCodeRepository;
import com.meicrypt.identity.sso.service.SsoSessionService;
import com.meicrypt.identity.user.entity.User;

import com.meicrypt.identity.user.entity.UserStatus;
import com.meicrypt.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Module 6.1 - Authorization endpoint.
 *
 * <p>Given an authenticated end-user (from a Phase 3 session), validates the
 * client, redirect_uri, response_type, scope, and PKCE parameters, then issues
 * a short-lived single-use authorization code.
 *
 * <p>The controller is responsible for building the actual HTTP redirect URL
 * from the {@link AuthorizationCodeResponse} returned here.
 */
@Service
@Transactional
public class OAuthAuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthAuthorizationService.class);
    private static final long CODE_TTL_SECONDS = 60L;
    private static final Set<String> ALLOWED_RESPONSE_TYPES = Set.of("code");

    private final ClientApplicationRepository clientRepository;
    private final ClientApplicationRedirectUriRepository redirectUriRepository;
    private final UserRepository userRepository;
    private final OAuthAuthorizationCodeRepository codeRepository;
    private final ScopeService scopeService;
    private final PkceValidator pkceValidator;
    private final OAuthTokenGenerator tokenGenerator;
    private final SsoSessionService ssoSessionService;

    public OAuthAuthorizationService(ClientApplicationRepository clientRepository,
                                     ClientApplicationRedirectUriRepository redirectUriRepository,
                                     UserRepository userRepository,
                                     OAuthAuthorizationCodeRepository codeRepository,
                                     ScopeService scopeService,
                                     PkceValidator pkceValidator,
                                     OAuthTokenGenerator tokenGenerator,
                                     SsoSessionService ssoSessionService) {
        this.clientRepository = clientRepository;
        this.redirectUriRepository = redirectUriRepository;
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.scopeService = scopeService;
        this.pkceValidator = pkceValidator;
        this.tokenGenerator = tokenGenerator;
        this.ssoSessionService = ssoSessionService;
    }


    /**
     * Resolve a client + redirect_uri pair BEFORE the user is prompted for
     * consent. Any failure here MUST NOT redirect back to the client (the
     * caller could be spoofed) - the controller renders an error page instead.
     */
    public ClientApplication resolveClientForAuthorize(String clientId, String redirectUri) {
        if (clientId == null || clientId.isBlank()) {
            throw OAuthException.invalidRequest("client_id is required");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw OAuthException.invalidRequest("redirect_uri is required");
        }
        ClientApplication client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> OAuthException.invalidClient("Unknown client_id"));
        if (client.getStatus() != ApplicationStatus.ACTIVE) {
            throw OAuthException.unauthorizedClient(
                    "Client is " + client.getStatus() + " and cannot request authorization");
        }
        List<ClientApplicationRedirectUri> registered =
                redirectUriRepository.findByClientApplicationId(client.getId());
        boolean allowed = registered.stream()
                .map(ClientApplicationRedirectUri::getRedirectUri)
                .anyMatch(redirectUri::equals);
        if (!allowed) {
            throw OAuthException.invalidRequest("redirect_uri is not registered for this client");
        }
        return client;
    }

    /**
     * Full authorize path. Everything after this point is safe to surface as
     * a client-side redirect (with error=...) because both client and
     * redirect_uri have been validated by {@link #resolveClientForAuthorize}.
     */
    public AuthorizationCodeResponse issueAuthorizationCode(ClientApplication client,
                                                            UUID userId,
                                                            UUID sessionId,
                                                            String responseType,
                                                            String redirectUri,
                                                            String scope,
                                                            String state,
                                                            String nonce,
                                                            String codeChallenge,
                                                            String codeChallengeMethod) {
        if (!ALLOWED_RESPONSE_TYPES.contains(responseType)) {
            throw OAuthException.unsupportedResponseType(
                    "Only 'code' response_type is supported");
        }

        // PKCE - always mandatory for MeiCrypt.
        pkceValidator.validateChallenge(codeChallenge, codeChallengeMethod);

        // Scope reconciliation (Module 6.2).
        String grantedScopes = scopeService.reconcile(client, scope);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> OAuthException.accessDenied("Authenticated user not found"));
        if (user.getStatus() != UserStatus.ACTIVE || !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw OAuthException.accessDenied("End-user account is not active");
        }
        if (!user.getOrganizationId().equals(client.getOrganizationId())) {
            // Multi-tenant guard: a user MUST authorize only clients registered
            // within their own organization.
            throw OAuthException.accessDenied(
                    "User organization does not match client organization");
        }

        String code = tokenGenerator.generateOpaqueToken();
        String codeHash = tokenGenerator.sha256(code);
        Instant expiresAt = Instant.now().plusSeconds(CODE_TTL_SECONDS);

        OAuthAuthorizationCode entity = new OAuthAuthorizationCode(
                codeHash,
                client.getOrganizationId(),
                client.getId(),
                user.getId(),
                sessionId,
                redirectUri,
                grantedScopes,
                codeChallenge,
                codeChallengeMethod,
                state,
                nonce,
                expiresAt);
        codeRepository.save(entity);

        // Phase 8, Module 8.1: record this client as a participant in the SSO
        // session so a future Single Logout can fan out Back-Channel
        // notifications. Best-effort - never break the OAuth flow if SSO
        // tracking is unavailable (e.g. legacy pre-Phase-8 session).
        if (sessionId != null) {
            try {
                ssoSessionService.recordParticipant(sessionId, client.getId(), grantedScopes);
            } catch (Exception ex) {
                logger.warn("Failed to record SSO participant client={} session={}: {}",
                        client.getClientId(), sessionId, ex.getMessage());
            }
        }

        logger.info("Issued OAuth authorization code for client={} user={} scopes={}",
                client.getClientId(), user.getId(), grantedScopes);
        return new AuthorizationCodeResponse(code, state, redirectUri);
    }
}

