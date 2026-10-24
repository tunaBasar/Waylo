package com.waylo.flightseatinventoryservice.repository;

import com.waylo.flightseatinventoryservice.domain.FlightSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlightSeatRepository extends JpaRepository<FlightSeat, UUID> {

    @Query("select s from FlightSeat s where s.flightNumber = :flightNumber "
            + "and s.closed = false and (s.available = true or s.holdExpiresAt < :now)")
    List<FlightSeat> findAvailable(@Param("flightNumber") String flightNumber, @Param("now") Instant now);

    @Query("select count(s) from FlightSeat s where s.flightNumber = :flightNumber "
            + "and s.closed = false and (s.available = true or s.holdExpiresAt < :now)")
    long countAvailable(@Param("flightNumber") String flightNumber, @Param("now") Instant now);

    Optional<FlightSeat> findByFlightNumberAndSeatNumber(String flightNumber, String seatNumber);

    List<FlightSeat> findByFlightNumber(String flightNumber);

    List<FlightSeat> findByFlightNumberAndSeatType(String flightNumber, String seatType);

    @Modifying
    @Query("update FlightSeat s set s.closed = true, s.available = false, s.reservedBy = null, "
            + "s.reservationId = null, s.holdExpiresAt = null, s.version = s.version + 1 "
            + "where s.flightNumber = :flightNumber and s.closed = false")
    int closeFlightSeats(@Param("flightNumber") String flightNumber);

    boolean existsByFlightNumberAndSeatNumber(String flightNumber, String seatNumber);

    @Modifying
    @Query("update FlightSeat s set s.available = true, s.reservedBy = null, s.reservationId = null, "
            + "s.holdExpiresAt = null, s.version = s.version + 1 "
            + "where s.holdExpiresAt < :now")
    int releaseExpiredHolds(@Param("now") Instant now);
}
