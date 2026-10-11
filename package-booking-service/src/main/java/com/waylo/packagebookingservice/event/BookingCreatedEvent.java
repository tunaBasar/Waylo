package com.waylo.packagebookingservice.event;

import java.math.BigDecimal;
import java.util.UUID;

public class BookingCreatedEvent extends BaseEvent {
    private UUID bookingId;
    private UUID customerId;
    private UUID flightId;
    private UUID hotelReservationId;
    private UUID vehicleReservationId;
    private BigDecimal totalPrice;

    public BookingCreatedEvent() { super("BOOKING_CREATED", "package-booking-service"); }

    public BookingCreatedEvent(UUID bookingId, UUID customerId, UUID flightId,
                               UUID hotelReservationId, UUID vehicleReservationId, BigDecimal totalPrice) {
        this();
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.flightId = flightId;
        this.hotelReservationId = hotelReservationId;
        this.vehicleReservationId = vehicleReservationId;
        this.totalPrice = totalPrice;
    }

    public UUID getBookingId() { return bookingId; }
    public void setBookingId(UUID bookingId) { this.bookingId = bookingId; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public UUID getHotelReservationId() { return hotelReservationId; }
    public void setHotelReservationId(UUID hotelReservationId) { this.hotelReservationId = hotelReservationId; }
    public UUID getVehicleReservationId() { return vehicleReservationId; }
    public void setVehicleReservationId(UUID vehicleReservationId) { this.vehicleReservationId = vehicleReservationId; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
}
