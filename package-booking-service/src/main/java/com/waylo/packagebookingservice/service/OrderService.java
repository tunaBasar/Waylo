package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.dto.HotelOrderCreateRequest;
import com.waylo.packagebookingservice.dto.CarOrderCreateRequest;
import com.waylo.packagebookingservice.dto.BusOrderCreateRequest;
import com.waylo.packagebookingservice.dto.InternalOrderResponse;
import com.waylo.packagebookingservice.dto.OrderCreateRequest;
import com.waylo.packagebookingservice.dto.OrderResponse;
import com.waylo.packagebookingservice.dto.VoucherResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse create(UUID actorUserId, OrderCreateRequest request);

    OrderResponse createHotelOrder(UUID actorUserId, HotelOrderCreateRequest request);
    OrderResponse createCarOrder(UUID actorUserId, CarOrderCreateRequest request);

    OrderResponse createBusOrder(UUID actorUserId, BusOrderCreateRequest request);

    OrderResponse createFlightOrder(UUID actorUserId, com.waylo.packagebookingservice.dto.FlightOrderCreateRequest request);

    OrderResponse getById(UUID actorUserId, UUID orderId);

    /** Owner only. QR payloads of the order's items. */
    List<VoucherResponse> getVouchers(UUID actorUserId, UUID orderId);

    List<OrderResponse> listOwn(UUID actorUserId);

    OrderResponse cancel(UUID actorUserId, UUID orderId, Long expectedVersion);

    /** Internal (payment-service): authoritative order with item amounts. */
    InternalOrderResponse getInternal(UUID orderId);

    /** Internal (payment-service): money is in escrow. Confirms hotel holds and sets CONFIRMED. Idempotent. */
    InternalOrderResponse markPaid(UUID orderId);
}
