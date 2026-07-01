package com.meicrypt.identity.mfa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.AttestationStatement;
import com.webauthn4j.data.extension.client.AuthenticationExtensionsClientOutputs;
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput;
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput;
import com.webauthn4j.server.ServerProperty;
import com.meicrypt.identity.mfa.config.MfaProperties;
import com.meicrypt.identity.mfa.dto.WebAuthnAssertionOptions;
import com.meicrypt.identity.mfa.dto.WebAuthnAssertionPayload;
import com.meicrypt.identity.mfa.dto.WebAuthnRegistrationOptions;
import com.meicrypt.identity.mfa.entity.MfaFactorStatus;
import com.meicrypt.identity.mfa.entity.MfaFactorType;
import com.meicrypt.identity.mfa.entity.UserMfaFactor;
import com.meicrypt.identity.mfa.entity.WebAuthnChallenge;
import com.meicrypt.identity.mfa.entity.WebAuthnCredential;
import com.meicrypt.identity.mfa.exception.MfaFactorNotFoundException;
import com.meicrypt.identity.mfa.exception.WebAuthnVerificationException;
import com.meicrypt.identity.mfa.repository.UserMfaFactorRepository;
import com.meicrypt.identity.mfa.repository.WebAuthnChallengeRepository;
import com.meicrypt.identity.mfa.repository.WebAuthnCredentialRepository;
import com.meicrypt.identity.user.entity.User;
import com.meicrypt.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Module 9.2 - WebAuthn/Passkey registration and assertion (WebAuthn Level 2).
 * <p>Uses webauthn4j to perform full CBOR attestation/assertion validation
 * (client-data hash, RP-ID hash, signature, sign-counter). Challenges are
 * server-generated (32 random bytes, base64url) and stored as one-shot rows.
 */
@Service
@Transactional
public class WebAuthnService {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    private final UserRepository userRepository;
    private final UserMfaFactorRepository factorRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private final WebAuthnChallengeRepository challengeRepository;
    private final MfaProperties mfaProperties;

    private final WebAuthnManager webAuthnManager;
    private final ObjectConverter objectConverter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebAuthnService(UserRepository userRepository,
                           UserMfaFactorRepository factorRepository,
                           WebAuthnCredentialRepository credentialRepository,
                           WebAuthnChallengeRepository challengeRepository,
                           MfaProperties mfaProperties) {
        this.userRepository = userRepository;
        this.factorRepository = factorRepository;
        this.credentialRepository = credentialRepository;
        this.challengeRepository = challengeRepository;
        this.mfaProperties = mfaProperties;
        this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        this.objectConverter = new ObjectConverter();
    }

    // ------------------------------------------------------------------
    // REGISTRATION CEREMONY
    // ------------------------------------------------------------------

    public WebAuthnRegistrationOptions beginRegistration(UUID userId, String displayName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MfaFactorNotFoundException("User not found: " + userId));

        UserMfaFactor factor = new UserMfaFactor(
                user.getId(), user.getOrganizationId(), MfaFactorType.WEBAUTHN, displayName);
        factor = factorRepository.save(factor);

        String challenge = newChallenge();
        Instant expiresAt = Instant.now().plusSeconds(mfaProperties.webauthnChallengeTtlSeconds());
        challengeRepository.save(new WebAuthnChallenge(
                user.getId(), challenge, WebAuthnChallenge.ChallengeType.REGISTRATION,
                mfaProperties.relyingPartyId(), expiresAt));

        List<WebAuthnRegistrationOptions.PubKeyCredParam> algs = List.of(
                new WebAuthnRegistrationOptions.PubKeyCredParam("public-key", -7),   // ES256
                new WebAuthnRegistrationOptions.PubKeyCredParam("public-key", -257)  // RS256
        );

        return new WebAuthnRegistrationOptions(
                factor.getId(),
                new WebAuthnRegistrationOptions.RelyingParty(
                        mfaProperties.relyingPartyId(), mfaProperties.relyingPartyName()),
                new WebAuthnRegistrationOptions.UserView(
                        B64URL.encodeToString(user.getId().toString().getBytes(StandardCharsets.UTF_8)),
                        user.getEmail(),
                        user.getDisplayName() != null ? user.getDisplayName() : user.getEmail()),
                challenge,
                algs,
                mfaProperties.webauthnChallengeTtlSeconds() * 1000L,
                "none",
                new WebAuthnRegistrationOptions.AuthenticatorSelection(
                        "preferred", "preferred", false));
    }

    public WebAuthnCredential completeRegistration(UUID userId, UUID factorId,
                                                   String credentialId,
                                                   String clientDataJsonBase64,
                                                   String attestationObjectBase64,
                                                   List<String> transports) {
        UserMfaFactor factor = factorRepository.findById(factorId)
                .orElseThrow(() -> new MfaFactorNotFoundException("Factor not found"));
        if (!factor.getUserId().equals(userId)) {
            throw new MfaFactorNotFoundException("Factor not owned by user");
        }
        if (factor.getFactorType() != MfaFactorType.WEBAUTHN) {
            throw new WebAuthnVerificationException("Factor is not a WebAuthn factor");
        }

        try {
            byte[] clientDataBytes = B64URL_DEC.decode(clientDataJsonBase64);
            byte[] attestationBytes = B64URL_DEC.decode(attestationObjectBase64);

            JsonNode clientData = objectMapper.readTree(clientDataBytes);
            String presentedChallenge = clientData.path("challenge").asText();

            WebAuthnChallenge stored = challengeRepository
                    .findByChallengeBase64AndChallengeType(
                            presentedChallenge, WebAuthnChallenge.ChallengeType.REGISTRATION)
                    .orElseThrow(() -> new WebAuthnVerificationException(
                            "Registration challenge not recognised"));
            if (stored.isExpired() || stored.isConsumed()) {
                throw new WebAuthnVerificationException("Registration challenge expired or already consumed");
            }
            if (!stored.getUserId().equals(userId)) {
                throw new WebAuthnVerificationException("Challenge does not belong to user");
            }

            ServerProperty serverProperty = new ServerProperty(
                    new Origin(mfaProperties.relyingPartyOrigin()),
                    mfaProperties.relyingPartyId(),
                    new DefaultChallenge(presentedChallenge.getBytes(StandardCharsets.UTF_8)),
                    null);

            RegistrationRequest request = new RegistrationRequest(attestationBytes, clientDataBytes);
            RegistrationParameters params = new RegistrationParameters(serverProperty, null, false, true);
            RegistrationData data = webAuthnManager.parse(request);
            webAuthnManager.validate(data, params);

            AttestedCredentialData attested = data.getAttestationObject()
                    .getAuthenticatorData().getAttestedCredentialData();
            byte[] credentialIdBytes = attested.getCredentialId();
            String storedCredentialId = B64URL.encodeToString(credentialIdBytes);
            byte[] coseKey = objectConverter.getCborConverter().writeValueAsBytes(attested.getCOSEKey());

            WebAuthnCredential credential = new WebAuthnCredential(
                    factor.getId(),
                    storedCredentialId,
                    Base64.getEncoder().encodeToString(attestationBytes),
                    Base64.getEncoder().encodeToString(coseKey));
            credential.setSignCount(data.getAttestationObject().getAuthenticatorData().getSignCount());
            credential.setUserVerified(data.getAttestationObject().getAuthenticatorData().isFlagUV());
            credential.setBackupEligible(data.getAttestationObject().getAuthenticatorData().isFlagBE());
            credential.setBackupState(data.getAttestationObject().getAuthenticatorData().isFlagBS());
            credential.setAaguid(attested.getAaguid().toString());
            if (transports != null && !transports.isEmpty()) {
                credential.setTransports(String.join(",", transports));
            }
            credentialRepository.save(credential);

            factor.setStatus(MfaFactorStatus.ACTIVE);
            factor.setActivatedAt(Instant.now());
            factor.setLastUsedAt(Instant.now());
            factorRepository.save(factor);

            stored.setConsumedAt(Instant.now());
            challengeRepository.save(stored);

            log.info("WebAuthn factor {} activated for user {}", factor.getId(), userId);
            return credential;
        } catch (WebAuthnVerificationException | MfaFactorNotFoundException e) {
            throw e;
        } catch (Exception ex) {
            throw new WebAuthnVerificationException("WebAuthn registration failed: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------
    // ASSERTION CEREMONY (step-up)
    // ------------------------------------------------------------------

    public WebAuthnAssertionOptions beginAssertion(UUID userId) {
        List<UserMfaFactor> factors = factorRepository
                .findByUserIdAndStatus(userId, MfaFactorStatus.ACTIVE)
                .stream()
                .filter(f -> f.getFactorType() == MfaFactorType.WEBAUTHN)
                .toList();
        if (factors.isEmpty()) {
            throw new MfaFactorNotFoundException("No active WebAuthn factors for user");
        }
        List<UUID> factorIds = factors.stream().map(UserMfaFactor::getId).toList();
        List<WebAuthnCredential> credentials = credentialRepository.findByFactorIdIn(factorIds);

        String challenge = newChallenge();
        Instant expiresAt = Instant.now().plusSeconds(mfaProperties.webauthnChallengeTtlSeconds());
        challengeRepository.save(new WebAuthnChallenge(
                userId, challenge, WebAuthnChallenge.ChallengeType.ASSERTION,
                mfaProperties.relyingPartyId(), expiresAt));

        List<WebAuthnAssertionOptions.AllowedCredential> allowed = new ArrayList<>();
        for (WebAuthnCredential c : credentials) {
            List<String> transports = c.getTransports() == null
                    ? List.of()
                    : Arrays.asList(c.getTransports().split(","));
            allowed.add(new WebAuthnAssertionOptions.AllowedCredential(
                    "public-key", c.getCredentialId(), transports));
        }

        return new WebAuthnAssertionOptions(
                challenge,
                mfaProperties.relyingPartyId(),
                mfaProperties.webauthnChallengeTtlSeconds() * 1000L,
                "preferred",
                allowed);
    }

    public UserMfaFactor verifyAssertion(UUID userId, WebAuthnAssertionPayload payload) {
        try {
            byte[] clientDataBytes = B64URL_DEC.decode(payload.clientDataJsonBase64());
            byte[] authenticatorData = B64URL_DEC.decode(payload.authenticatorDataBase64());
            byte[] signature = B64URL_DEC.decode(payload.signatureBase64());
            byte[] credentialIdBytes = B64URL_DEC.decode(payload.credentialId());

            JsonNode clientData = objectMapper.readTree(clientDataBytes);
            String presentedChallenge = clientData.path("challenge").asText();

            WebAuthnChallenge stored = challengeRepository
                    .findByChallengeBase64AndChallengeType(
                            presentedChallenge, WebAuthnChallenge.ChallengeType.ASSERTION)
                    .orElseThrow(() -> new WebAuthnVerificationException(
                            "Assertion challenge not recognised"));
            if (stored.isExpired() || stored.isConsumed()) {
                throw new WebAuthnVerificationException("Assertion challenge expired or already consumed");
            }
            if (!stored.getUserId().equals(userId)) {
                throw new WebAuthnVerificationException("Challenge does not belong to user");
            }

            WebAuthnCredential credential = credentialRepository.findByCredentialId(payload.credentialId())
                    .orElseThrow(() -> new WebAuthnVerificationException("Credential not registered"));
            UserMfaFactor factor = factorRepository.findById(credential.getFactorId())
                    .orElseThrow(() -> new WebAuthnVerificationException("Factor missing"));
            if (!factor.getUserId().equals(userId) || factor.getStatus() != MfaFactorStatus.ACTIVE) {
                throw new WebAuthnVerificationException("Credential does not belong to an active factor");
            }

            ServerProperty serverProperty = new ServerProperty(
                    new Origin(mfaProperties.relyingPartyOrigin()),
                    mfaProperties.relyingPartyId(),
                    new DefaultChallenge(presentedChallenge.getBytes(StandardCharsets.UTF_8)),
                    null);

            byte[] coseKey = Base64.getDecoder().decode(credential.getPublicKeyCoseBase64());
            var cose = objectConverter.getCborConverter()
                    .readValue(coseKey, com.webauthn4j.data.attestation.authenticator.COSEKey.class);
            AttestedCredentialData attested = new AttestedCredentialData(
                    com.webauthn4j.data.attestation.authenticator.AAGUID.NULL, credentialIdBytes, cose);

            AttestationStatement attestationStatement =
                    new com.webauthn4j.data.attestation.statement.NoneAttestationStatement();
            AuthenticationExtensionsClientOutputs<RegistrationExtensionClientOutput> emptyRegExt =
                    new AuthenticationExtensionsClientOutputs<>();
            com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs<
                    com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput>
                    emptyAuthExt = new com.webauthn4j.data.extension.authenticator
                    .AuthenticationExtensionsAuthenticatorOutputs<>();
            CredentialRecord credentialRecord = new CredentialRecordImpl(
                    attestationStatement,
                    Boolean.valueOf(credential.isUserVerified()),
                    Boolean.valueOf(credential.isBackupEligible()),
                    Boolean.valueOf(credential.isBackupState()),
                    credential.getSignCount(),
                    attested,
                    emptyAuthExt,
                    null,
                    emptyRegExt,
                    java.util.Set.of());

            AuthenticationRequest authRequest = new AuthenticationRequest(
                    credentialIdBytes, authenticatorData, clientDataBytes, signature);
            AuthenticationParameters authParams = new AuthenticationParameters(
                    serverProperty, credentialRecord, null, false, true);

            AuthenticationData authData = webAuthnManager.parse(authRequest);
            webAuthnManager.validate(authData, authParams);

            credential.setSignCount(authData.getAuthenticatorData().getSignCount());
            credential.setLastUsedAt(Instant.now());
            credentialRepository.save(credential);

            stored.setConsumedAt(Instant.now());
            challengeRepository.save(stored);

            factor.setLastUsedAt(Instant.now());
            factorRepository.save(factor);

            return factor;
        } catch (WebAuthnVerificationException | MfaFactorNotFoundException e) {
            throw e;
        } catch (Exception ex) {
            throw new WebAuthnVerificationException(
                    "WebAuthn assertion failed: " + ex.getMessage(), ex);
        }
    }

    public void revokeFactor(UUID userId, UUID factorId) {
        UserMfaFactor factor = factorRepository.findById(factorId)
                .orElseThrow(() -> new MfaFactorNotFoundException("Factor not found"));
        if (!factor.getUserId().equals(userId) || factor.getFactorType() != MfaFactorType.WEBAUTHN) {
            throw new MfaFactorNotFoundException("Factor not owned by user or wrong type");
        }
        factor.setStatus(MfaFactorStatus.REVOKED);
        factor.setRevokedAt(Instant.now());
        factorRepository.save(factor);
    }

    private String newChallenge() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return B64URL.encodeToString(bytes);
    }
}
