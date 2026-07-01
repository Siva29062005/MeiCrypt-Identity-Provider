package com.meicrypt.identity.sso.entity;

/**
 * Lifecycle states of an SSO session (Phase 8, Module 8.1).
 */
public enum SsoSessionStatus {
    /** Live; can silently seed additional client applications */
    ACTIVE,
    /** Explicitly terminated via Single Logout or user logout */
    TERMINATED,
    /** Passed its expires_at timestamp */
    EXPIRED
}
