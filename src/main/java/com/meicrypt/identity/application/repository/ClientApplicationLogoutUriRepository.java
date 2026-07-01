package com.meicrypt.identity.application.repository;

import com.meicrypt.identity.application.entity.ClientApplicationLogoutUri;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientApplicationLogoutUriRepository
        extends JpaRepository<ClientApplicationLogoutUri, UUID> {

    List<ClientApplicationLogoutUri> findByClientApplicationId(UUID clientApplicationId);

    void deleteByClientApplicationId(UUID clientApplicationId);

    boolean existsByClientApplicationIdAndLogoutUri(UUID clientApplicationId, String logoutUri);
}
