package com.meicrypt.identity.oauth.repository;

import com.meicrypt.identity.oauth.entity.OAuthSigningKey;
import com.meicrypt.identity.oauth.entity.OAuthSigningKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthSigningKeyRepository extends JpaRepository<OAuthSigningKey, UUID> {

    Optional<OAuthSigningKey> findByKid(String kid);

    Optional<OAuthSigningKey> findFirstByStatusOrderByCreatedAtDesc(OAuthSigningKeyStatus status);

    List<OAuthSigningKey> findAllByStatusIn(List<OAuthSigningKeyStatus> statuses);
}
