package com.meicrypt.identity.sso.repository;

import com.meicrypt.identity.sso.entity.SsoSession;
import com.meicrypt.identity.sso.entity.SsoSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SsoSessionRepository extends JpaRepository<SsoSession, UUID> {

    Optional<SsoSession> findBySsoId(String ssoId);

    Optional<SsoSession> findByUserSessionId(UUID userSessionId);

    List<SsoSession> findByUserIdAndStatus(UUID userId, SsoSessionStatus status);
}
