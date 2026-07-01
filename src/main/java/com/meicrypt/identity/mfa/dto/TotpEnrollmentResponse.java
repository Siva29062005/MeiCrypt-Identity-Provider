package com.meicrypt.identity.mfa.dto;

import java.util.UUID;

/**
 * Response returned when a TOTP factor is created (Module 9.1). Carries the
 * base32 secret, the otpauth:// URI, and a base64 PNG QR code so the client
 * can render the pairing screen with zero extra network round-trips.
 */
public record TotpEnrollmentResponse(
        UUID factorId,
        String secretBase32,
        String otpAuthUri,
        String qrCodePngBase64,
        int digits,
        int periodSeconds,
        String algorithm
) {}
