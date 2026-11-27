package com.waylo.flightservice.repository;

import com.waylo.flightservice.domain.FlightSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FlightScheduleRepository extends JpaRepository<FlightSchedule, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    List<FlightSchedule> findAllByOrderByCodeAsc();

    List<FlightSchedule> findByActiveTrueOrderByCodeAsc();
}
