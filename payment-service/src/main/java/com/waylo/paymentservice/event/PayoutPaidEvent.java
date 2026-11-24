package com.waylo.paymentservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Partner payout marked PAID (P4). Carries the IBAN last4 only, never the full IBAN. */
public class PayoutPaidEvent extends BaseEvent {
    private UUID payoutId;
    private UUID organizationId;
    private UUID requestedByUserId;
    private String recipientEmail;
    private BigDecimal amount;
    private String currency;
    private String ibanLast4;
    private String reference;
    private Instant paidAt;

    public PayoutPaidEvent() { super("PAYOUT_PAID", "payment-service"); }

    public PayoutPaidEvent(UUID payoutId, UUID organizationId, UUID requestedByUserId, String recipientEmail, BigDecimal amount,
                           String currency, String ibanLast4, String reference, Instant paidAt) {
        this();
        this.payoutId = payoutId;
        this.organizationId = organizationId;
        this.requestedByUserId = requestedByUserId;
        this.recipientEmail = recipientEmail;
        this.amount = amount;
        this.currency = currency;
        this.ibanLast4 = ibanLast4;
        this.reference = reference;
        this.paidAt = paidAt;
    }

    public UUID getPayoutId() { return payoutId; }
    public void setPayoutId(UUID payoutId) { this.payoutId = payoutId; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(UUID requestedByUserId) { this.requestedByUserId = requestedByUserId; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getIbanLast4() { return ibanLast4; }
    public void setIbanLast4(String ibanLast4) { this.ibanLast4 = ibanLast4; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}
