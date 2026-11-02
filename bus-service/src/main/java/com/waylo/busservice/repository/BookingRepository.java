package com.waylo.busservice.repository;

import com.waylo.busservice.domain.Booking;
import com.waylo.busservice.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Booking> findByOrganizationIdAndStatusInOrderByCreatedAtDesc(UUID organizationId, Collection<BookingStatus> statuses);

    List<Booking> findByTripIdAndStatusIn(UUID tripId, Collection<BookingStatus> statuses);

    @Query("select b from Booking b where b.status = 'HELD' and b.holdExpiresAt <= :now")
    List<Booking> findExpiredHolds(@Param("now") Instant now);
}
