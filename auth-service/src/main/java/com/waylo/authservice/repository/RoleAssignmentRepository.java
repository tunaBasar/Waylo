package com.waylo.authservice.repository;

import com.waylo.authservice.domain.Role;
import com.waylo.authservice.domain.RoleAssignment;
import com.waylo.authservice.domain.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    @Query("select r from RoleAssignment r where r.membership.id = :membershipId")
    List<RoleAssignment> findByMembershipId(@Param("membershipId") UUID membershipId);

    @Query("select case when count(r) > 0 then true else false end from RoleAssignment r "
            + "where r.membership.id = :membershipId and r.role = :role and r.scopeType = :scopeType and r.scopeId = :scopeId")
    boolean existsByMembershipIdAndRoleAndScopeTypeAndScopeId(
            @Param("membershipId") UUID membershipId,
            @Param("role") Role role,
            @Param("scopeType") ScopeType scopeType,
            @Param("scopeId") UUID scopeId);

    RoleAssignment findByIdAndOrganizationId(UUID id, UUID organizationId);
}
