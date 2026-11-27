package com.waylo.flightservice.repository;

import com.waylo.flightservice.domain.Flight;
import com.waylo.flightservice.domain.FlightStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlightRepository extends JpaRepository<Flight, UUID>, JpaSpecificationExecutor<Flight> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    boolean existsByFlightNumber(String flightNumber);

    @Override
    @EntityGraph(attributePaths = {"departureAirport", "arrivalAirport"})
    Page<Flight> findAll(Specification<Flight> spec, Pageable pageable);

    @Query("SELECT f FROM Flight f JOIN FETCH f.departureAirport d JOIN FETCH f.arrivalAirport a " +
           "WHERE d.iataCode = :from AND a.iataCode = :to " +
           "AND f.departureTime >= :start AND f.departureTime < :end " +
           "AND (:includeCancelled = true OR f.status <> :cancelledStatus) " +
           "ORDER BY f.departureTime ASC")
    List<Flight> searchSchedule(@Param("from") String from,
                                @Param("to") String to,
                                @Param("start") Instant start,
                                @Param("end") Instant end,
                                @Param("includeCancelled") boolean includeCancelled,
                                @Param("cancelledStatus") FlightStatus cancelledStatus);

    List<Flight> findTop5ByScheduleIdAndStatusNotAndDepartureTimeGreaterThanEqualOrderByDepartureTimeAsc(
            UUID scheduleId, FlightStatus status, Instant from);
}
