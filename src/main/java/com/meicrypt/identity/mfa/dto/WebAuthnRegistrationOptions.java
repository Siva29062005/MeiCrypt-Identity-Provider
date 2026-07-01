package com.meicrypt.identity.mfa.dto;

import java.util.List;
import java.util.UUID;

/**
 * Options block matching {@code PublicKeyCredentialCreationOptions} in the
 * WebAuthn spec (WebAuthn Level 2, §5.4). The client feeds this directly into
 * {@code navigator.credentials.create()}.
 */
public record WebAuthnRegistrationOptions(
        UUID factorId,
        RelyingParty rp,
        UserView user,
        String challenge,
        List<PubKeyCredParam> pubKeyCredParams,
        long timeout,
        String attestation,
        AuthenticatorSelection authenticatorSelection
) {
    public record RelyingParty(String id, String name) {}
    public record UserView(String id, String name, String displayName) {}
    public record PubKeyCredParam(String type, int alg) {}
    public record AuthenticatorSelection(String userVerification, String residentKey, boolean requireResidentKey) {}
}
