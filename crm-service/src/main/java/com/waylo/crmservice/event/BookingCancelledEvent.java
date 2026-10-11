package com.waylo.crmservice.event;

import java.util.UUID;

public class BookingCancelledEvent extends BaseEvent {
    private UUID bookingId;
    private UUID customerId;

    public BookingCancelledEvent() { super("BOOKING_CANCELLED", "package-booking-service"); }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
}
