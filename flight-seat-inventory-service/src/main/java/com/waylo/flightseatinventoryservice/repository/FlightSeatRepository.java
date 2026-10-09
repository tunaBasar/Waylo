package com.waylo.flightseatinventoryservice.repository;

import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlightSeatRepository extends JpaRepository<FlightSeat, UUID> {

    List<FlightSeat> findByFlightNumberAndAvailableTrue(String flightNumber);

    Optional<FlightSeat> findByFlightNumberAndSeatNumber(String flightNumber, String seatNumber);

    List<FlightSeat> findByFlightNumber(String flightNumber);

    List<FlightSeat> findByFlightNumberAndSeatType(String flightNumber, String seatType);

    long countByFlightNumberAndAvailableTrue(String flightNumber);

    boolean existsByFlightNumberAndSeatNumber(String flightNumber, String seatNumber);
}
