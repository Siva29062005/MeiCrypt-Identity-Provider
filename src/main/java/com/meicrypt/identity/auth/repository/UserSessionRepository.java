package com.meicrypt.identity.auth.repository;

import com.meicrypt.identity.auth.entity.SessionStatus;
import com.meicrypt.identity.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndStatus(UUID userId, SessionStatus status);

    List<UserSession> findByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, SessionStatus status);

    long countByStatus(SessionStatus status);

    long countByOrganizationIdAndStatus(UUID organizationId, SessionStatus status);
}
