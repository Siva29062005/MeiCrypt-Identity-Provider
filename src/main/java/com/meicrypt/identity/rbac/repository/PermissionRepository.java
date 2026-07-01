package com.meicrypt.identity.rbac.repository;

import com.meicrypt.identity.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    List<Permission> findByDomain(String domain);

    Set<Permission> findByCodeIn(Collection<String> codes);
}
