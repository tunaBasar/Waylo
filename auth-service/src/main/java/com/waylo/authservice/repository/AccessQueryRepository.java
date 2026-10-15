package com.waylo.authservice.repository;

import com.waylo.authservice.domain.MembershipStatus;
import com.waylo.authservice.domain.OrganizationStatus;
import com.waylo.authservice.domain.RoleAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Query-only repository for the access-check endpoint. */
public interface AccessQueryRepository
        extends Repository<RoleAssignment, UUID> {

    @Query("select a from RoleAssignment a join a.membership m join m.organization o "
            + "where m.user.id = :userId "
            + "and o.id = :organizationId "
            + "and m.status = com.waylo.authservice.domain.MembershipStatus.ACTIVE "
            + "and o.status not in ("
            + "    com.waylo.authservice.domain.OrganizationStatus.SUSPENDED, "
            + "    com.waylo.authservice.domain.OrganizationStatus.ARCHIVED"
            + ")")
    List<RoleAssignment> findActiveGrants(
            @Param("userId") UUID userId,
            @Param("organizationId") UUID organizationId);
}
