package com.waylo.notificationservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Looks up CONFIRMED, not-cancelled flight order items affected by an upstream flight event
 * (Order model, internal endpoint), so FlightEventListener can notify the right customers.
 * Response is wrapped in the standard ApiResponse envelope.
 */
@FeignClient(name = "package-booking-service", path = "/internal/orders", configuration = InternalTokenFeignConfig.class)
public interface PackageBookingServiceClient {

    /** departureDate is UTC yyyy-MM-dd, may be null (all dates of that flight number). */
    @GetMapping("/flight-items")
    FlightItemListResponse getFlightItems(@RequestParam("flightNumber") String flightNumber,
                                          @RequestParam(value = "departureDate", required = false) String departureDate);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FlightItemListResponse(boolean success, List<FlightItemDto> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FlightItemDto(UUID orderItemId, UUID orderId, UUID customerId) {}
}
