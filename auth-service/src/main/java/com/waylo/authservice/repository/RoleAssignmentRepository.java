package com.waylo.authservice.repository;

import com.waylo.authservice.domain.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    @Query("select r from RoleAssignment r where r.membership.id = :membershipId")
    List<RoleAssignment> findByMembershipId(@Param("membershipId") UUID membershipId);
}
