package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByOrganizationIdOrderByName(UUID organizationId);

    List<Branch> findByIdIn(Collection<UUID> ids);

    @Query("select b from Branch b where b.active = true and (:city is null or lower(b.city) = lower(:city)) order by b.city, b.name")
    List<Branch> findActive(@Param("city") String city);
}
