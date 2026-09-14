package com.waylo.flightservice.repository;

import com.waylo.flightservice.domain.Flight;
import com.waylo.flightservice.domain.FlightStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlightRepository extends JpaRepository<Flight, UUID> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    boolean existsByFlightNumber(String flightNumber);

    List<Flight> findByDepartureAirport_IataCodeAndArrivalAirport_IataCodeAndDepartureTimeBetween(
            String departureIataCode,
            String arrivalIataCode,
            Instant from,
            Instant to
    );

    List<Flight> findByDepartureAirport_IataCodeAndDepartureTimeBetween(
            String departureIataCode,
            Instant from,
            Instant to
    );

    List<Flight> findByStatus(FlightStatus status);

    List<Flight> findByAirline(String airline);
}
