package com.meicrypt.identity.audit.entity;

/**
 * Category of principal that emitted a security-sensitive event.
 * Mirrors the CHECK constraint on {@code audit_events.actor_type}.
 */
public enum AuditActorType {
    USER,
    SYSTEM,
    CLIENT_APPLICATION,
    ANONYMOUS
}
