package com.waylo.flightservice.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class FlightCreatedEvent extends BaseEvent {
    private UUID flightId;
    private String flightNumber;
    private String departureAirportCode;
    private String arrivalAirportCode;
    private Instant departureTime;
    private Instant arrivalTime;
    private String airline;
    /** Null for flights created by hand. */
    private List<FlightCabinSpec> cabins;

    public FlightCreatedEvent() { super("FLIGHT_CREATED", "flight-service"); }

    public FlightCreatedEvent(UUID flightId, String flightNumber, String departureAirportCode,
                              String arrivalAirportCode, Instant departureTime, Instant arrivalTime, String airline) {
        this();
        this.flightId = flightId;
        this.flightNumber = flightNumber;
        this.departureAirportCode = departureAirportCode;
        this.arrivalAirportCode = arrivalAirportCode;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.airline = airline;
    }

    public FlightCreatedEvent(UUID flightId, String flightNumber, String departureAirportCode,
                              String arrivalAirportCode, Instant departureTime, Instant arrivalTime, String airline,
                              List<FlightCabinSpec> cabins) {
        this(flightId, flightNumber, departureAirportCode, arrivalAirportCode, departureTime, arrivalTime, airline);
        this.cabins = cabins;
    }

    public UUID getFlightId() { return flightId; }
    public void setFlightId(UUID flightId) { this.flightId = flightId; }
    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
    public String getDepartureAirportCode() { return departureAirportCode; }
    public void setDepartureAirportCode(String departureAirportCode) { this.departureAirportCode = departureAirportCode; }
    public String getArrivalAirportCode() { return arrivalAirportCode; }
    public void setArrivalAirportCode(String arrivalAirportCode) { this.arrivalAirportCode = arrivalAirportCode; }
    public Instant getDepartureTime() { return departureTime; }
    public void setDepartureTime(Instant departureTime) { this.departureTime = departureTime; }
    public Instant getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Instant arrivalTime) { this.arrivalTime = arrivalTime; }
    public String getAirline() { return airline; }
    public void setAirline(String airline) { this.airline = airline; }
    public List<FlightCabinSpec> getCabins() { return cabins; }
    public void setCabins(List<FlightCabinSpec> cabins) { this.cabins = cabins; }
}
