package com.waylo.crmservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/** Superset of package-booking order-events (ORDER_CREATED, ORDER_CANCELLED, ORDER_ITEM_REDEEMED). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent extends BaseEvent {
    private UUID orderId;
    private UUID orderItemId;
    private UUID customerId;
    private String currency;
    private BigDecimal amount;

    public OrderEvent() { super(); }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID v) { this.orderId = v; }
    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID v) { this.orderItemId = v; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID v) { this.customerId = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { this.amount = v; }
}
