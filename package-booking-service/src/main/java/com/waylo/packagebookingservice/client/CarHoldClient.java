package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(name="car-rental-service", contextId="carHoldClient", path="/internal/car-holds", configuration=CarHoldFeignConfig.class)
public interface CarHoldClient {
    @PostMapping Envelope<HoldDto> hold(@RequestBody HoldRequest request);
    @PostMapping("/{holdId}/confirm") Envelope<HoldDto> confirm(@PathVariable UUID holdId);
    @PostMapping("/{holdId}/release") Envelope<HoldDto> release(@PathVariable UUID holdId);
    record HoldRequest(UUID pickupBranchId, UUID returnBranchId, String category, String transmission, LocalDateTime pickupAt, LocalDateTime returnAt, int driverAge, UUID principalUserId, String idempotencyKey, UUID orderCorrelationId) {}
    record Envelope<T>(boolean success, T data, String message) {}
    record HoldDto(UUID holdId, UUID organizationId, UUID pickupBranchId, UUID returnBranchId, String category, String transmission, String exampleModel, LocalDateTime pickupAt, LocalDateTime returnAt, int chargeableDays, String currency, BigDecimal totalAmount, BigDecimal depositAmount, Integer kmLimitPerDay, String status, Instant expiresAt) {}
}
