package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.DailyRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyRateRepository extends JpaRepository<DailyRate, UUID> {

    @Query("SELECT DISTINCT r.currency FROM DailyRate r WHERE r.hotelId = :hotelId")
    List<String> findCurrenciesByHotelId(@Param("hotelId") UUID hotelId);

    @Query("SELECT r FROM DailyRate r " +
           "WHERE r.hotelId = :hotelId " +
           "AND r.stayDate >= :from " +
           "AND r.stayDate <= :to " +
           "AND ( :roomTypeId IS NULL OR r.roomTypeId = :roomTypeId ) " +
           "AND ( :ratePlanId IS NULL OR r.ratePlanId = :ratePlanId ) " +
           "ORDER BY r.roomTypeId, r.ratePlanId, r.stayDate, r.occupancy")
    List<DailyRate> findByHotelIdAndDateRange(
            @Param("hotelId") UUID hotelId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("roomTypeId") UUID roomTypeId,
            @Param("ratePlanId") UUID ratePlanId);

    @Query("SELECT r FROM DailyRate r " +
           "WHERE r.hotelId = :hotelId " +
           "AND r.roomTypeId IN :roomTypeIds " +
           "AND r.ratePlanId IN :ratePlanIds " +
           "AND r.stayDate >= :from " +
           "AND r.stayDate <= :to")
    List<DailyRate> findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
            @Param("hotelId") UUID hotelId,
            @Param("roomTypeIds") Collection<UUID> roomTypeIds,
            @Param("ratePlanIds") Collection<UUID> ratePlanIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
