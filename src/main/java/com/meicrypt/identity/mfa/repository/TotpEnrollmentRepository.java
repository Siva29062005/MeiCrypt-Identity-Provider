package com.meicrypt.identity.mfa.repository;

import com.meicrypt.identity.mfa.entity.TotpEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TotpEnrollmentRepository extends JpaRepository<TotpEnrollment, UUID> {

    Optional<TotpEnrollment> findByFactorId(UUID factorId);
}
