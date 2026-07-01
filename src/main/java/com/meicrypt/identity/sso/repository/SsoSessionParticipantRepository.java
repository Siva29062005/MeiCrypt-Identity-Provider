package com.meicrypt.identity.sso.repository;

import com.meicrypt.identity.sso.entity.SsoSessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SsoSessionParticipantRepository
        extends JpaRepository<SsoSessionParticipant, UUID> {

    List<SsoSessionParticipant> findBySsoSessionId(UUID ssoSessionId);

    Optional<SsoSessionParticipant> findBySsoSessionIdAndClientApplicationId(
            UUID ssoSessionId, UUID clientApplicationId);
}
