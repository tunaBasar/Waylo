package com.waylo.packagebookingservice.dto;

import com.waylo.packagebookingservice.domain.OrderItemVertical;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * v1 destekler tek item/order (PRODUCT-ARCHITECTURE.md SS5), currency alani yoktur:
 * order.currency tum item'lar icin otoritedir, OrderServiceImpl item.currency != order.currency
 * durumunda 422 doner.
 */
public record OrderItemCreateRequest(
        @NotNull(message = "Vertical is required")
        OrderItemVertical vertical,

        UUID supplierOrganizationId,

        @NotNull(message = "Product reference is required")
        UUID productRef,

        @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @NotNull(message = "Unit amount is required")
        @DecimalMin(value = "0.01", message = "Unit amount must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "Unit amount must have at most 10 integer and 2 fraction digits")
        BigDecimal unitAmount,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 20, message = "Quantity must be at most 20")
        Integer quantity,

        @NotNull(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code")
        String currency
) {}
