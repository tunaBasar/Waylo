package com.waylo.flightseatinventoryservice.event;

import java.util.UUID;

public class FlightCancelledEvent extends BaseEvent {
    private UUID flightId;
    private String flightNumber;

    public FlightCancelledEvent() { super("FLIGHT_CANCELLED", "flight-service"); }

    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
}
