package com.meicrypt.identity.sso.service;

import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.application.repository.ClientApplicationRepository;
import com.meicrypt.identity.auth.config.JwtProperties;
import com.meicrypt.identity.oauth.service.OAuthSigningKeyService;
import com.meicrypt.identity.sso.entity.SsoSession;
import com.meicrypt.identity.sso.entity.SsoSessionParticipant;
import com.meicrypt.identity.sso.entity.SsoSessionParticipant.LogoutNotificationState;
import com.meicrypt.identity.sso.repository.SsoSessionParticipantRepository;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Back-Channel Logout notifier (Phase 8, Module 8.2).
 *
 * <p>For every {@link SsoSessionParticipant} bound to a terminating SSO
 * session, mints a signed {@code logout_token} JWT per OIDC Back-Channel
 * Logout 1.0 §2.4 and POSTs it as
 * {@code application/x-www-form-urlencoded} to the client's
 * {@code backchannel_logout_uri}.
 *
 * <p>Runs asynchronously so the /oauth2/logout HTTP response is never blocked
 * on slow relying parties; state is durably tracked on the participant row.
 */
@Service
public class BackchannelLogoutService {

    private static final Logger logger = LoggerFactory.getLogger(BackchannelLogoutService.class);
    private static final String LOGOUT_TOKEN_TYP = "logout+jwt";
    private static final String LOGOUT_EVENT_CLAIM =
            "http://schemas.openid.net/event/backchannel-logout";

    private final SsoSessionParticipantRepository participantRepository;
    private final ClientApplicationRepository clientRepository;
    private final OAuthSigningKeyService signingKeyService;
    private final JwtProperties jwtProperties;
    private final RestClient restClient;

    public BackchannelLogoutService(SsoSessionParticipantRepository participantRepository,
                                    ClientApplicationRepository clientRepository,
                                    OAuthSigningKeyService signingKeyService,
                                    JwtProperties jwtProperties) {
        this.participantRepository = participantRepository;
        this.clientRepository = clientRepository;
        this.signingKeyService = signingKeyService;
        this.jwtProperties = jwtProperties;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Fire-and-forget fan-out. Every participant is notified independently -
     * one relying-party failure never blocks others.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyParticipantsAsync(SsoSession session,
                                        List<SsoSessionParticipant> participants) {
        for (SsoSessionParticipant p : participants) {
            try {
                notifyOne(session, p);
            } catch (Exception ex) {
                logger.warn("Back-channel logout dispatch failed for participant {}: {}",
                        p.getId(), ex.getMessage());
                markFailed(p, ex.getMessage());
            }
        }
    }

    private void notifyOne(SsoSession session, SsoSessionParticipant participant) {
        ClientApplication client = clientRepository.findById(participant.getClientApplicationId())
                .orElse(null);
        if (client == null || client.getBackchannelLogoutUri() == null
                || client.getBackchannelLogoutUri().isBlank()) {
            markSkipped(participant);
            return;
        }

        String logoutToken = mintLogoutToken(client, session);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("logout_token", logoutToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setCacheControl("no-store");
        headers.set("Pragma", "no-cache");

        ResponseEntity<String> response = restClient.post()
                .uri(client.getBackchannelLogoutUri())
                .headers(h -> h.addAll(headers))
                .body(new HttpEntity<>(form, headers).getBody())
                .retrieve()
                .toEntity(String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            markSent(participant);
        } else {
            markFailed(participant,
                    "RP responded with HTTP " + response.getStatusCode().value());
        }
    }

    private String mintLogoutToken(ClientApplication client, SsoSession session) {
        Instant now = Instant.now();
        var key = signingKeyService.getActiveSigningKey();
        return Jwts.builder()
                .header()
                    .keyId(key.kid())
                    .type(LOGOUT_TOKEN_TYP)
                    .and()
                .issuer(jwtProperties.issuer())
                .subject(session.getUserId().toString())
                .audience().add(client.getClientId()).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                // OIDC BCL: logout_token MUST include the `events` claim with
                // the fixed http://schemas.openid.net/event/backchannel-logout
                // member and MUST include `sid` (session id).
                .claim("events", Map.of(LOGOUT_EVENT_CLAIM, Map.of()))
                .claim("sid", session.getSsoId())
                .claim("token_type", "logout_token")
                .signWith(key.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private void markSent(SsoSessionParticipant participant) {
        participant.setLogoutNotifiedAt(Instant.now());
        participant.setLogoutNotificationState(LogoutNotificationState.SENT);
        participant.setLogoutNotificationError(null);
        participantRepository.save(participant);
    }

    private void markFailed(SsoSessionParticipant participant, String error) {
        participant.setLogoutNotifiedAt(Instant.now());
        participant.setLogoutNotificationState(LogoutNotificationState.FAILED);
        participant.setLogoutNotificationError(truncate(error));
        participantRepository.save(participant);
    }

    private void markSkipped(SsoSessionParticipant participant) {
        participant.setLogoutNotifiedAt(Instant.now());
        participant.setLogoutNotificationState(LogoutNotificationState.SKIPPED);
        participant.setLogoutNotificationError(null);
        participantRepository.save(participant);
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
