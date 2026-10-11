package com.waylo.notificationservice.event;

import java.util.UUID;

public class PaymentRefundedEvent extends BaseEvent {
    private UUID paymentId;
    private UUID bookingId;
    private UUID customerId;

    public PaymentRefundedEvent() { super("PAYMENT_REFUNDED", "payment-service"); }

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
}
