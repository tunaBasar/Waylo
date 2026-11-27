package com.waylo.flightservice.controller;

import com.waylo.flightservice.dto.ApiResponse;
import com.waylo.flightservice.dto.FlightScheduleDtos.GenerationSummary;
import com.waylo.flightservice.dto.FlightScheduleDtos.ScheduleRequest;
import com.waylo.flightservice.dto.FlightScheduleDtos.ScheduleResponse;
import com.waylo.flightservice.dto.FlightScheduleDtos.ScheduleResult;
import com.waylo.flightservice.service.FlightScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Literal path "schedules" always wins over GET /api/flights/{id} in Spring MVC pattern ordering, and the
 * UUID variables here are typed, so "apply" never parses as an id. Writes are admin-only via AdminWriteFilter.
 */
@RestController
@RequestMapping("/api/flights/schedules")
public class FlightScheduleController {

    private final FlightScheduleService service;

    public FlightScheduleController(FlightScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(service.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResult>> create(@RequestBody ScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.create(request), "Schedule created"));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<GenerationSummary>> apply() {
        return ResponseEntity.ok(ApiResponse.of(service.applyAll()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleResult>> update(@PathVariable UUID id,
                                                              @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.of(service.update(id, request), "Schedule updated"));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<ScheduleResult>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(service.activate(id)));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<ScheduleResult>> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(service.deactivate(id)));
    }
}
