package com.waylo.packagebookingservice.event;

import java.util.UUID;

public class OrderCancelledEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;

    public OrderCancelledEvent() { super("ORDER_CANCELLED", "package-booking-service"); }

    public OrderCancelledEvent(UUID orderId, UUID customerId) {
        this();
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
}
