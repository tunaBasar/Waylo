package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.HotelQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelQuoteRepository extends JpaRepository<HotelQuote, UUID> {

    @Query("SELECT q FROM HotelQuote q LEFT JOIN FETCH q.nights WHERE q.id = :id")
    Optional<HotelQuote> findByIdWithNights(@Param("id") UUID id);

    /**
     * Pessimistic write lock on the quote to prevent concurrent duplicate holds.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM HotelQuote q LEFT JOIN FETCH q.nights WHERE q.id = :id")
    Optional<HotelQuote> findByIdForUpdate(@Param("id") UUID id);
}
