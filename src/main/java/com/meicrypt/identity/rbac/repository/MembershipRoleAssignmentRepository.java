package com.meicrypt.identity.rbac.repository;

import com.meicrypt.identity.rbac.entity.MembershipRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRoleAssignmentRepository extends JpaRepository<MembershipRoleAssignment, UUID> {

    List<MembershipRoleAssignment> findByMembershipId(UUID membershipId);

    List<MembershipRoleAssignment> findByRoleId(UUID roleId);

    Optional<MembershipRoleAssignment> findByMembershipIdAndRoleId(UUID membershipId, UUID roleId);

    boolean existsByMembershipIdAndRoleId(UUID membershipId, UUID roleId);

    void deleteByMembershipIdAndRoleId(UUID membershipId, UUID roleId);

    long countByRoleId(UUID roleId);
}
