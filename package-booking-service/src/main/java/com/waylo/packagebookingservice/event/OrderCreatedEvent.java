package com.waylo.packagebookingservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public class OrderCreatedEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;
    private String currency;
    private BigDecimal totalAmount;

    public OrderCreatedEvent() { super("ORDER_CREATED", "package-booking-service"); }

    public OrderCreatedEvent(UUID orderId, UUID customerId, String currency, BigDecimal totalAmount) {
        this();
        this.orderId = orderId;
        this.customerId = customerId;
        this.currency = currency;
        this.totalAmount = totalAmount;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}
