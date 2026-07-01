package com.meicrypt.identity.application.service;

import com.meicrypt.identity.application.dto.ClientApplicationCredentialsDTO;
import com.meicrypt.identity.application.dto.ClientApplicationDTO;
import com.meicrypt.identity.application.dto.CreateClientApplicationRequest;
import com.meicrypt.identity.application.dto.UpdateApplicationStatusRequest;
import com.meicrypt.identity.application.dto.UpdateClientApplicationRequest;
import com.meicrypt.identity.application.entity.ApplicationStatus;
import com.meicrypt.identity.application.entity.ApplicationType;
import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.application.entity.ClientApplicationLogoutUri;
import com.meicrypt.identity.application.entity.ClientApplicationRedirectUri;
import com.meicrypt.identity.application.exception.ApplicationStateException;
import com.meicrypt.identity.application.exception.ClientApplicationNotFoundException;
import com.meicrypt.identity.application.exception.PublicClientSecretException;
import com.meicrypt.identity.application.mapper.ClientApplicationMapper;
import com.meicrypt.identity.application.repository.ClientApplicationLogoutUriRepository;
import com.meicrypt.identity.application.repository.ClientApplicationRedirectUriRepository;
import com.meicrypt.identity.application.repository.ClientApplicationRepository;
import com.meicrypt.identity.common.exception.DuplicateResourceException;
import com.meicrypt.identity.common.exception.InvalidOperationException;
import com.meicrypt.identity.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Core service for the Client Application Registry (Phase 5).
 *
 * <p>Modules covered:
 * <ul>
 *   <li>5.1 (Application Discovery) - CRUD operations scoped to an organization.</li>
 *   <li>5.2 (Credential Issuance) - opaque {@code client_id} + BCrypt-hashed
 *       {@code client_secret} generation, one-shot disclosure, and rotation.</li>
 * </ul>
 *
 * <p>Multi-tenant safety: every method accepts an {@code organizationId} and
 * enforces that the target {@link ClientApplication} belongs to it before
 * performing any mutation. Cross-tenant access is impossible even if a valid
 * application id from another organization is provided.
 */
@Service
@Transactional(readOnly = true)
public class ClientApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ClientApplicationService.class);

    private final ClientApplicationRepository applicationRepository;
    private final ClientApplicationRedirectUriRepository redirectUriRepository;
    private final ClientApplicationLogoutUriRepository logoutUriRepository;
    private final ClientApplicationMapper mapper;
    private final ClientCredentialGenerator credentialGenerator;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;

    public ClientApplicationService(ClientApplicationRepository applicationRepository,
                                    ClientApplicationRedirectUriRepository redirectUriRepository,
                                    ClientApplicationLogoutUriRepository logoutUriRepository,
                                    ClientApplicationMapper mapper,
                                    ClientCredentialGenerator credentialGenerator,
                                    PasswordEncoder passwordEncoder,
                                    OrganizationRepository organizationRepository) {
        this.applicationRepository = applicationRepository;
        this.redirectUriRepository = redirectUriRepository;
        this.logoutUriRepository = logoutUriRepository;
        this.mapper = mapper;
        this.credentialGenerator = credentialGenerator;
        this.passwordEncoder = passwordEncoder;
        this.organizationRepository = organizationRepository;
    }

    // ---------------------------------------------------------------------
    // Read operations
    // ---------------------------------------------------------------------

    public List<ClientApplicationDTO> listApplications(UUID organizationId) {
        requireOrganizationExists(organizationId);
        return applicationRepository.findByOrganizationId(organizationId).stream()
                .map(this::projectDTO)
                .toList();
    }

    public ClientApplicationDTO getApplication(UUID organizationId, UUID applicationId) {
        return projectDTO(requireApplication(organizationId, applicationId));
    }

    // ---------------------------------------------------------------------
    // Create - Module 5.2 credential issuance
    // ---------------------------------------------------------------------

    /**
     * Registers a new client application and issues its credentials.
     *
     * The returned {@link ClientApplicationCredentialsDTO} contains the
     * <b>plaintext</b> {@code clientSecret}; it is the only time it will be
     * visible. The service persists only a BCrypt hash. Public clients receive
     * {@code clientSecret = null}.
     */
    @Transactional
    public CreateClientApplicationResult createApplication(UUID organizationId,
                                                           CreateClientApplicationRequest request,
                                                           UUID actorUserId) {
        requireOrganizationExists(organizationId);

        String slug = normalizeSlug(request.slug(), request.name());
        if (applicationRepository.existsByOrganizationIdAndSlug(organizationId, slug)) {
            throw new DuplicateResourceException("ClientApplication", "slug", slug);
        }

        String clientId = generateUniqueClientId();
        ApplicationType type = request.applicationType();

        ClientApplication application = new ClientApplication(
                organizationId, request.name().trim(), slug, type, clientId, actorUserId);
        application.setDescription(request.description());
        application.setLogoUrl(request.logoUrl());
        application.setHomepageUrl(request.homepageUrl());
        // Phase 8, Module 8.2: opt-in Back-Channel Logout endpoint.
        if (request.backchannelLogoutUri() != null && !request.backchannelLogoutUri().isBlank()) {
            application.setBackchannelLogoutUri(normalizeUri(request.backchannelLogoutUri()));
        }
        applyGrantsAndScopes(application, request.grantTypes(), request.scopes(), true);

        applyBooleans(application, request.requirePkce(), request.requireConsent(), true);
        applyTtls(application, request.accessTokenTtlSeconds(), request.refreshTokenTtlSeconds());

        // Enforce PKCE for public clients regardless of the incoming flag.
        if (!type.isConfidential()) {
            application.setRequirePkce(true);
        }

        // Issue secret (confidential only).
        String plaintextSecret = null;
        if (type.isConfidential()) {
            plaintextSecret = credentialGenerator.generateClientSecret();
            application.setClientSecretHash(passwordEncoder.encode(plaintextSecret));
            application.setClientSecretLastRotatedAt(Instant.now());
        }

        ClientApplication saved = applicationRepository.save(application);
        replaceRedirectUris(saved.getId(), request.redirectUris());
        replaceLogoutUris(saved.getId(), request.postLogoutRedirectUris());

        logger.info("Registered client application {} (clientId={}) in organization {}",
                saved.getId(), saved.getClientId(), organizationId);

        ClientApplicationDTO dto = projectDTO(saved);
        ClientApplicationCredentialsDTO credentials = mapper.toCredentialsDTO(
                saved,
                plaintextSecret,
                type.isConfidential()
                        ? "Store this client_secret now - it will not be shown again."
                        : "Public client - PKCE is mandatory; no client_secret is issued.");
        return new CreateClientApplicationResult(dto, credentials);
    }

    // ---------------------------------------------------------------------
    // Update
    // ---------------------------------------------------------------------

    @Transactional
    public ClientApplicationDTO updateApplication(UUID organizationId, UUID applicationId,
                                                  UpdateClientApplicationRequest request) {
        ClientApplication app = requireApplication(organizationId, applicationId);
        assertMutable(app);

        if (request.name() != null && !request.name().isBlank()) {
            app.setName(request.name().trim());
        }
        if (request.description() != null) {
            app.setDescription(request.description());
        }
        if (request.logoUrl() != null) {
            app.setLogoUrl(request.logoUrl());
        }
        if (request.homepageUrl() != null) {
            app.setHomepageUrl(request.homepageUrl());
        }
        // Phase 8, Module 8.2: allow clearing (empty string) or replacing the
        // registered Back-Channel Logout URI. Leaving the field null (i.e.
        // absent from the payload) preserves the existing value.
        if (request.backchannelLogoutUri() != null) {
            String value = request.backchannelLogoutUri().trim();
            app.setBackchannelLogoutUri(value.isEmpty() ? null : normalizeUri(value));
        }
        applyGrantsAndScopes(app, request.grantTypes(), request.scopes(), false);

        applyBooleans(app, request.requirePkce(), request.requireConsent(), false);
        applyTtls(app, request.accessTokenTtlSeconds(), request.refreshTokenTtlSeconds());

        // Public clients must keep PKCE on.
        if (!app.getApplicationType().isConfidential()) {
            app.setRequirePkce(true);
        }

        ClientApplication saved = applicationRepository.save(app);

        if (request.redirectUris() != null) {
            replaceRedirectUris(saved.getId(), request.redirectUris());
        }
        if (request.postLogoutRedirectUris() != null) {
            replaceLogoutUris(saved.getId(), request.postLogoutRedirectUris());
        }

        logger.info("Updated client application {} in organization {}", saved.getId(), organizationId);
        return projectDTO(saved);
    }

    // ---------------------------------------------------------------------
    // Status transitions
    // ---------------------------------------------------------------------

    @Transactional
    public ClientApplicationDTO changeStatus(UUID organizationId, UUID applicationId,
                                             UpdateApplicationStatusRequest request) {
        ClientApplication app = requireApplication(organizationId, applicationId);
        ApplicationStatus current = app.getStatus();
        ApplicationStatus target = request.status();
        if (current == target) {
            return projectDTO(app);
        }
        if (current == ApplicationStatus.REVOKED) {
            throw new ApplicationStateException(
                    "Application is REVOKED and cannot transition to " + target);
        }
        app.setStatus(target);
        ClientApplication saved = applicationRepository.save(app);
        logger.info("Client application {} transitioned {} -> {}", applicationId, current, target);
        return projectDTO(saved);
    }

    // ---------------------------------------------------------------------
    // Delete
    // ---------------------------------------------------------------------

    @Transactional
    public void deleteApplication(UUID organizationId, UUID applicationId) {
        ClientApplication app = requireApplication(organizationId, applicationId);
        redirectUriRepository.deleteByClientApplicationId(app.getId());
        logoutUriRepository.deleteByClientApplicationId(app.getId());
        applicationRepository.delete(app);
        logger.info("Deleted client application {} in organization {}", applicationId, organizationId);
    }

    // ---------------------------------------------------------------------
    // Credential rotation (Module 5.2)
    // ---------------------------------------------------------------------

    /**
     * Rotates the client_secret for a confidential application.
     * The old hash is replaced atomically; any deployed instance still using
     * the previous secret will start receiving 401s immediately.
     */
    @Transactional
    public ClientApplicationCredentialsDTO rotateClientSecret(UUID organizationId, UUID applicationId) {
        ClientApplication app = requireApplication(organizationId, applicationId);
        if (!app.getApplicationType().isConfidential()) {
            throw new PublicClientSecretException(
                    "Public clients (" + app.getApplicationType() + ") cannot hold a client_secret");
        }
        assertMutable(app);

        String plaintextSecret = credentialGenerator.generateClientSecret();
        app.setClientSecretHash(passwordEncoder.encode(plaintextSecret));
        app.setClientSecretLastRotatedAt(Instant.now());
        ClientApplication saved = applicationRepository.save(app);
        logger.warn("Rotated client_secret for application {} (clientId={})",
                saved.getId(), saved.getClientId());
        return mapper.toCredentialsDTO(saved, plaintextSecret,
                "Client secret rotated. Update deployments immediately - old secret is invalidated.");
    }

    // ---------------------------------------------------------------------
    // Cross-module accessor (used later by the OAuth engine in Phase 6)
    // ---------------------------------------------------------------------

    /**
     * Verifies a client_secret candidate against the stored hash. Returns the
     * application when active AND the secret matches, empty otherwise. This is
     * the ONLY path Phase 6 token endpoints should use to authenticate a
     * confidential client.
     */
    public java.util.Optional<ClientApplication> authenticateClient(String clientId, String clientSecret) {
        return applicationRepository.findByClientId(clientId)
                .filter(ClientApplication::isActive)
                .filter(app -> app.getClientSecretHash() != null
                        && clientSecret != null
                        && passwordEncoder.matches(clientSecret, app.getClientSecretHash()));
    }

    // ---------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------

    private void requireOrganizationExists(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new ClientApplicationNotFoundException(
                    "Organization " + organizationId + " does not exist");
        }
    }

    private ClientApplication requireApplication(UUID organizationId, UUID applicationId) {
        return applicationRepository.findByIdAndOrganizationId(applicationId, organizationId)
                .orElseThrow(() -> new ClientApplicationNotFoundException(
                        "Client application " + applicationId
                                + " not found in organization " + organizationId));
    }

    private void assertMutable(ClientApplication app) {
        if (app.getStatus() == ApplicationStatus.REVOKED) {
            throw new ApplicationStateException(
                    "Application " + app.getId() + " is REVOKED and cannot be modified");
        }
    }

    private String generateUniqueClientId() {
        // Extremely unlikely to collide (256-bit entropy), but guard anyway.
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = credentialGenerator.generateClientId();
            if (!applicationRepository.existsByClientId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique client_id after 5 attempts");
    }

    private void applyGrantsAndScopes(ClientApplication app,
                                      Collection<String> grantTypes,
                                      Collection<String> scopes,
                                      boolean creation) {
        if (grantTypes != null && !grantTypes.isEmpty()) {
            app.setGrantTypes(joinCsv(grantTypes));
        } else if (creation && (app.getGrantTypes() == null || app.getGrantTypes().isBlank())) {
            app.setGrantTypes("authorization_code,refresh_token");
        }
        if (scopes != null && !scopes.isEmpty()) {
            app.setScopes(joinCsv(scopes));
        } else if (creation && (app.getScopes() == null || app.getScopes().isBlank())) {
            app.setScopes("openid,profile,email");
        }
    }

    private void applyBooleans(ClientApplication app, Boolean pkce, Boolean consent, boolean creation) {
        if (pkce != null) {
            app.setRequirePkce(pkce);
        } else if (creation) {
            app.setRequirePkce(true);
        }
        if (consent != null) {
            app.setRequireConsent(consent);
        } else if (creation) {
            app.setRequireConsent(true);
        }
    }

    private void applyTtls(ClientApplication app, Integer accessTtl, Integer refreshTtl) {
        if (accessTtl != null) {
            app.setAccessTokenTtlSeconds(accessTtl);
        }
        if (refreshTtl != null) {
            app.setRefreshTokenTtlSeconds(refreshTtl);
        }
    }

    private void replaceRedirectUris(UUID applicationId, Collection<String> uris) {
        redirectUriRepository.deleteByClientApplicationId(applicationId);
        if (uris == null || uris.isEmpty()) {
            return;
        }
        Set<String> unique = new HashSet<>();
        for (String raw : uris) {
            String uri = normalizeUri(raw);
            if (uri == null || !unique.add(uri)) {
                continue;
            }
            redirectUriRepository.save(new ClientApplicationRedirectUri(applicationId, uri));
        }
    }

    private void replaceLogoutUris(UUID applicationId, Collection<String> uris) {
        logoutUriRepository.deleteByClientApplicationId(applicationId);
        if (uris == null || uris.isEmpty()) {
            return;
        }
        Set<String> unique = new HashSet<>();
        for (String raw : uris) {
            String uri = normalizeUri(raw);
            if (uri == null || !unique.add(uri)) {
                continue;
            }
            logoutUriRepository.save(new ClientApplicationLogoutUri(applicationId, uri));
        }
    }

    private String normalizeUri(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            URI parsed = URI.create(trimmed);
            if (parsed.getScheme() == null) {
                throw new InvalidOperationException(
                        "Redirect/logout URI must be absolute: " + trimmed);
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidOperationException("Invalid URI: " + trimmed);
        }
        return trimmed;
    }

    private String joinCsv(Collection<String> values) {
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        return String.join(",", cleaned);
    }

    private String normalizeSlug(String slug, String name) {
        String source = (slug == null || slug.isBlank()) ? name : slug;
        if (source == null) {
            throw new InvalidOperationException("Cannot derive slug from empty name");
        }
        String normalized = source.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            throw new InvalidOperationException(
                    "Cannot derive a valid slug from '" + source + "'");
        }
        return normalized;
    }

    private ClientApplicationDTO projectDTO(ClientApplication app) {
        List<ClientApplicationRedirectUri> redirects = app.getId() == null
                ? Collections.emptyList()
                : redirectUriRepository.findByClientApplicationId(app.getId());
        List<ClientApplicationLogoutUri> logouts = app.getId() == null
                ? Collections.emptyList()
                : logoutUriRepository.findByClientApplicationId(app.getId());
        return mapper.toDTO(app, redirects, logouts);
    }

    /**
     * Two-shot result returned by {@link #createApplication}: full DTO for
     * regular consumption plus a one-time credentials envelope.
     */
    public record CreateClientApplicationResult(
            ClientApplicationDTO application,
            ClientApplicationCredentialsDTO credentials
    ) {}
}
