package com.waylo.flightservice.controller;

import com.waylo.flightservice.dto.ApiResponse;
import com.waylo.flightservice.dto.FlightRequest;
import com.waylo.flightservice.dto.FlightResponse;
import com.waylo.flightservice.service.FlightService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightService flightService;

    public FlightController(FlightService flightService) {
        this.flightService = flightService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FlightResponse>> create(@Valid @RequestBody FlightRequest request) {
        FlightResponse response = flightService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Flight created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(flightService.getById(id)));
    }

    @GetMapping("/number/{flightNumber}")
    public ResponseEntity<ApiResponse<FlightResponse>> getByFlightNumber(@PathVariable String flightNumber) {
        return ResponseEntity.ok(ApiResponse.of(flightService.getByFlightNumber(flightNumber)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FlightResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(flightService.getAll()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FlightResponse>>> search(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Instant departureFrom,
            @RequestParam(required = false) Instant departureTo,
            @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return ResponseEntity.ok(ApiResponse.of(flightService.search(from, to, date, departureFrom, departureTo, includeCancelled)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightResponse>> update(@PathVariable UUID id,
                                                               @Valid @RequestBody FlightRequest request) {
        return ResponseEntity.ok(ApiResponse.of(flightService.update(id, request), "Flight updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        flightService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<FlightResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(flightService.cancel(id), "Flight cancelled"));
    }
}
