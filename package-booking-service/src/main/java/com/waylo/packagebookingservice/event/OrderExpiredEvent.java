package com.waylo.packagebookingservice.event;

import java.math.BigDecimal;
import java.util.UUID;

/** Published once when a PENDING order is expired because its holds ran out unpaid. Moves no money. */
public class OrderExpiredEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;
    private String currency;
    private BigDecimal totalAmount;

    public OrderExpiredEvent() { super("ORDER_EXPIRED", "package-booking-service"); }

    public OrderExpiredEvent(UUID orderId, UUID customerId, String currency, BigDecimal totalAmount) {
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
