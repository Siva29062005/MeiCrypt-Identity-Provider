package com.meicrypt.identity.mfa.controller;

import com.meicrypt.identity.auth.dto.TokenResponse;
import com.meicrypt.identity.auth.service.AuthenticationService;
import com.meicrypt.identity.mfa.dto.VerifyMfaChallengeRequest;
import com.meicrypt.identity.mfa.dto.WebAuthnAssertionPayload;
import com.meicrypt.identity.mfa.entity.MfaChallenge;
import com.meicrypt.identity.mfa.entity.MfaFactorType;
import com.meicrypt.identity.mfa.entity.UserMfaFactor;
import com.meicrypt.identity.mfa.exception.InvalidMfaChallengeStateException;
import com.meicrypt.identity.mfa.exception.InvalidMfaCodeException;
import com.meicrypt.identity.mfa.exception.WebAuthnVerificationException;
import com.meicrypt.identity.mfa.service.MfaChallengeService;
import com.meicrypt.identity.mfa.service.TotpService;
import com.meicrypt.identity.mfa.service.WebAuthnService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Redeems an MFA {@code challengeToken} (issued during password login) for a
 * real Phase-3 token pair. Serves as the single "verify" surface across all
 * factor types so client apps only need to call one endpoint.
 */
@RestController
@RequestMapping("/api/v1/mfa/challenges")
@Tag(name = "MFA - Verify", description = "Redeem an MFA challenge token (Phase 9)")
public class MfaChallengeController {

    private final MfaChallengeService challengeService;
    private final TotpService totpService;
    private final WebAuthnService webAuthnService;
    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MfaChallengeController(MfaChallengeService challengeService,
                                  TotpService totpService,
                                  WebAuthnService webAuthnService,
                                  AuthenticationService authenticationService) {
        this.challengeService = challengeService;
        this.totpService = totpService;
        this.webAuthnService = webAuthnService;
        this.authenticationService = authenticationService;
    }

    @Operation(summary = "Redeem a challenge token by presenting a second-factor proof")
    @PostMapping("/verify")
    public ResponseEntity<TokenResponse> verify(@Valid @RequestBody VerifyMfaChallengeRequest request,
                                                HttpServletRequest http) {
        MfaChallenge challenge = challengeService.loadForVerification(request.challengeToken());
        try {
            UserMfaFactor satisfyingFactor;
            if (request.factorType() == MfaFactorType.TOTP) {
                satisfyingFactor = totpService.verifyTotpCode(challenge.getUserId(), request.proof());
            } else {
                WebAuthnAssertionPayload payload =
                        objectMapper.readValue(request.proof(), WebAuthnAssertionPayload.class);
                satisfyingFactor = webAuthnService.verifyAssertion(challenge.getUserId(), payload);
            }
            challenge = challengeService.markSatisfied(
                    request.challengeToken(), request.factorType(), satisfyingFactor.getId());
        } catch (InvalidMfaCodeException | WebAuthnVerificationException | InvalidMfaChallengeStateException ex) {
            challengeService.recordFailedAttempt(request.challengeToken());
            throw ex;
        } catch (Exception ex) {
            challengeService.recordFailedAttempt(request.challengeToken());
            throw new WebAuthnVerificationException("Failed to parse MFA proof: " + ex.getMessage(), ex);
        }
        TokenResponse tokens = authenticationService.completeLoginAfterMfa(
                challenge, resolveClientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(tokens);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
