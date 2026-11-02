package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.SupplierAccount;
import com.waylo.paymentservice.domain.SupplierLedgerEntry;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SupplierDtos {
    private SupplierDtos() {}

    public record TopUpRequest(
            @NotNull @DecimalMin(value = "0.01") @DecimalMax("10000000.00") BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotBlank @Size(max = 200) String reference,
            @NotBlank @Size(max = 150) String idempotencyKey) {}

    /** payout: ticketing debits the net fare. credit: airline refund comes back. */
    public record MovementRequest(
            @NotBlank @Size(max = 30) String supplier,
            @NotNull UUID orderId,
            UUID orderItemId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotBlank @Size(max = 150) String idempotencyKey) {}

    public record AccountResponse(String supplier, String currency, BigDecimal balance, Instant updatedAt) {
        public static AccountResponse from(SupplierAccount a) {
            return new AccountResponse(a.getSupplier(), a.getCurrency(), a.getBalance(), a.getUpdatedAt());
        }
    }

    public record LedgerRow(UUID id, String entryType, BigDecimal amount, BigDecimal balanceAfter, String currency,
                            UUID orderId, UUID orderItemId, String reference, UUID createdBy, Instant createdAt) {
        public static LedgerRow from(SupplierLedgerEntry e) {
            return new LedgerRow(e.getId(), e.getEntryType().name(), e.getAmount(), e.getBalanceAfter(), e.getCurrency(),
                    e.getOrderId(), e.getOrderItemId(), e.getReference(), e.getCreatedBy(), e.getCreatedAt());
        }
    }

    public record LedgerPage(java.util.List<LedgerRow> items, long total, int page, int size) {}
}
