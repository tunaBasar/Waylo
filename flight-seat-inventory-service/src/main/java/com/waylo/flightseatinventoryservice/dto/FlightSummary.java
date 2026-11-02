package com.waylo.flightseatinventoryservice.dto;

import java.time.Instant;

/** Subset of flight-service FlightResponse used to build offers and holds. */
public record FlightSummary(String flightNumber, String departureAirportCode, String arrivalAirportCode,
                            Instant departureTime, Instant arrivalTime, String airline, String status) {}
