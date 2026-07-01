package com.meicrypt.identity.rbac.repository;

import com.meicrypt.identity.rbac.entity.Role;
import com.meicrypt.identity.rbac.entity.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByOrganizationId(UUID organizationId);

    Optional<Role> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Role> findByOrganizationIdAndSlug(UUID organizationId, String slug);

    boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);

    List<Role> findByOrganizationIdAndRoleType(UUID organizationId, RoleType roleType);

    List<Role> findByOrganizationIdAndDefaultRoleTrue(UUID organizationId);

    /**
     * Returns roles attached to a given membership, eagerly loading permissions
     * to avoid N+1 queries when computing effective authorities.
     */
    @Query("""
            SELECT DISTINCT r
              FROM Role r
              LEFT JOIN FETCH r.permissions
              JOIN MembershipRoleAssignment mra ON mra.roleId = r.id
             WHERE mra.membershipId = :membershipId
            """)
    List<Role> findRolesWithPermissionsByMembershipId(@Param("membershipId") UUID membershipId);
}
