package com.meicrypt.identity.auth.entity;

/**
 * Lifecycle states of a refresh token in the rotating token chain.
 */
public enum RefreshTokenStatus {
    /** Currently active and usable for token exchange */
    ACTIVE,
    /** Successfully rotated - a child token was issued from this one */
    ROTATED,
    /** Explicitly revoked (e.g. logout) */
    REVOKED,
    /** Passed its expiry timestamp */
    EXPIRED,
    /** Detected as compromised (reuse of already-rotated token) */
    COMPROMISED
}
