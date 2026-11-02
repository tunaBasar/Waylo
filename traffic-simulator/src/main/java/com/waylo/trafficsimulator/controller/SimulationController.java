package com.waylo.trafficsimulator.controller;

import com.waylo.trafficsimulator.dto.ApiResponse;
import com.waylo.trafficsimulator.dto.SimulationRequest;
import com.waylo.trafficsimulator.dto.SimulationResponse;
import com.waylo.trafficsimulator.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {
    private final SimulationService simulationService;
    public SimulationController(SimulationService simulationService) { this.simulationService = simulationService; }

    private static boolean isAdmin(String role) {
        return "ADMIN".equals(role);
    }

    private static <T> ResponseEntity<ApiResponse<T>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.of(null, "Admin access required"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SimulationResponse>> start(
            @Valid @RequestBody SimulationRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!isAdmin(role)) {
            return forbidden();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(simulationService.start(request), "Simulation started"));
    }

    @GetMapping("/{simulationId}")
    public ResponseEntity<ApiResponse<SimulationResponse>> getStatus(@PathVariable String simulationId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!isAdmin(role)) return forbidden();
        return ResponseEntity.ok(ApiResponse.of(simulationService.getStatus(simulationId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SimulationResponse>>> listAll(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!isAdmin(role)) return forbidden();
        return ResponseEntity.ok(ApiResponse.of(simulationService.listAll()));
    }

    @PostMapping("/{simulationId}/cancel")
    public ResponseEntity<ApiResponse<SimulationResponse>> cancel(@PathVariable String simulationId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!isAdmin(role)) return forbidden();
        return ResponseEntity.ok(ApiResponse.of(simulationService.cancel(simulationId), "Simulation cancelled"));
    }
}
