package com.waylo.packagebookingservice.event;

import java.util.UUID;

public class BookingCancelledEvent extends BaseEvent {
    private UUID bookingId;
    private UUID customerId;

    public BookingCancelledEvent() { super("BOOKING_CANCELLED", "package-booking-service"); }

    public BookingCancelledEvent(UUID bookingId, UUID customerId) {
        this();
        this.bookingId = bookingId;
        this.customerId = customerId;
    }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
}
