package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.DailyInventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DailyInventoryRepository extends JpaRepository<DailyInventory, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DailyInventory d WHERE d.hotelId = :hotelId " +
           "AND d.roomTypeId IN :roomTypeIds " +
           "AND d.stayDate BETWEEN :from AND :to " +
           "ORDER BY d.roomTypeId, d.stayDate")
    List<DailyInventory> findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(
            @Param("hotelId") UUID hotelId,
            @Param("roomTypeIds") List<UUID> roomTypeIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT d FROM DailyInventory d WHERE d.hotelId = :hotelId " +
           "AND d.stayDate BETWEEN :from AND :to " +
           "ORDER BY d.roomTypeId, d.stayDate")
    List<DailyInventory> findByHotelIdAndStayDateBetween(
            @Param("hotelId") UUID hotelId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT d FROM DailyInventory d WHERE d.hotelId = :hotelId " +
           "AND d.roomTypeId = :roomTypeId " +
           "AND d.stayDate BETWEEN :from AND :to " +
           "ORDER BY d.roomTypeId, d.stayDate")
    List<DailyInventory> findByHotelIdAndRoomTypeIdAndStayDateBetween(
            @Param("hotelId") UUID hotelId,
            @Param("roomTypeId") UUID roomTypeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
