package com.waylo.busservice.dto;

import com.waylo.busservice.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Request and response records of the bus API. Local times are Europe/Istanbul. */
public final class BusDtos {

    private BusDtos() { }

    // ---- requests ----

    public record TerminalRequest(@NotBlank @Size(max = 100) String city, @NotBlank @Size(max = 150) String name,
                                  Boolean active) { }

    public record BusRequest(@NotBlank @Size(max = 20) String plateNumber, @NotBlank @Size(max = 100) String brand,
                             @NotBlank @Size(max = 100) String model, @Min(1980) @Max(2100) int year,
                             @NotNull SeatLayout layout, @Min(5) @Max(20) int rowCount, Boolean active) { }

    public record StopItem(@NotNull UUID terminalId, @NotNull LocalDateTime arrivalAt, @NotNull LocalDateTime departureAt) { }

    public record FareItem(@Min(0) int fromSeq, @Min(1) int toSeq, @NotNull @DecimalMin("0.01") BigDecimal price) { }

    public record TripRequest(@NotNull UUID busId, @NotBlank @Size(max = 40) String code,
                              @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
                              @NotNull @Size(min = 2, max = 20) List<@Valid StopItem> stops,
                              @NotNull @Size(min = 1, max = 200) List<@Valid FareItem> fares) { }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) { }

    public record PassengerItem(@Min(1) int seatNo, @NotBlank @Size(min = 2, max = 150) String name,
                                @NotNull Gender gender, @Size(max = 20) String identityNumber) { }

    public record HoldRequest(@NotNull UUID tripId, @Min(0) int fromSeq, @Min(1) int toSeq,
                              @NotNull @Size(min = 1, max = 5) List<@Valid PassengerItem> passengers,
                              @NotNull UUID principalUserId, @NotBlank @Size(max = 100) String idempotencyKey,
                              @Size(max = 100) String orderCorrelationId) { }

    // ---- responses ----

    public record TerminalResponse(UUID id, String city, String name, boolean active) {
        public static TerminalResponse of(Terminal t) { return new TerminalResponse(t.id, t.city, t.name, t.active); }
    }

    public record BusResponse(UUID id, UUID organizationId, String plateNumber, String brand, String model, int year,
                              SeatLayout layout, int rowCount, int seatCount, boolean active, boolean sellable,
                              List<DocumentType> missingDocuments, Instant createdAt, Instant updatedAt) {
        public static BusResponse of(Bus b, boolean sellable, List<DocumentType> missing) {
            return new BusResponse(b.id, b.organizationId, b.plateNumber, b.brand, b.model, b.year, b.layout,
                    b.rowCount, b.seatCount, b.active, sellable, missing, b.createdAt, b.updatedAt);
        }
    }

    public record StopResponse(int seq, UUID terminalId, String city, String terminalName,
                               LocalDateTime arrivalAt, LocalDateTime departureAt) { }

    public record FareResponse(int fromSeq, int toSeq, BigDecimal price) { }

    public record TripResponse(UUID id, UUID organizationId, UUID busId, String code, TripStatus status,
                               String currency, List<StopResponse> stops, List<FareResponse> fares,
                               long soldSeats, int seatCount) { }

    public record DocumentResponse(UUID id, UUID organizationId, UUID busId, DocumentType type,
                                   String documentNumber, LocalDate validUntil, DocumentStatus status,
                                   String rejectReason, UUID reviewedBy, Instant reviewedAt, String fileName,
                                   String contentType, long sizeBytes, UUID uploadedBy,
                                   Instant createdAt, Instant updatedAt) {
        public static DocumentResponse of(BusDocument d) {
            return new DocumentResponse(d.id, d.organizationId, d.busId, d.type, d.documentNumber, d.validUntil,
                    d.status, d.rejectReason, d.reviewedBy, d.reviewedAt, d.fileName, d.contentType, d.sizeBytes,
                    d.uploadedBy, d.createdAt, d.updatedAt);
        }
    }

    public record AdminDocumentResponse(DocumentResponse document, String organizationName, String busLabel) { }

    public record AdminBusResponse(BusResponse bus, String organizationName) { }

    public record PartnerStatus(boolean sellable, List<DocumentType> missingOrgDocuments, boolean salesEnabled) { }

    public record TicketResponse(UUID id, int seatNo, String passengerName, Gender gender, BigDecimal price,
                                 BookingStatus status) { }

    public record BookingResponse(UUID id, UUID organizationId, UUID customerId, UUID tripId, int fromSeq, int toSeq,
                                  String currency, BigDecimal totalAmount, BookingStatus status,
                                  Instant holdExpiresAt, LocalDateTime boardingAt, List<TicketResponse> tickets,
                                  Instant createdAt) { }

    public record TripOffer(UUID tripId, UUID organizationId, String companyName, String code, int fromSeq, int toSeq,
                            UUID fromTerminalId, UUID toTerminalId, String fromCity, String toCity,
                            LocalDateTime departureAt, LocalDateTime arrivalAt, BigDecimal price, String currency,
                            int availableSeats, String busBrand, String busModel, SeatLayout layout) { }

    public record SeatState(int seatNo, boolean available, Gender occupantGender) { }

    public record SeatMap(UUID tripId, SeatLayout layout, int rowCount, int fromSeq, int toSeq,
                          List<SeatState> seats) { }

    public record HoldDto(UUID holdId, UUID organizationId, UUID tripId, int fromSeq, int toSeq,
                          UUID fromTerminalId, UUID toTerminalId, String fromCity, String toCity,
                          String fromTerminalName, String toTerminalName, String companyName,
                          LocalDateTime boardingAt, LocalDateTime arrivalAt, List<Integer> seatNos,
                          String currency, BigDecimal totalAmount, BookingStatus status, Instant expiresAt) { }

    public static LocalDateTime local(Instant i) {
        return LocalDateTime.ofInstant(i, com.waylo.busservice.config.BusSettings.ZONE);
    }
}
