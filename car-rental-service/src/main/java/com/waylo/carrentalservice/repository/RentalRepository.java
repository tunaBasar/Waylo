package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.Rental;
import com.waylo.carrentalservice.domain.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentalRepository extends JpaRepository<Rental, UUID> {

    Optional<Rental> findByIdempotencyKey(String idempotencyKey);

    List<Rental> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Rental> findByOrganizationIdAndStatusInOrderByCreatedAtDesc(UUID organizationId, Collection<RentalStatus> statuses);

    /** CONFIRMED and unexpired HELD rentals of the given vehicles. */
    @Query("select r from Rental r where r.vehicleId in :ids and (r.status = 'CONFIRMED' "
            + "or (r.status = 'HELD' and r.holdExpiresAt > :now))")
    List<Rental> findBlocking(@Param("ids") Collection<UUID> vehicleIds, @Param("now") Instant now);

    @Query("select r from Rental r where r.status = 'HELD' and r.holdExpiresAt <= :now")
    List<Rental> findExpiredHolds(@Param("now") Instant now);
}
