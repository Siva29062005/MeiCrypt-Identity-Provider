package com.meicrypt.identity.mfa.repository;

import com.meicrypt.identity.mfa.entity.WebAuthnChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebAuthnChallengeRepository extends JpaRepository<WebAuthnChallenge, UUID> {

    Optional<WebAuthnChallenge> findByChallengeBase64AndChallengeType(
            String challengeBase64, WebAuthnChallenge.ChallengeType challengeType);
}
