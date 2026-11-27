package com.waylo.flightservice.event;

import java.math.BigDecimal;

/** One cabin of a scheduled flight, carried by FlightCreatedEvent so seat inventory can create the seats. */
public record FlightCabinSpec(String seatType, BigDecimal price, int fromRow, int toRow, String letters) {
}
