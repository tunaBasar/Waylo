package com.waylo.crmservice.event;

import java.util.UUID;

public class BookingCreatedEvent extends BaseEvent {
    private UUID bookingId;
    private UUID customerId;

    public BookingCreatedEvent() { super("BOOKING_CREATED", "package-booking-service"); }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
}
