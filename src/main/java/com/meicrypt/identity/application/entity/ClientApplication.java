package com.meicrypt.identity.application.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;


/**
 * Client Application entity (Phase 5).
 *
 * Represents an OAuth2/OIDC client registered by an organization (e.g. CRM,
 * ERP, internal dashboard). Each client belongs to exactly one organization -
 * enforcing the platform's multi-tenant boundary.
 *
 * <p>Credential handling contract:
 * <ul>
 *   <li>{@code clientId} is a public opaque identifier, safe to log and share.</li>
 *   <li>{@code clientSecretHash} stores a BCrypt hash. The plaintext secret
 *       is generated in-memory at creation/rotation time and returned to the
 *       caller <b>once</b>; it is never re-derivable.</li>
 *   <li>Public clients (SPA/MOBILE) MUST have {@code clientSecretHash == null}
 *       and MUST require PKCE.</li>
 * </ul>
 */
@Entity
@Table(name = "client_applications",
        uniqueConstraints = {
                @UniqueConstraint(name = "unique_client_slug_per_org",
                        columnNames = {"organization_id", "slug"})
        },
        indexes = {
                @Index(name = "idx_client_applications_organization_id", columnList = "organization_id"),
                @Index(name = "idx_client_applications_client_id", columnList = "client_id"),
                @Index(name = "idx_client_applications_status", columnList = "status"),
                @Index(name = "idx_client_applications_type", columnList = "application_type")
        })
public class ClientApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "homepage_url", length = 500)
    private String homepageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false, length = 20)
    private ApplicationType applicationType = ApplicationType.WEB;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.ACTIVE;

    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    private String clientId;

    @Column(name = "client_secret_hash", length = 255)
    private String clientSecretHash;

    @Column(name = "client_secret_last_rotated_at")
    private Instant clientSecretLastRotatedAt;

    @Column(name = "grant_types", nullable = false, length = 300)
    private String grantTypes = "authorization_code,refresh_token";

    @Column(name = "scopes", nullable = false, length = 500)
    private String scopes = "openid,profile,email";

    @Column(name = "require_pkce", nullable = false)
    private boolean requirePkce = true;

    @Column(name = "require_consent", nullable = false)
    private boolean requireConsent = true;

    @Column(name = "access_token_ttl_seconds", nullable = false)
    private int accessTokenTtlSeconds = 900;

    @Column(name = "refresh_token_ttl_seconds", nullable = false)
    private int refreshTokenTtlSeconds = 1209600;

    /**
     * OIDC Back-Channel Logout endpoint (Phase 8, Module 8.2). When present,
     * MeiCrypt will POST a signed {@code logout_token} here as part of a
     * Single Logout flow terminating the associated SSO session. Nullable -
     * clients that only participate in Phase 3 session logout can leave it blank.
     */
    @Column(name = "backchannel_logout_uri", length = 1000)
    private String backchannelLogoutUri;

    @CreationTimestamp

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    protected ClientApplication() {
    }


    public ClientApplication(UUID organizationId, String name, String slug,
                             ApplicationType applicationType, String clientId,
                             UUID createdByUserId) {
        this.organizationId = organizationId;
        this.name = name;
        this.slug = slug;
        this.applicationType = applicationType;
        this.clientId = clientId;
        this.createdByUserId = createdByUserId;
        this.status = ApplicationStatus.ACTIVE;
    }

    // ---------------------------------------------------------------------
    // Getters / setters
    // ---------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getHomepageUrl() {
        return homepageUrl;
    }

    public void setHomepageUrl(String homepageUrl) {
        this.homepageUrl = homepageUrl;
    }

    public ApplicationType getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(ApplicationType applicationType) {
        this.applicationType = applicationType;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecretHash() {
        return clientSecretHash;
    }

    public void setClientSecretHash(String clientSecretHash) {
        this.clientSecretHash = clientSecretHash;
    }

    public Instant getClientSecretLastRotatedAt() {
        return clientSecretLastRotatedAt;
    }

    public void setClientSecretLastRotatedAt(Instant clientSecretLastRotatedAt) {
        this.clientSecretLastRotatedAt = clientSecretLastRotatedAt;
    }

    public String getGrantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(String grantTypes) {
        this.grantTypes = grantTypes;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public boolean isRequirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(boolean requirePkce) {
        this.requirePkce = requirePkce;
    }

    public boolean isRequireConsent() {
        return requireConsent;
    }

    public void setRequireConsent(boolean requireConsent) {
        this.requireConsent = requireConsent;
    }

    public int getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(int accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public int getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(int refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public String getBackchannelLogoutUri() {
        return backchannelLogoutUri;
    }

    public void setBackchannelLogoutUri(String backchannelLogoutUri) {
        this.backchannelLogoutUri = backchannelLogoutUri;
    }


    // ---------------------------------------------------------------------

    // Domain helpers
    // ---------------------------------------------------------------------

    public boolean isConfidential() {
        return applicationType != null && applicationType.isConfidential();
    }

    public boolean isActive() {
        return status == ApplicationStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientApplication that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ClientApplication{" +
                "id=" + id +
                ", organizationId=" + organizationId +
                ", name='" + name + '\'' +
                ", slug='" + slug + '\'' +
                ", clientId='" + clientId + '\'' +
                ", type=" + applicationType +
                ", status=" + status +
                '}';
    }
}
