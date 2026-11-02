package com.waylo.busservice.controller;

import com.waylo.busservice.domain.DocumentStatus;
import com.waylo.busservice.dto.ApiResponse;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.service.BusService;
import com.waylo.busservice.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminBusController {

    private final DocumentService documents;
    private final BusService service;

    public AdminBusController(DocumentService documents, BusService service) {
        this.documents = documents;
        this.service = service;
    }

    @GetMapping("/bus-documents")
    public ApiResponse<List<AdminDocumentResponse>> list(@RequestParam(value = "status", required = false) DocumentStatus status) {
        return ApiResponse.of(documents.adminList(status));
    }

    @GetMapping("/bus-documents/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        return PartnerBusController.attachment(documents.adminFile(id));
    }

    @PostMapping("/bus-documents/{id}/approve")
    public ApiResponse<DocumentResponse> approve(@RequestHeader("X-User-Id") UUID admin, @PathVariable UUID id) {
        return ApiResponse.of(documents.approve(admin, id));
    }

    @PostMapping("/bus-documents/{id}/reject")
    public ApiResponse<DocumentResponse> reject(@RequestHeader("X-User-Id") UUID admin, @PathVariable UUID id,
                                                @Valid @RequestBody RejectRequest req) {
        return ApiResponse.of(documents.reject(admin, id, req.reason()));
    }

    @GetMapping("/bus-vehicles")
    public ApiResponse<List<AdminBusResponse>> buses() { return ApiResponse.of(service.adminBuses()); }

    @GetMapping("/bus-terminals")
    public ApiResponse<List<TerminalResponse>> terminals() { return ApiResponse.of(service.listTerminals(false)); }

    @PostMapping("/bus-terminals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TerminalResponse> createTerminal(@Valid @RequestBody TerminalRequest req) {
        return ApiResponse.of(service.createTerminal(req));
    }

    @PutMapping("/bus-terminals/{id}")
    public ApiResponse<TerminalResponse> updateTerminal(@PathVariable UUID id, @Valid @RequestBody TerminalRequest req) {
        return ApiResponse.of(service.updateTerminal(id, req));
    }
}
