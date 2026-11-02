package com.waylo.packagebookingservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published after a voucher is redeemed. amount = customer-paid gross of the item (no commercial rule applied here). */
public class OrderItemRedeemedEvent extends BaseEvent {
    private UUID orderId;
    private UUID orderItemId;
    private UUID customerId;
    private String currency;
    private BigDecimal amount;

    public OrderItemRedeemedEvent() { super("ORDER_ITEM_REDEEMED", "package-booking-service"); }

    public OrderItemRedeemedEvent(UUID orderId, UUID orderItemId, UUID customerId, String currency, BigDecimal amount) {
        this();
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.customerId = customerId;
        this.currency = currency;
        this.amount = amount;
    }

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
