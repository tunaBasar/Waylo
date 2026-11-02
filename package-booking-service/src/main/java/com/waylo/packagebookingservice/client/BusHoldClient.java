package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@FeignClient(name="bus-service", contextId="busHoldClient", path="/internal/bus-holds", configuration=BusHoldFeignConfig.class)
public interface BusHoldClient {
    @PostMapping Envelope<HoldDto> hold(@RequestBody HoldRequest request);
    @PostMapping("/{holdId}/confirm") Envelope<HoldDto> confirm(@PathVariable UUID holdId);
    @PostMapping("/{holdId}/release") Envelope<HoldDto> release(@PathVariable UUID holdId);
    record Passenger(int seatNo, String name, String gender, String identityNumber) {}
    record HoldRequest(UUID tripId, int fromSeq, int toSeq, List<Passenger> passengers, UUID principalUserId, String idempotencyKey, String orderCorrelationId) {}
    record Envelope<T>(boolean success, T data, String message) {}
    record HoldDto(UUID holdId, UUID organizationId, UUID tripId, int fromSeq, int toSeq, UUID fromTerminalId, UUID toTerminalId, String fromCity, String toCity, String fromTerminalName, String toTerminalName, String companyName, LocalDateTime boardingAt, LocalDateTime arrivalAt, List<Integer> seatNos, String currency, BigDecimal totalAmount, String status, Instant expiresAt) {}
}
