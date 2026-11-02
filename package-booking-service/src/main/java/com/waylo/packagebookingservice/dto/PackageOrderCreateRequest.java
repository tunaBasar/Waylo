package com.waylo.packagebookingservice.dto;

import com.waylo.packagebookingservice.domain.OrderItemVertical;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * POST /api/orders/package (PACKAGE-ORDER-CONTRACT.md). Each item carries the same fields as the single endpoint of its
 * vertical, flat. Fields of other verticals are ignored. Required fields per vertical are checked by the service.
 */
public record PackageOrderCreateRequest(
        @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid Item> items,
        @Size(max = 50) String promoCode,
        @Size(max = 1000) String notes) {

    public record Item(
            @NotNull OrderItemVertical vertical,
            UUID quoteId,
            UUID pickupBranchId, UUID returnBranchId, String category, String transmission,
            LocalDateTime pickupAt, LocalDateTime returnAt, Integer driverAge,
            String offerId, String seatNumber, String passengerFirstName, String passengerLastName,
            UUID tripId, Integer fromSeq, Integer toSeq,
            @Valid List<BusOrderCreateRequest.Passenger> passengers) {}
}
