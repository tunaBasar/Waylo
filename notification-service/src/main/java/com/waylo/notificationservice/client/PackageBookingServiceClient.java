package com.waylo.notificationservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Looks up package bookings affected by an upstream flight event, so FlightEventListener
 * can notify the right customers. Response is wrapped in the standard ApiResponse envelope.
 */
@FeignClient(name = "package-booking-service", path = "/api/bookings")
public interface PackageBookingServiceClient {

    @GetMapping("/flight/{flightId}")
    BookingListResponse getByFlightId(@PathVariable UUID flightId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BookingListResponse(boolean success, List<BookingDto> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BookingDto(UUID id, UUID customerId, UUID flightId) {}
}
