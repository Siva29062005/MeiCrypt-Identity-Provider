package com.meicrypt.identity.notification.entity;

/**
 * Lifecycle of a persisted notification row (Phase 10 outbox pattern).
 *
 * <pre>
 *   PENDING ─► SENDING ─► SENT
 *                    └──► FAILED (retry until attempt_count exceeds max)
 *   PENDING ─► CANCELLED (administrative revocation before dispatch)
 * </pre>
 */
public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED
}
