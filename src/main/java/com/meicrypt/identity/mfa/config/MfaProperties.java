package com.meicrypt.identity.mfa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable parameters for Phase 9 MFA behaviour.
 * <p>Bound to the {@code meicrypt.mfa} block of {@code application.yml}.
 */
@ConfigurationProperties(prefix = "meicrypt.mfa")
public record MfaProperties(
        String issuer,
        String relyingPartyId,
        String relyingPartyName,
        String relyingPartyOrigin,
        long challengeTtlSeconds,
        long webauthnChallengeTtlSeconds,
        int totpAllowedTimeStepSkew,
        int maxChallengeAttempts
) {
    public MfaProperties {
        if (issuer == null || issuer.isBlank()) issuer = "MeiCrypt";
        if (relyingPartyId == null || relyingPartyId.isBlank()) relyingPartyId = "localhost";
        if (relyingPartyName == null || relyingPartyName.isBlank()) relyingPartyName = "MeiCrypt Identity";
        if (relyingPartyOrigin == null || relyingPartyOrigin.isBlank()) relyingPartyOrigin = "http://localhost:8080";
        if (challengeTtlSeconds <= 0) challengeTtlSeconds = 300L;              // 5 minutes
        if (webauthnChallengeTtlSeconds <= 0) webauthnChallengeTtlSeconds = 300L;
        if (totpAllowedTimeStepSkew < 0) totpAllowedTimeStepSkew = 1;
        if (maxChallengeAttempts <= 0) maxChallengeAttempts = 5;
    }
}
