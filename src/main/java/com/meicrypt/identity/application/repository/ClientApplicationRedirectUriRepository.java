package com.meicrypt.identity.application.repository;

import com.meicrypt.identity.application.entity.ClientApplicationRedirectUri;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientApplicationRedirectUriRepository
        extends JpaRepository<ClientApplicationRedirectUri, UUID> {

    List<ClientApplicationRedirectUri> findByClientApplicationId(UUID clientApplicationId);

    void deleteByClientApplicationId(UUID clientApplicationId);

    boolean existsByClientApplicationIdAndRedirectUri(UUID clientApplicationId, String redirectUri);
}
