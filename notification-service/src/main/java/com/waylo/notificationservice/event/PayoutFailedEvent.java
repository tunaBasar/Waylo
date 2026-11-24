package com.waylo.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/** payment-service PAYOUT_FAILED. The reason is the admin's free text. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayoutFailedEvent extends BaseEvent {
    private UUID payoutId;
    private UUID organizationId;
    private UUID requestedByUserId;
    private String recipientEmail;
    private BigDecimal amount;
    private String currency;
    private String ibanLast4;
    private String reason;

    public PayoutFailedEvent() { super("PAYOUT_FAILED", "payment-service"); }

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
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
