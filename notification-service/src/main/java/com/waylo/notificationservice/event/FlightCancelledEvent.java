package com.waylo.notificationservice.event;

import java.util.UUID;

public class FlightCancelledEvent extends BaseEvent {
    private UUID flightId;
    private String flightNumber;
    private java.time.Instant departureTime;

    public FlightCancelledEvent() { super("FLIGHT_CANCELLED", "flight-service"); }

    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public java.time.Instant getDepartureTime() { return departureTime; }
    public void setDepartureTime(java.time.Instant departureTime) { this.departureTime = departureTime; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
}
