package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.RoomTypeCreateRequest;
import com.waylo.hotelservice.dto.RoomTypeResponse;
import com.waylo.hotelservice.dto.RoomTypeUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface RoomTypeService {

    RoomTypeResponse create(UUID hotelId, RoomTypeCreateRequest request);

    RoomTypeResponse getById(UUID hotelId, UUID roomTypeId, boolean includeInactive);

    List<RoomTypeResponse> getByHotelId(UUID hotelId, boolean includeInactive);

    RoomTypeResponse update(UUID hotelId, UUID roomTypeId, RoomTypeUpdateRequest request);
}
