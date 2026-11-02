package com.waylo.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Superset of package-booking order-events (ORDER_PAID, ORDER_CANCELLED, ORDER_CREATED, ORDER_ITEM_REDEEMED). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;
    private String currency;
    private BigDecimal totalAmount;
    private List<Item> items;
    private boolean paid;
    private BigDecimal refundAmount;

    public OrderEvent() { super(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String vertical, String description, String startsAt, String endsAt, BigDecimal amount) {}

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID v) { this.orderId = v; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID v) { this.customerId = v; }
    public String getCurrency() { return currency; }
    public void setCurrency(String v) { this.currency = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> v) { this.items = v; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean v) { this.paid = v; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal v) { this.refundAmount = v; }
}
