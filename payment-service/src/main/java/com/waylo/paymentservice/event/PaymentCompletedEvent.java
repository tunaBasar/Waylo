package com.waylo.paymentservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentCompletedEvent extends BaseEvent {
    private UUID paymentId;
    private UUID bookingId;
    private UUID customerId;
    private BigDecimal amount;
    private String transactionRef;

    public PaymentCompletedEvent() { super("PAYMENT_COMPLETED", "payment-service"); }

    public PaymentCompletedEvent(UUID paymentId, UUID bookingId, UUID customerId, BigDecimal amount, String transactionRef) {
        this();
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.amount = amount;
        this.transactionRef = transactionRef;
    }

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
}
