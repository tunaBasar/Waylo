package com.waylo.packagebookingservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderCancelledEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;
    /** true when the order had been paid (CONFIRMED) at cancel time. A PENDING order cancel moves no money. */
    private boolean paid;
    private String currency;
    /** Sum of item refund amounts decided at cancel (CANCELLATION-CONTRACT.md), zero when nothing was refunded. */
    private BigDecimal refundAmount;

    public OrderCancelledEvent() { super("ORDER_CANCELLED", "package-booking-service"); }

    public OrderCancelledEvent(UUID orderId, UUID customerId) {
        this();
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public OrderCancelledEvent(UUID orderId, UUID customerId, boolean paid, String currency, BigDecimal refundAmount) {
        this(orderId, customerId);
        this.paid = paid;
        this.currency = currency;
        this.refundAmount = refundAmount;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
}
