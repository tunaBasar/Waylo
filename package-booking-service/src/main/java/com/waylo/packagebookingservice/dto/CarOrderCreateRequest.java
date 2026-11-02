package com.waylo.packagebookingservice.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
public record CarOrderCreateRequest(@NotNull UUID pickupBranchId, @NotNull UUID returnBranchId, @NotBlank String category, @NotBlank String transmission, @NotNull LocalDateTime pickupAt, @NotNull LocalDateTime returnAt, @Min(18) @Max(100) int driverAge, @Size(max = 50) String promoCode) {}
