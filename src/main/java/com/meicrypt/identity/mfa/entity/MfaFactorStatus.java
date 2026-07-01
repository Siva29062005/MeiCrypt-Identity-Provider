package com.meicrypt.identity.mfa.entity;

/**
 * Lifecycle state of a {@link UserMfaFactor}.
 * <ul>
 *   <li>{@link #PENDING} - enrollment started, not yet confirmed by proof-of-possession.</li>
 *   <li>{@link #ACTIVE}  - confirmed and usable for login step-up.</li>
 *   <li>{@link #REVOKED} - retired; kept for audit but cannot satisfy a challenge.</li>
 * </ul>
 */
public enum MfaFactorStatus {
    PENDING,
    ACTIVE,
    REVOKED
}
