package com.waylo.packagebookingsagaorchestrator.controller;

import com.waylo.packagebookingsagaorchestrator.domain.SagaStatus;
import com.waylo.packagebookingsagaorchestrator.dto.ApiResponse;
import com.waylo.packagebookingsagaorchestrator.dto.SagaInstanceResponse;
import com.waylo.packagebookingsagaorchestrator.dto.SagaStartRequest;
import com.waylo.packagebookingsagaorchestrator.service.SagaOrchestratorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sagas")
public class SagaController {
    private final SagaOrchestratorService sagaService;
    public SagaController(SagaOrchestratorService sagaService) { this.sagaService = sagaService; }

    @PostMapping
    public ResponseEntity<ApiResponse<SagaInstanceResponse>> start(@Valid @RequestBody SagaStartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(sagaService.startSaga(request), "Saga started"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SagaInstanceResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(sagaService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SagaInstanceResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(sagaService.getAll()));
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<SagaInstanceResponse>> getByBookingId(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.of(sagaService.getByBookingId(bookingId)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<SagaInstanceResponse>>> getByStatus(@PathVariable SagaStatus status) {
        return ResponseEntity.ok(ApiResponse.of(sagaService.getByStatus(status)));
    }

    @PostMapping("/{id}/compensate")
    public ResponseEntity<ApiResponse<SagaInstanceResponse>> compensate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(sagaService.compensate(id), "Compensation started"));
    }
}
