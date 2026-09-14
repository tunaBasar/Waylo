package com.waylo.flightservice.service;

import com.waylo.flightservice.dto.AirportRequest;
import com.waylo.flightservice.dto.AirportResponse;

import java.util.List;
import java.util.UUID;

public interface AirportService {

    AirportResponse create(AirportRequest request);

    AirportResponse getById(UUID id);

    AirportResponse getByIataCode(String iataCode);

    List<AirportResponse> getAll();

    AirportResponse update(UUID id, AirportRequest request);

    void delete(UUID id);
}
