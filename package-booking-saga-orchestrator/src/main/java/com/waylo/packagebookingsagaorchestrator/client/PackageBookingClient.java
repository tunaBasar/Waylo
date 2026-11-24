package com.waylo.packagebookingsagaorchestrator.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "package-booking-service", path = "/api/bookings")
public interface PackageBookingClient {

    @PostMapping("/{id}/status")
    Object updateStatus(@PathVariable UUID id, @RequestParam String status);

    @PostMapping("/{id}/cancel")
    Object cancelBooking(@PathVariable UUID id);
}
