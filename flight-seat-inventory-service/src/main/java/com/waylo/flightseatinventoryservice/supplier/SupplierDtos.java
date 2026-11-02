package com.waylo.flightseatinventoryservice.supplier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class SupplierDtos {
    private SupplierDtos() {}

    public record OfferSearch(String from, String to, LocalDate date, int adults) {}

    /** totalAmount is per passenger, what the customer pays. */
    public record FlightOffer(String offerId, String supplier, String flightNumber, String airline,
                              String departureAirport, String arrivalAirport, Instant departureTime,
                              Instant arrivalTime, String cabin, String currency, BigDecimal totalAmount,
                              int availableSeats, Instant expiresAt) {}

    public record HoldCommand(String offerId, String seatNumber, String passengerFirstName,
                              String passengerLastName, UUID principalUserId, String idempotencyKey,
                              String orderCorrelationId) {}

    /** supplierNetAmount is what we pay the supplier at ticketing, 0.00 for INTERNAL. */
    public record FlightHold(UUID holdId, String supplier, String flightNumber, String airline,
                             String departureAirport, String arrivalAirport, Instant departureTime,
                             Instant arrivalTime, String cabin, String seatNumber,
                             String passengerFirstName, String passengerLastName, String currency,
                             BigDecimal totalAmount, BigDecimal supplierNetAmount, String status,
                             Instant expiresAt, String pnr, String ticketNumber) {}

    public record RefundQuote(UUID holdId, String currency, BigDecimal customerRefundAmount,
                              BigDecimal supplierRecoverableAmount, String rule) {}
}
