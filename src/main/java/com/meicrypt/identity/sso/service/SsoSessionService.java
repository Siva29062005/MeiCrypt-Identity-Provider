package com.meicrypt.identity.sso.service;

import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.application.repository.ClientApplicationRepository;
import com.meicrypt.identity.sso.dto.SsoSessionDTO;
import com.meicrypt.identity.sso.entity.SsoSession;
import com.meicrypt.identity.sso.entity.SsoSessionParticipant;
import com.meicrypt.identity.sso.entity.SsoSessionStatus;
import com.meicrypt.identity.sso.exception.SsoSessionNotFoundException;
import com.meicrypt.identity.sso.repository.SsoSessionParticipantRepository;
import com.meicrypt.identity.sso.repository.SsoSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 8, Module 8.1 - SSO shared session engine.
 *
 * <p>The SSO session is minted at Phase-3 login and lasts as long as the
 * anchoring {@code user_sessions} row. It exists so multiple OAuth client
 * applications can silently obtain tokens against the same authenticated
 * browser context (i.e. seamless multi-app SSO within an organization).
 *
 * <p>Participants are recorded lazily by the OAuth authorization service so
 * Single Logout (Module 8.2) has a durable fan-out list.
 */
@Service
@Transactional
public class SsoSessionService {

    private static final Logger logger = LoggerFactory.getLogger(SsoSessionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SsoSessionRepository sessionRepository;
    private final SsoSessionParticipantRepository participantRepository;
    private final ClientApplicationRepository clientRepository;

    public SsoSessionService(SsoSessionRepository sessionRepository,
                             SsoSessionParticipantRepository participantRepository,
                             ClientApplicationRepository clientRepository) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.clientRepository = clientRepository;
    }

    // ---------------------------------------------------------------------
    // Creation / lookup
    // ---------------------------------------------------------------------

    /**
     * Mint a new SSO session anchored to a fresh Phase-3 user session. Called
     * from {@code AuthenticationService.login} immediately after the session
     * row is persisted.
     */
    public SsoSession openForLogin(UUID userSessionId, UUID userId, UUID organizationId,
                                   String ipAddress, String userAgent, Instant expiresAt) {
        String ssoId = generateSsoId();
        SsoSession session = new SsoSession(userSessionId, userId, organizationId,
                ssoId, ipAddress, userAgent, expiresAt);
        session = sessionRepository.save(session);
        logger.debug("Opened SSO session {} for user={} org={}", session.getId(), userId, organizationId);
        return session;
    }

    @Transactional(readOnly = true)
    public Optional<SsoSession> findByUserSessionId(UUID userSessionId) {
        return sessionRepository.findByUserSessionId(userSessionId);
    }

    @Transactional(readOnly = true)
    public Optional<SsoSession> findBySsoId(String ssoId) {
        return sessionRepository.findBySsoId(ssoId);
    }

    @Transactional(readOnly = true)
    public SsoSessionDTO describe(UUID userSessionId) {
        SsoSession sso = sessionRepository.findByUserSessionId(userSessionId)
                .orElseThrow(() -> new SsoSessionNotFoundException(
                        "No SSO session bound to user session " + userSessionId));
        List<SsoSessionParticipant> participants = participantRepository.findBySsoSessionId(sso.getId());
        List<SsoSessionDTO.ParticipantView> views = participants.stream()
                .map(this::toParticipantView)
                .toList();
        return new SsoSessionDTO(
                sso.getId(),
                sso.getSsoId(),
                sso.getUserId(),
                sso.getOrganizationId(),
                sso.getUserSessionId(),
                sso.getAuthenticatedAt(),
                sso.getExpiresAt(),
                sso.getStatus().name(),
                views);
    }

    // ---------------------------------------------------------------------
    // Participant registration (Module 8.1 -> Module 8.2)
    // ---------------------------------------------------------------------

    /**
     * Idempotently register a participating client application. Called by the
     * OAuth authorization service every time it issues a code so Single
     * Logout knows every relying party for this session.
     */
    public SsoSessionParticipant recordParticipant(UUID userSessionId, UUID clientApplicationId,
                                                   String scope) {
        SsoSession sso = sessionRepository.findByUserSessionId(userSessionId).orElse(null);
        if (sso == null) {
            // Login flow succeeded before Phase 8 rolled out; be resilient and
            // skip participant tracking rather than break the OAuth flow.
            logger.debug("No SSO session for user_session {} - skipping participant tracking",
                    userSessionId);
            return null;
        }
        return participantRepository
                .findBySsoSessionIdAndClientApplicationId(sso.getId(), clientApplicationId)
                .map(existing -> {
                    existing.setLastAuthorizedAt(Instant.now());
                    if (scope != null) existing.setLastScope(scope);
                    return participantRepository.save(existing);
                })
                .orElseGet(() -> participantRepository.save(
                        new SsoSessionParticipant(sso.getId(), clientApplicationId, scope)));
    }

    // ---------------------------------------------------------------------
    // Termination (Module 8.2)
    // ---------------------------------------------------------------------

    /**
     * Terminate the SSO session and return the list of participants so the
     * caller can fan out Back-Channel Logout notifications.
     */
    public TerminationResult terminate(UUID userSessionId, String reason) {
        Optional<SsoSession> lookup = sessionRepository.findByUserSessionId(userSessionId);
        if (lookup.isEmpty()) {
            return new TerminationResult(null, List.of());
        }
        SsoSession sso = lookup.get();
        List<SsoSessionParticipant> participants =
                participantRepository.findBySsoSessionId(sso.getId());
        if (sso.getStatus() == SsoSessionStatus.ACTIVE) {
            sso.setStatus(SsoSessionStatus.TERMINATED);
            sso.setTerminatedAt(Instant.now());
            sso.setTerminationReason(reason);
            sessionRepository.save(sso);
            logger.info("Terminated SSO session {} participants={} reason={}",
                    sso.getId(), participants.size(), reason);
        }
        return new TerminationResult(sso, participants);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String generateSsoId() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return URL_ENCODER.encodeToString(buf);
    }

    private SsoSessionDTO.ParticipantView toParticipantView(SsoSessionParticipant participant) {
        ClientApplication client = clientRepository.findById(participant.getClientApplicationId())
                .orElse(null);
        return new SsoSessionDTO.ParticipantView(
                participant.getClientApplicationId(),
                client == null ? null : client.getClientId(),
                client == null ? null : client.getName(),
                participant.getFirstAuthorizedAt(),
                participant.getLastAuthorizedAt(),
                participant.getLastScope());
    }

    /**
     * Return payload from {@link #terminate}; carries the terminated session
     * (or {@code null} if none existed) and every registered participant.
     */
    public record TerminationResult(SsoSession session, List<SsoSessionParticipant> participants) {
        public boolean hasParticipants() {
            return participants != null && !participants.isEmpty();
        }
    }
}
