package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Internal hotel-service hold API. Requires X-Internal-Token (added by {@link HotelHoldFeignConfig}). */
@FeignClient(name = "hotel-service", contextId = "hotelHoldClient", path = "/internal/hotel-holds",
        configuration = HotelHoldFeignConfig.class)
public interface HotelHoldClient {

    @PostMapping
    Envelope<HoldDto> hold(@RequestBody HoldRequest request);

    @PostMapping("/{holdId}/release")
    Envelope<HoldDto> release(@PathVariable("holdId") UUID holdId);

    record HoldRequest(UUID quoteId, String idempotencyKey, UUID principalUserId, UUID orderCorrelationId) {}

    record Envelope<T>(boolean success, T data, String message) {}

    record HoldDto(
            UUID holdId,
            UUID quoteId,
            UUID hotelId,
            UUID organizationId,
            UUID roomTypeId,
            UUID ratePlanId,
            LocalDate checkIn,
            LocalDate checkOut,
            int nights,
            int rooms,
            String status,
            String currency,
            BigDecimal totalAmount,
            Instant expiresAt
    ) {}
}
