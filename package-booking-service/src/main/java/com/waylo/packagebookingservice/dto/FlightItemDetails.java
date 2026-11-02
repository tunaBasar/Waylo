package com.waylo.packagebookingservice.dto;

import java.time.Instant;

/** Flight facts of an order item, present only for FLIGHT items. pnr and ticketNumber appear once ticketed. */
public record FlightItemDetails(String flightNumber, Instant departureAt, String seatNumber, String passengerFirstName,
                                String passengerLastName, String pnr, String ticketNumber, String supplier) {}
