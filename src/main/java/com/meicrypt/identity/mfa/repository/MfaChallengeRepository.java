package com.meicrypt.identity.mfa.repository;

import com.meicrypt.identity.mfa.entity.MfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, UUID> {

    Optional<MfaChallenge> findByChallengeToken(String challengeToken);
}
