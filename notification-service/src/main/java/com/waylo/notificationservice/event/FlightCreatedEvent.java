package com.waylo.notificationservice.event;

import java.util.UUID;

/**
 * Local copy of flight-service's FlightCreatedEvent. notification-service does not act on
 * flight creation, but it subscribes to the shared flight-events topic, so the type must be
 * resolvable or every new flight would be treated as an unprocessable record.
 */
public class FlightCreatedEvent extends BaseEvent {
    private UUID flightId;
    private String flightNumber;

    public FlightCreatedEvent() { super("FLIGHT_CREATED", "flight-service"); }

    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
}
