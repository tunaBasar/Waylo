package com.waylo.busservice.repository;

import com.waylo.busservice.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByBookingId(UUID bookingId);

    List<Ticket> findByBookingIdIn(Collection<UUID> bookingIds);

    /** CONFIRMED and unexpired HELD tickets of the trip. */
    @Query("select t from Ticket t where t.tripId = :tripId and (t.status = 'CONFIRMED' "
            + "or (t.status = 'HELD' and t.holdExpiresAt > :now))")
    List<Ticket> findBlocking(@Param("tripId") UUID tripId, @Param("now") Instant now);

    @Query("select t from Ticket t where t.tripId in :tripIds and (t.status = 'CONFIRMED' "
            + "or (t.status = 'HELD' and t.holdExpiresAt > :now))")
    List<Ticket> findBlockingForTrips(@Param("tripIds") Collection<UUID> tripIds, @Param("now") Instant now);
}
