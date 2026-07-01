package com.meicrypt.identity.oauth.entity;

/**
 * Lifecycle of a signing key held in {@code oauth_signing_keys}.
 *
 * <ul>
 *   <li>{@link #ACTIVE}   - exactly one key at any time; used to sign
 *                            newly-issued JWTs.</li>
 *   <li>{@link #ROTATED}  - superseded by a newer ACTIVE key; retained so
 *                            existing JWTs continue to verify until they
 *                            expire.</li>
 *   <li>{@link #REVOKED}  - hard-disabled; excluded from the public JWKS
 *                            document and any newly-issued tokens.</li>
 * </ul>
 */
public enum OAuthSigningKeyStatus {
    ACTIVE,
    ROTATED,
    REVOKED
}
