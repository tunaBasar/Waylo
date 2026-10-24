package com.waylo.flightseatinventoryservice.dto;

import java.time.Instant;

/** Subset of flight-service FlightResponse needed to validate bookability. */
public record FlightInfo(String status, Instant departureTime) {}
