package com.waylo.authservice.repository;

import com.waylo.authservice.domain.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    @Query("select m from OrganizationMembership m where m.user.id = :userId")
    List<OrganizationMembership> findByUserId(@Param("userId") UUID userId);

    @Query("select m from OrganizationMembership m where m.user.id = :userId and m.organization.id = :organizationId")
    Optional<OrganizationMembership> findByUserIdAndOrganizationId(@Param("userId") UUID userId,
                                                                   @Param("organizationId") UUID organizationId);

    @Query("select count(a) from RoleAssignment a where a.membership.user.id = :userId and a.role = com.waylo.authservice.domain.Role.ORGANIZATION_OWNER")
    long countOwnedOrganizations(@Param("userId") UUID userId);
}
