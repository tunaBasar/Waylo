package com.waylo.notificationservice.event;

import java.util.UUID;

public class BookingCreatedEvent extends BaseEvent {
    private UUID bookingId;
    private UUID customerId;
    private UUID flightId;

    public BookingCreatedEvent() { super("BOOKING_CREATED", "package-booking-service"); }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
}
