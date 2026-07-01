package com.meicrypt.identity.auth.service;

import com.meicrypt.identity.auth.dto.SessionDTO;
import com.meicrypt.identity.auth.entity.SessionStatus;
import com.meicrypt.identity.auth.mapper.SessionMapper;
import com.meicrypt.identity.auth.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side of active session lookup for user self-service and admin audit.
 */
@Service
@Transactional(readOnly = true)
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final SessionMapper sessionMapper;

    public SessionService(UserSessionRepository sessionRepository, SessionMapper sessionMapper) {
        this.sessionRepository = sessionRepository;
        this.sessionMapper = sessionMapper;
    }

    public List<SessionDTO> listSessions(UUID userId) {
        return sessionRepository.findByUserId(userId).stream()
                .map(sessionMapper::toDTO)
                .toList();
    }

    public List<SessionDTO> listActiveSessions(UUID userId) {
        return sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE).stream()
                .map(sessionMapper::toDTO)
                .toList();
    }

    public long countActiveSessions(UUID userId) {
        return sessionRepository.countByUserIdAndStatus(userId, SessionStatus.ACTIVE);
    }
}
