package com.meicrypt.identity.auth.repository;

import com.meicrypt.identity.auth.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {

    Optional<UserDevice> findByUserIdAndDeviceFingerprint(UUID userId, String deviceFingerprint);

    List<UserDevice> findByUserIdOrderByLastSeenAtDesc(UUID userId);
}
