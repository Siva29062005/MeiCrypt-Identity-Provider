package com.meicrypt.identity.oauth.entity;

/**
 * Lifecycle state of an OAuth2 refresh token (Phase 6, Module 6.1).
 */
public enum OAuthRefreshTokenStatus {
    ACTIVE,
    ROTATED,
    REVOKED,
    EXPIRED,
    COMPROMISED
}
