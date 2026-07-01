package com.meicrypt.identity.application.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Allowed OAuth2 redirect URI for a {@link ClientApplication}.
 *
 * The full URI is stored verbatim - matching at authorization time is exact
 * (spec-compliant, no wildcard/prefix matching).
 */
@Entity
@Table(name = "client_application_redirect_uris",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_redirect_uri_per_client",
                columnNames = {"client_application_id", "redirect_uri"}),
        indexes = @Index(name = "idx_car_client_application_id",
                columnList = "client_application_id"))
public class ClientApplicationRedirectUri {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "client_application_id", nullable = false)
    private UUID clientApplicationId;

    @Column(name = "redirect_uri", nullable = false, length = 1000)
    private String redirectUri;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClientApplicationRedirectUri() {
    }

    public ClientApplicationRedirectUri(UUID clientApplicationId, String redirectUri) {
        this.clientApplicationId = clientApplicationId;
        this.redirectUri = redirectUri;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientApplicationId() {
        return clientApplicationId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientApplicationRedirectUri that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
