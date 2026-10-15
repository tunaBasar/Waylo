package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.Room;
import com.waylo.hotelservice.domain.RoomStatus;
import com.waylo.hotelservice.domain.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByHotelId(UUID hotelId);

    List<Room> findByHotelIdAndStatus(UUID hotelId, RoomStatus status);

    List<Room> findByHotelIdAndRoomType(UUID hotelId, RoomType roomType);

    List<Room> findByHotelIdAndRoomTypeAndStatus(UUID hotelId, RoomType roomType, RoomStatus status);

    List<Room> findByHotelIdAndPricePerNightLessThanEqual(UUID hotelId, BigDecimal maxPrice);

    Optional<Room> findByHotelIdAndRoomNumber(UUID hotelId, String roomNumber);

    boolean existsByHotelIdAndRoomNumber(UUID hotelId, String roomNumber);

    Optional<Room> findByIdAndHotelId(UUID id, UUID hotelId);

    boolean existsByHotelIdAndRoomNumberAndIdNot(UUID hotelId, String roomNumber, UUID id);

    long countByHotelIdAndStatus(UUID hotelId, RoomStatus status);
}
