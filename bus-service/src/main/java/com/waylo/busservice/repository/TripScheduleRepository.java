package com.waylo.busservice.repository;

import com.waylo.busservice.domain.TripSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripScheduleRepository extends JpaRepository<TripSchedule, UUID> {

    List<TripSchedule> findByOrganizationIdOrderByCode(UUID organizationId);

    List<TripSchedule> findByActiveTrue();

    List<TripSchedule> findByOrganizationIdAndActiveTrue(UUID organizationId);

    Optional<TripSchedule> findByOrganizationIdAndCode(UUID organizationId, String code);
}
