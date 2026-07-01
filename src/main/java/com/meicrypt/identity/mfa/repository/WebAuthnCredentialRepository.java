package com.meicrypt.identity.mfa.repository;

import com.meicrypt.identity.mfa.entity.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, UUID> {

    Optional<WebAuthnCredential> findByFactorId(UUID factorId);

    Optional<WebAuthnCredential> findByCredentialId(String credentialId);

    List<WebAuthnCredential> findByFactorIdIn(List<UUID> factorIds);
}
