package com.meicrypt.identity.auth.dto;

import com.meicrypt.identity.mfa.dto.MfaChallengeDTO;

/**
 * Discriminated response returned from {@code POST /api/v1/auth/login}.
 *
 * <p>When the user has no active MFA factor, {@link #tokens} is populated and
 * {@link #mfaChallenge} is {@code null} - the historical Phase-3 behaviour.
 * When step-up is required (Phase 9), {@link #tokens} is {@code null} and the
 * client is expected to redeem {@link #mfaChallenge#challengeToken()} via
 * {@code POST /api/v1/mfa/challenges/verify}.
 */
public record LoginResponse(
        TokenResponse tokens,
        MfaChallengeDTO mfaChallenge
) {
    public static LoginResponse ofTokens(TokenResponse tokens) {
        return new LoginResponse(tokens, null);
    }
    public static LoginResponse ofMfaChallenge(MfaChallengeDTO challenge) {
        return new LoginResponse(null, challenge);
    }
    public boolean requiresMfa() { return mfaChallenge != null; }
}
