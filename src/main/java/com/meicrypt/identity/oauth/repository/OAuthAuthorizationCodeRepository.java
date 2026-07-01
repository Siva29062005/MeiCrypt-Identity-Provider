package com.meicrypt.identity.oauth.repository;

import com.meicrypt.identity.oauth.entity.OAuthAuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthAuthorizationCodeRepository extends JpaRepository<OAuthAuthorizationCode, UUID> {

    Optional<OAuthAuthorizationCode> findByCodeHash(String codeHash);

    @Modifying
    @Query("DELETE FROM OAuthAuthorizationCode c WHERE c.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
