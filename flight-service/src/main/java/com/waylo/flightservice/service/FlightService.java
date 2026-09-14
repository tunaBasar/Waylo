package com.waylo.flightservice.service;

import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FlightService {

    FlightResponse create(FlightRequest request);

    FlightResponse getById(UUID id);

    FlightResponse getByFlightNumber(String flightNumber);

    List<FlightResponse> getAll();

    /**
     * Kalkış ve varış havalimanı + tarih aralığı ile uçuş arama.
     */
    List<FlightResponse> search(String departureCode, String arrivalCode,
                                Instant from, Instant to);

    FlightResponse update(UUID id, FlightRequest request);

    void delete(UUID id);

    FlightResponse cancel(UUID id);
}
