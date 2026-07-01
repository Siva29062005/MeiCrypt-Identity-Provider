package com.meicrypt.identity.mfa.entity;

/**
 * Enumeration of concrete second-factor kinds supported by Phase 9.
 * <ul>
 *   <li>{@link #TOTP} - Module 9.1 (RFC 6238 authenticator apps).</li>
 *   <li>{@link #WEBAUTHN} - Module 9.2 (Passkeys / hardware security keys).</li>
 * </ul>
 */
public enum MfaFactorType {
    TOTP,
    WEBAUTHN
}
