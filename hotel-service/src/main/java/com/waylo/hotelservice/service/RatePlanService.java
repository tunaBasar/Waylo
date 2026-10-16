package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.RatePlanCreateRequest;
import com.waylo.hotelservice.dto.RatePlanResponse;
import com.waylo.hotelservice.dto.RatePlanUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface RatePlanService {

    RatePlanResponse create(UUID hotelId, RatePlanCreateRequest request);

    RatePlanResponse getById(UUID hotelId, UUID ratePlanId, boolean includeInactive);

    List<RatePlanResponse> getByHotelId(UUID hotelId, boolean includeInactive);

    RatePlanResponse update(UUID hotelId, UUID ratePlanId, RatePlanUpdateRequest request);
}
