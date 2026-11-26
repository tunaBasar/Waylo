package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.BankAccountStatus;
import com.waylo.paymentservice.domain.PayoutActorType;
import com.waylo.paymentservice.domain.PayoutStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Partner payout DTOs (PAYOUT-CONTRACT.md sec. 6). Separate records on purpose: no response record has a field for the IBAN
 * ciphertext, hash or the plain IBAN, except {@link BankDetailsResponse} (admin reveal) which is only built by the audited reveal path.
 */
public final class PayoutDtos {

    private PayoutDtos() {}

    // ---- requests ----

    public record RequestPayoutRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull @DecimalMin("0.01") @DecimalMax("10000000.00") @Digits(integer = 8, fraction = 2) BigDecimal amount) {}

    /** The IBAN is request-only: toString never prints it so accidental logging cannot leak it. */
    public record BankAccountRequest(
            @NotBlank @Size(min = 2, max = 140) String accountHolderName,
            @NotBlank @Size(max = 64) String iban) {
        @Override public String toString() { return "BankAccountRequest[accountHolderName=" + accountHolderName + ", iban=***]"; }
    }

    /** Optional body of approve and processing. */
    public record TransitionRequest(Long expectedVersion) {}

    public record PaidRequest(@NotBlank @Size(min = 3, max = 100) String reference, Instant paidAt, Long expectedVersion) {}

    public record ReasonRequest(@NotBlank @Size(min = 5, max = 500) String reason, Long expectedVersion) {}

    public record RejectBankAccountRequest(@NotBlank @Size(min = 5, max = 500) String reason) {}

    public record AdjustmentRequest(
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @NotNull @DecimalMin("0.01") @DecimalMax("10000000.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @NotNull Direction direction,
            @NotBlank @Size(min = 5, max = 500) String reason,
            UUID orderItemId,
            @NotBlank @Size(max = 150) String idempotencyKey) {
        public enum Direction { DEBIT, CREDIT }
    }

    // ---- responses ----

    public record PayoutResponse(UUID id, UUID organizationId, String currency, BigDecimal amount, PayoutStatus status,
                                 String maskedIban, String accountHolderName, Instant requestedAt, Instant approvedAt,
                                 Instant processingAt, Instant paidAt, String paymentReference, String failureReason,
                                 String cancelReason, PayoutActorType cancelledByType, long version) {}

    public record PayoutEventView(PayoutStatus fromStatus, PayoutStatus toStatus, PayoutActorType actorType, String reason,
                                  String reference, Instant occurredAt) {}

    public record AdminPayoutDetail(PayoutResponse payout, List<PayoutEventView> events, BalanceItem organizationBalance) {}

    public record PageResponse<T>(List<T> items, int page, int size, long total) {}

    public record BankSummary(BankAccountStatus status, String maskedIban) {}

    public record BalanceItem(String currency, BigDecimal payable, BigDecimal releasedNotYetPayable, BigDecimal held,
                              BigDecimal reserved, BigDecimal paidTotal, BigDecimal owed, BigDecimal minimumPayout,
                              boolean canRequest, String blockedReason) {}

    public record BalanceResponse(UUID organizationId, int holdbackDays, List<BalanceItem> balances, BankSummary bankAccount) {}

    public record AdminBalanceItem(String currency, BigDecimal payable, BigDecimal releasedNotYetPayable, BigDecimal held,
                                   BigDecimal reserved, BigDecimal paidTotal, BigDecimal owed, BigDecimal minimumPayout,
                                   boolean canRequest, String blockedReason, BigDecimal adjustmentsNet, boolean reconciles) {}

    public record AdminBalanceResponse(UUID organizationId, int holdbackDays, List<AdminBalanceItem> balances, BankSummary bankAccount) {}

    public record BankAccountResponse(UUID id, String accountHolderName, String ibanLast4, String maskedIban,
                                      BankAccountStatus status, Instant verifiedAt, String rejectReason, Instant updatedAt, long version) {}

    public record AdminBankAccountResponse(UUID id, UUID organizationId, String accountHolderName, String ibanLast4, String maskedIban,
                                           BankAccountStatus status, Instant verifiedAt, String rejectReason, Instant updatedAt, long version) {}

    /** The one deliberate full-IBAN response (audited admin reveal). toString never prints it. */
    public record BankDetailsResponse(UUID payoutId, String accountHolderName, String iban) {
        @Override public String toString() { return "BankDetailsResponse[payoutId=" + payoutId + ", iban=***]"; }
    }

    /** Admin reveal of a bank account IBAN (verification against documents). toString never prints it. */
    public record BankAccountIbanResponse(UUID bankAccountId, String accountHolderName, String iban) {
        @Override public String toString() { return "BankAccountIbanResponse[bankAccountId=" + bankAccountId + ", iban=***]"; }
    }

    public record AdjustmentResponse(UUID id, UUID organizationId, String currency, BigDecimal amount,
                                     AdjustmentRequest.Direction direction, String reason, Instant createdAt) {}
}
