package com.waylo.packagebookingservice.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Published once when an order becomes CONFIRMED (paid). Carries no voucher code or QR payload (bearer credentials). */
public class OrderPaidEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;
    private String currency;
    private BigDecimal totalAmount;
    private List<Item> items;

    public OrderPaidEvent() { super("ORDER_PAID", "package-booking-service"); }

    public OrderPaidEvent(UUID orderId, UUID customerId, String currency, BigDecimal totalAmount, List<Item> items) {
        this();
        this.orderId = orderId;
        this.customerId = customerId;
        this.currency = currency;
        this.totalAmount = totalAmount;
        this.items = items;
    }

    /** startsAt/endsAt: ISO date or date-time text of the service (hotel stay, car pickup/return, flight departure), may be null. */
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
}
