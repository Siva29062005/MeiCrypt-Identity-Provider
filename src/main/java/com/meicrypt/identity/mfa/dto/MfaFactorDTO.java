package com.meicrypt.identity.mfa.dto;

import com.meicrypt.identity.mfa.entity.MfaFactorStatus;
import com.meicrypt.identity.mfa.entity.MfaFactorType;

import java.time.Instant;
import java.util.UUID;

/**
 * Public projection of a registered MFA factor (Phase 9).
 */
public record MfaFactorDTO(
        UUID id,
        UUID userId,
        MfaFactorType factorType,
        String displayName,
        MfaFactorStatus status,
        boolean primary,
        Instant createdAt,
        Instant activatedAt,
        Instant lastUsedAt
) {}
