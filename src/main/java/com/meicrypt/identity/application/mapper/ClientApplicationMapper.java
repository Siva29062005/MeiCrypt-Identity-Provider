package com.meicrypt.identity.application.mapper;

import com.meicrypt.identity.application.dto.ClientApplicationCredentialsDTO;
import com.meicrypt.identity.application.dto.ClientApplicationDTO;
import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.application.entity.ClientApplicationLogoutUri;
import com.meicrypt.identity.application.entity.ClientApplicationRedirectUri;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manual entity <-> DTO mapper for the client application domain.
 *
 * Deliberately manual (not MapStruct) - the transformations include list
 * splitting and derived flags that are clearer expressed in Java.
 */
@Component
public class ClientApplicationMapper {

    /**
     * Full DTO projection. Redirect / logout URI collections are passed
     * separately so the caller can control how they were fetched.
     */
    public ClientApplicationDTO toDTO(ClientApplication app,
                                      List<ClientApplicationRedirectUri> redirectUris,
                                      List<ClientApplicationLogoutUri> logoutUris) {
        return new ClientApplicationDTO(
                app.getId(),
                app.getOrganizationId(),
                app.getName(),
                app.getSlug(),
                app.getDescription(),
                app.getLogoUrl(),
                app.getHomepageUrl(),
                app.getApplicationType(),
                app.getStatus(),
                app.getClientId(),
                splitCsv(app.getGrantTypes()),
                splitCsv(app.getScopes()),
                app.isRequirePkce(),
                app.isRequireConsent(),
                app.getAccessTokenTtlSeconds(),
                app.getRefreshTokenTtlSeconds(),
                redirectUris == null
                        ? Collections.emptyList()
                        : redirectUris.stream().map(ClientApplicationRedirectUri::getRedirectUri).sorted().toList(),
                logoutUris == null
                        ? Collections.emptyList()
                        : logoutUris.stream().map(ClientApplicationLogoutUri::getLogoutUri).sorted().toList(),
                app.getBackchannelLogoutUri(),
                app.isConfidential(),

                app.getClientSecretHash() != null,
                app.getClientSecretLastRotatedAt(),
                app.getCreatedAt(),
                app.getUpdatedAt(),
                app.getCreatedByUserId()
        );
    }

    public ClientApplicationCredentialsDTO toCredentialsDTO(ClientApplication app,
                                                            String plaintextSecret,
                                                            String message) {
        return new ClientApplicationCredentialsDTO(
                app.getId(),
                app.getClientId(),
                plaintextSecret,
                Instant.now(),
                message
        );
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
