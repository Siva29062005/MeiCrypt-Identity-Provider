package com.meicrypt.identity.application.entity;

/**
 * Lifecycle state of a client application.
 *
 * ACTIVE  - the client may participate in OAuth flows.
 * SUSPENDED - temporarily disabled by the organization; can be re-activated.
 * REVOKED - permanently disabled; new tokens will not be issued.
 */
public enum ApplicationStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED
}
