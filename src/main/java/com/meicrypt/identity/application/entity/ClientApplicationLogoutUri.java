package com.meicrypt.identity.application.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Allowed OIDC post-logout redirect URI for a {@link ClientApplication}.
 */
@Entity
@Table(name = "client_application_logout_uris",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_logout_uri_per_client",
                columnNames = {"client_application_id", "logout_uri"}),
        indexes = @Index(name = "idx_cal_client_application_id",
                columnList = "client_application_id"))
public class ClientApplicationLogoutUri {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "client_application_id", nullable = false)
    private UUID clientApplicationId;

    @Column(name = "logout_uri", nullable = false, length = 1000)
    private String logoutUri;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClientApplicationLogoutUri() {
    }

    public ClientApplicationLogoutUri(UUID clientApplicationId, String logoutUri) {
        this.clientApplicationId = clientApplicationId;
        this.logoutUri = logoutUri;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientApplicationId() {
        return clientApplicationId;
    }

    public String getLogoutUri() {
        return logoutUri;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientApplicationLogoutUri that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
