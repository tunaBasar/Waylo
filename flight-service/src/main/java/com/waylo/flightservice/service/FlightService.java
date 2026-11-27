package com.waylo.flightservice.service;

import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightResponse;
import com.waylo.flightservice.event.FlightCabinSpec;

import com.waylo.flightservice.domain.FlightStatus;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FlightService {

    FlightResponse create(FlightRequest request);

    /**
     * Same path as {@link #create(FlightRequest)} (validation, duplicate check, FlightCreatedEvent), for a
     * departure generated from a schedule: sets schedule_id and carries the cabins in the event.
     */
    FlightResponse createScheduled(FlightRequest request, UUID scheduleId, List<FlightCabinSpec> cabins);

    FlightResponse getById(UUID id);

    FlightResponse getByFlightNumber(String flightNumber);

    /**
     * Bounded, filtered list ordered by departure time ascending. {@code from}/{@code to} are inclusive
     * Europe/Istanbul days, {@code status} null means all, {@code upcoming} keeps departure >= now,
     * {@code flightNumber} keeps numbers containing it (case-insensitive, characters other than A-Z/0-9 ignored,
     * blank means no filter). {@code size} is clamped to 1..200 (default 50 applied by the controller), negative
     * {@code page} to 0.
     */
    Page<FlightResponse> list(LocalDate from, LocalDate to, FlightStatus status, boolean upcoming, String flightNumber,
                              int page, int size);

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
