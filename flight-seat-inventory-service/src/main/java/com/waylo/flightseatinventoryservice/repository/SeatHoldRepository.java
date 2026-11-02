package com.waylo.flightseatinventoryservice.repository;

import com.waylo.flightseatinventoryservice.domain.HoldStatus;
import com.waylo.flightseatinventoryservice.domain.SeatHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {

    Optional<SeatHold> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SeatHold h where h.id = :id")
    Optional<SeatHold> findByIdForUpdate(@Param("id") UUID id);

    List<SeatHold> findBySeatIdAndStatus(UUID seatId, HoldStatus status);

    List<SeatHold> findByStatusAndHoldExpiresAtBefore(HoldStatus status, Instant before);
}
