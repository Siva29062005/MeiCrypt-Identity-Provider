package com.meicrypt.identity.mfa.controller;

import com.meicrypt.identity.auth.security.AuthenticatedUser;
import com.meicrypt.identity.mfa.dto.MfaFactorDTO;
import com.meicrypt.identity.mfa.entity.UserMfaFactor;
import com.meicrypt.identity.mfa.mapper.MfaFactorMapper;
import com.meicrypt.identity.mfa.repository.UserMfaFactorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only inventory of the caller's own registered MFA factors (Phase 9).
 * <p>Concrete enrollment lives in the type-specific controllers
 * ({@link TotpController}, {@link WebAuthnController}) so this class stays
 * cross-factor.
 */
@RestController
@RequestMapping("/api/v1/mfa/factors")
@Tag(name = "MFA", description = "Multi-Factor Authentication (Phase 9)")
public class MfaFactorController {

    private final UserMfaFactorRepository factorRepository;
    private final MfaFactorMapper mapper;

    public MfaFactorController(UserMfaFactorRepository factorRepository, MfaFactorMapper mapper) {
        this.factorRepository = factorRepository;
        this.mapper = mapper;
    }

    @Operation(summary = "List all MFA factors registered for the authenticated user")
    @GetMapping
    public ResponseEntity<List<MfaFactorDTO>> listFactors(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        List<UserMfaFactor> factors = factorRepository.findByUserId(principal.userId());
        return ResponseEntity.ok(factors.stream().map(mapper::toDto).toList());
    }
}
