package com.waylo.flightseatinventoryservice.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Local copy of flight-service's FlightCreatedEvent. Only the fields seat inventory needs; unknown JSON
 * fields are ignored by the Spring JsonDeserializer defaults. {@code cabins} is null for flights created by hand.
 */
public class FlightCreatedEvent extends BaseEvent {
    private UUID flightId;
    private String flightNumber;
    private List<Cabin> cabins;

    public FlightCreatedEvent() { super("FLIGHT_CREATED", "flight-service"); }

    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
    public List<Cabin> getCabins() { return cabins; }
    public void setCabins(List<Cabin> cabins) { this.cabins = cabins; }

    public record Cabin(String seatType, BigDecimal price, int fromRow, int toRow, String letters) {}
}
