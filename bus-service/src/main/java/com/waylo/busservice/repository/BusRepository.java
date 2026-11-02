package com.waylo.busservice.repository;

import com.waylo.busservice.domain.Bus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusRepository extends JpaRepository<Bus, UUID> {

    List<Bus> findByOrganizationIdOrderByPlateNumber(UUID organizationId);

    List<Bus> findAllByOrderByOrganizationIdAscPlateNumberAsc();

    Optional<Bus> findByPlateNumber(String plateNumber);
}
