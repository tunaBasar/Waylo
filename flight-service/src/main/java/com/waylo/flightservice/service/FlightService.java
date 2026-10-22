package com.waylo.flightservice.service;

import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FlightService {

    FlightResponse create(FlightRequest request);

    FlightResponse getById(UUID id);

    FlightResponse getByFlightNumber(String flightNumber);

    List<FlightResponse> getAll();

    /**
     * Search flights by departure/arrival airport codes with date or time range.
     */
    List<FlightResponse> search(String from, String to,
                                LocalDate date,
                                Instant departureFrom,
                                Instant departureTo,
                                boolean includeCancelled);

    FlightResponse update(UUID id, FlightRequest request);

    void delete(UUID id);

    FlightResponse cancel(UUID id);
}
