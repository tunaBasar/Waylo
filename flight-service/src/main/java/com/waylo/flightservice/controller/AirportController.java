package com.waylo.flightservice.controller;

import com.waylo.flightservice.dto.AirportRequest;
import com.waylo.flightservice.dto.AirportResponse;
import com.waylo.flightservice.dto.ApiResponse;
import com.waylo.flightservice.service.AirportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/airports")
public class AirportController {

    private final AirportService airportService;

    public AirportController(AirportService airportService) {
        this.airportService = airportService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AirportResponse>> create(@Valid @RequestBody AirportRequest request) {
        AirportResponse response = airportService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Airport created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AirportResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(airportService.getById(id)));
    }

    @GetMapping("/iata/{iataCode}")
    public ResponseEntity<ApiResponse<AirportResponse>> getByIataCode(@PathVariable String iataCode) {
        return ResponseEntity.ok(ApiResponse.of(airportService.getByIataCode(iataCode)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AirportResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(airportService.getAll()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AirportResponse>> update(@PathVariable UUID id,
                                                                @Valid @RequestBody AirportRequest request) {
        return ResponseEntity.ok(ApiResponse.of(airportService.update(id, request), "Airport updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        airportService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
