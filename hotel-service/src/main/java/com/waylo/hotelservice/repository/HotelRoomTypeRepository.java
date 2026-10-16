package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelRoomTypeRepository extends JpaRepository<HotelRoomType, UUID> {

    boolean existsByHotelIdAndCode(UUID hotelId, String code);

    Optional<HotelRoomType> findByIdAndHotelId(UUID id, UUID hotelId);

    List<HotelRoomType> findByHotelIdAndStatusOrderByNameAsc(UUID hotelId, CatalogStatus status);

    List<HotelRoomType> findByHotelIdOrderByNameAsc(UUID hotelId);

    long countByHotelId(UUID hotelId);

    @Query("SELECT h FROM Hotel h WHERE h.id = :hotelId")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Hotel> lockHotelById(@Param("hotelId") UUID hotelId);
}
