package com.meicrypt.identity.mfa.repository;

import com.meicrypt.identity.mfa.entity.MfaFactorStatus;
import com.meicrypt.identity.mfa.entity.MfaFactorType;
import com.meicrypt.identity.mfa.entity.UserMfaFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link UserMfaFactor} rows (Phase 9).
 */
@Repository
public interface UserMfaFactorRepository extends JpaRepository<UserMfaFactor, UUID> {

    List<UserMfaFactor> findByUserId(UUID userId);

    List<UserMfaFactor> findByUserIdAndStatus(UUID userId, MfaFactorStatus status);

    Optional<UserMfaFactor> findByUserIdAndFactorTypeAndStatus(UUID userId,
                                                              MfaFactorType factorType,
                                                              MfaFactorStatus status);

    boolean existsByUserIdAndStatus(UUID userId, MfaFactorStatus status);

    long countByUserIdAndStatus(UUID userId, MfaFactorStatus status);
}
