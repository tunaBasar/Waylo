package com.waylo.flightservice.event;

import java.time.Instant;
import java.util.UUID;

public class FlightCreatedEvent extends BaseEvent {
    private UUID flightId;
    private String flightNumber;
    private String departureAirportCode;
    private String arrivalAirportCode;
    private Instant departureTime;
    private Instant arrivalTime;
    private String airline;

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
}
