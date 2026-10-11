package com.waylo.checkinservice.repository;

import com.waylo.checkinservice.domain.Checkin;
import com.waylo.checkinservice.domain.CheckinStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckinRepository extends JpaRepository<Checkin, UUID> {
    List<Checkin> findByBookingId(UUID bookingId);
    List<Checkin> findByCustomerId(UUID customerId);
    List<Checkin> findByFlightId(UUID flightId);
    Optional<Checkin> findByBookingIdAndFlightId(UUID bookingId, UUID flightId);
    List<Checkin> findByStatus(CheckinStatus status);
}
