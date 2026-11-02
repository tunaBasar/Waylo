package com.waylo.carrentalservice.dto;

import com.waylo.carrentalservice.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Request and response records of the car rental API. */
public final class CarDtos {

    private CarDtos() { }

    // ---- requests ----

    public record BranchRequest(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 100) String city,
                                @NotBlank @Size(max = 300) String address, @Size(max = 40) String phone,
                                @NotNull LocalTime openTime, @NotNull LocalTime closeTime, Boolean active) { }

    public record VehicleRequest(@NotNull UUID homeBranchId, @NotBlank @Size(max = 20) String plateNumber,
                                 @NotBlank @Size(max = 100) String brand, @NotBlank @Size(max = 100) String model,
                                 @Min(1980) @Max(2100) int year, @NotNull CarCategory category,
                                 @NotNull Transmission transmission, @NotNull FuelType fuelType,
                                 @Min(1) @Max(20) int seatCount, Boolean active) { }

    public record RateItem(@NotNull UUID branchId, @NotNull CarCategory category, @NotNull Transmission transmission,
                           @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
                           @NotNull @DecimalMin(value = "0.01") BigDecimal dailyRate,
                           @NotNull @DecimalMin("0.00") BigDecimal depositAmount,
                           @Min(1) Integer kmLimitPerDay, @DecimalMin("0.00") BigDecimal extraKmFee,
                           @Min(18) @Max(99) int minDriverAge, @Min(0) @Max(50) int minLicenseYears) { }

    public record RatesRequest(@NotNull @Valid List<@Valid RateItem> items) { }

    public record RouteItem(@NotNull UUID fromBranchId, @NotNull UUID toBranchId,
                            @NotNull @DecimalMin("0.00") BigDecimal fee,
                            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) { }

    public record RoutesRequest(@NotNull @Valid List<@Valid RouteItem> routes) { }

    public record RejectRequest(@NotBlank @Size(max = 500) String reason) { }

    public record HoldRequest(@NotNull UUID pickupBranchId, @NotNull UUID returnBranchId, @NotNull CarCategory category,
                              @NotNull Transmission transmission, @NotNull LocalDateTime pickupAt,
                              @NotNull LocalDateTime returnAt, @Min(18) @Max(99) int driverAge,
                              @NotNull UUID principalUserId, @NotBlank @Size(max = 100) String idempotencyKey,
                              @Size(max = 100) String orderCorrelationId) { }

    // ---- responses ----

    public record BranchResponse(UUID id, UUID organizationId, String name, String city, String address, String phone,
                                 LocalTime openTime, LocalTime closeTime, boolean active,
                                 Instant createdAt, Instant updatedAt) {
        public static BranchResponse of(Branch b) {
            return new BranchResponse(b.id, b.organizationId, b.name, b.city, b.address, b.phone,
                    b.openTime, b.closeTime, b.active, b.createdAt, b.updatedAt);
        }
    }

    public record PublicBranchResponse(UUID id, UUID organizationId, String name, String city, String address,
                                       LocalTime openTime, LocalTime closeTime) {
        public static PublicBranchResponse of(Branch b) {
            return new PublicBranchResponse(b.id, b.organizationId, b.name, b.city, b.address, b.openTime, b.closeTime);
        }
    }

    public record VehicleResponse(UUID id, UUID organizationId, UUID homeBranchId, String plateNumber, String brand,
                                  String model, int year, CarCategory category, Transmission transmission,
                                  FuelType fuelType, int seatCount, boolean active, boolean sellable,
                                  List<DocumentType> missingDocuments, Instant createdAt, Instant updatedAt) {
        public static VehicleResponse of(Vehicle v, boolean sellable, List<DocumentType> missing) {
            return new VehicleResponse(v.id, v.organizationId, v.homeBranchId, v.plateNumber, v.brand, v.model, v.year,
                    v.category, v.transmission, v.fuelType, v.seatCount, v.active, sellable, missing,
                    v.createdAt, v.updatedAt);
        }
    }

    public record RateResponse(UUID id, UUID organizationId, UUID branchId, CarCategory category,
                               Transmission transmission, String currency, BigDecimal dailyRate,
                               BigDecimal depositAmount, Integer kmLimitPerDay, BigDecimal extraKmFee,
                               int minDriverAge, int minLicenseYears) {
        public static RateResponse of(CategoryRate r) {
            return new RateResponse(r.id, r.organizationId, r.branchId, r.category, r.transmission, r.currency,
                    r.dailyRate, r.depositAmount, r.kmLimitPerDay, r.extraKmFee, r.minDriverAge, r.minLicenseYears);
        }
    }

    public record RouteResponse(UUID id, UUID organizationId, UUID fromBranchId, UUID toBranchId, BigDecimal fee,
                                String currency) {
        public static RouteResponse of(OneWayRoute r) {
            return new RouteResponse(r.id, r.organizationId, r.fromBranchId, r.toBranchId, r.fee, r.currency);
        }
    }

    public record DocumentResponse(UUID id, UUID organizationId, UUID vehicleId, DocumentType type,
                                   String documentNumber, LocalDate validUntil, DocumentStatus status,
                                   String rejectReason, UUID reviewedBy, Instant reviewedAt, String fileName,
                                   String contentType, long sizeBytes, UUID uploadedBy,
                                   Instant createdAt, Instant updatedAt) {
        public static DocumentResponse of(CarDocument d) {
            return new DocumentResponse(d.id, d.organizationId, d.vehicleId, d.type, d.documentNumber, d.validUntil,
                    d.status, d.rejectReason, d.reviewedBy, d.reviewedAt, d.fileName, d.contentType, d.sizeBytes,
                    d.uploadedBy, d.createdAt, d.updatedAt);
        }
    }

    public record RentalResponse(UUID id, UUID organizationId, UUID customerId, UUID vehicleId, UUID pickupBranchId,
                                 UUID returnBranchId, CarCategory category, Transmission transmission,
                                 LocalDateTime pickupAt, LocalDateTime returnAt, int chargeableDays,
                                 BigDecimal dailyRate, BigDecimal oneWayFee, BigDecimal totalAmount, String currency,
                                 BigDecimal depositAmount, Integer kmLimitPerDay, int driverAge, RentalStatus status,
                                 Instant holdExpiresAt, Instant createdAt, Instant updatedAt) {
        public static RentalResponse of(Rental r) {
            return new RentalResponse(r.id, r.organizationId, r.customerId, r.vehicleId, r.pickupBranchId,
                    r.returnBranchId, r.category, r.transmission, local(r.pickupAt), local(r.returnAt),
                    r.chargeableDays, r.dailyRate, r.oneWayFee, r.totalAmount, r.currency, r.depositAmount,
                    r.kmLimitPerDay, r.driverAge, r.status, r.holdExpiresAt, r.createdAt, r.updatedAt);
        }
    }

    public record PartnerStatus(boolean sellable, List<DocumentType> missingOrgDocuments, boolean individualGateOpen) { }

    public record AdminVehicleResponse(UUID id, UUID organizationId, UUID homeBranchId, String plateNumber,
                                       String brand, String model, int year, CarCategory category,
                                       Transmission transmission, FuelType fuelType, int seatCount, boolean active,
                                       String organizationName, String homeBranchName, boolean sellable,
                                       List<DocumentType> missingDocuments) {
        public static AdminVehicleResponse of(Vehicle v, String orgName, String branchName,
                                               boolean sellable, List<DocumentType> missing) {
            return new AdminVehicleResponse(v.id, v.organizationId, v.homeBranchId, v.plateNumber, v.brand, v.model,
                    v.year, v.category, v.transmission, v.fuelType, v.seatCount, v.active,
                    orgName, branchName, sellable, missing);
        }
    }

    public record AdminDocumentResponse(UUID id, UUID organizationId, UUID vehicleId, DocumentType type,
                                       String documentNumber, LocalDate validUntil, DocumentStatus status,
                                       String rejectReason, UUID reviewedBy, Instant reviewedAt,
                                       String fileName, String contentType, long sizeBytes, UUID uploadedBy,
                                       Instant createdAt, Instant updatedAt,
                                       String organizationName, String legalType, String vehicleLabel) {
        public static AdminDocumentResponse of(DocumentResponse dr, String orgName, String legalType, String vehicleLabel) {
            return new AdminDocumentResponse(dr.id(), dr.organizationId(), dr.vehicleId(), dr.type(),
                    dr.documentNumber(), dr.validUntil(), dr.status(), dr.rejectReason(), dr.reviewedBy(),
                    dr.reviewedAt(), dr.fileName(), dr.contentType(), dr.sizeBytes(), dr.uploadedBy(),
                    dr.createdAt(), dr.updatedAt(), orgName, legalType, vehicleLabel);
        }
    }

    public record OfferResponse(UUID pickupBranchId, UUID returnBranchId, UUID organizationId, CarCategory category,
                                Transmission transmission, String exampleModel, int seatCount, FuelType fuelType,
                                int chargeableDays, BigDecimal dailyRate, BigDecimal oneWayFee, BigDecimal totalAmount,
                                String currency, BigDecimal depositAmount, Integer kmLimitPerDay,
                                BigDecimal extraKmFee, int minDriverAge, int minLicenseYears,
                                String authorizationDocumentNumber) { }

    public record HoldDto(UUID holdId, UUID organizationId, UUID pickupBranchId, UUID returnBranchId,
                          CarCategory category, Transmission transmission, String exampleModel,
                          LocalDateTime pickupAt, LocalDateTime returnAt, int chargeableDays, String currency,
                          BigDecimal totalAmount, BigDecimal depositAmount, Integer kmLimitPerDay,
                          RentalStatus status, Instant expiresAt) { }

    public static LocalDateTime local(Instant i) {
        return LocalDateTime.ofInstant(i, com.waylo.carrentalservice.config.CarSettings.ZONE);
    }
}
