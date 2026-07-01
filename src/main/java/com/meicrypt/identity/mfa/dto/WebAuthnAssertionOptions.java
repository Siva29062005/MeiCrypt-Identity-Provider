package com.meicrypt.identity.mfa.dto;

import java.util.List;

/**
 * Options block matching {@code PublicKeyCredentialRequestOptions} (WebAuthn
 * Level 2, §5.5). Fed into {@code navigator.credentials.get()} by the client.
 */
public record WebAuthnAssertionOptions(
        String challenge,
        String rpId,
        long timeout,
        String userVerification,
        List<AllowedCredential> allowCredentials
) {
    public record AllowedCredential(String type, String id, List<String> transports) {}
}
