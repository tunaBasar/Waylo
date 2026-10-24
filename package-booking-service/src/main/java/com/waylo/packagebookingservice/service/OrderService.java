package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.dto.HotelOrderCreateRequest;
import com.waylo.packagebookingservice.dto.OrderCreateRequest;
import com.waylo.packagebookingservice.dto.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse create(UUID actorUserId, OrderCreateRequest request);

    OrderResponse createHotelOrder(UUID actorUserId, HotelOrderCreateRequest request);

    OrderResponse getById(UUID actorUserId, UUID orderId);

    List<OrderResponse> listOwn(UUID actorUserId);

    OrderResponse cancel(UUID actorUserId, UUID orderId, Long expectedVersion);
}
