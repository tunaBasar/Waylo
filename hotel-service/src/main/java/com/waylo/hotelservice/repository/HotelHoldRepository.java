package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.HoldStatus;
import com.waylo.hotelservice.domain.HotelHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelHoldRepository extends JpaRepository<HotelHold, UUID> {

    Optional<HotelHold> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HotelHold h WHERE h.id = :id")
    Optional<HotelHold> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Find HELD holds whose expiresAt <= now (inclusive boundary), ordered by expiry.
     * Pessimistic write lock to prevent concurrent processing by multiple scheduler runs.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HotelHold h WHERE h.status = com.waylo.hotelservice.domain.HoldStatus.HELD " +
           "AND h.expiresAt <= :now ORDER BY h.expiresAt ASC")
    List<HotelHold> findExpiredHeld(@Param("now") Instant now, Pageable pageable);

    long countByQuoteIdAndStatusIn(UUID quoteId, Collection<HoldStatus> statuses);
}
