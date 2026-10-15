package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.RoomStatus;
import com.waylo.hotelservice.domain.RoomType;
import com.waylo.hotelservice.dto.RoomRequest;
import com.waylo.hotelservice.dto.RoomResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RoomService {

    RoomResponse create(UUID hotelId, RoomRequest request);

    RoomResponse getById(UUID hotelId, UUID roomId);

    List<RoomResponse> getByHotelId(UUID hotelId);

    /**
     * Otele ait odalari filtreli getir.
     */
    List<RoomResponse> search(UUID hotelId, RoomType roomType, RoomStatus status, BigDecimal maxPrice);

    RoomResponse update(UUID hotelId, UUID roomId, RoomRequest request);

    void delete(UUID hotelId, UUID roomId);

    RoomResponse updateStatus(UUID hotelId, UUID roomId, RoomStatus status);

    long getAvailableRoomCount(UUID hotelId);
}
