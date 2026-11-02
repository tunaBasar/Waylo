package com.waylo.checkinservice.client;

import com.waylo.checkinservice.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.util.UUID;

/** package-booking-service internal read model. X-Internal-Token is added by {@link OrderItemFeignConfig}. */
@FeignClient(name = "package-booking-service", contextId = "orderItemClient", path = "/internal/orders/items",
        configuration = OrderItemFeignConfig.class)
public interface OrderItemClient {

    @GetMapping("/{orderItemId}")
    ApiResponse<OrderItemDto> get(@PathVariable("orderItemId") UUID orderItemId);

    record OrderItemDto(UUID orderItemId, UUID orderId, UUID customerId, String orderStatus, String vertical,
                        String cancelOutcome, String supplier, String flightNumber, Instant departureAt,
                        String seatNumber, String pnr, String passengerFirstName, String passengerLastName,
                        String voucherStatus) {}
}
