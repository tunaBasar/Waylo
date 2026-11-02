package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.domain.DocumentStatus;
import com.waylo.carrentalservice.dto.ApiResponse;
import com.waylo.carrentalservice.dto.CarDtos.*;
import com.waylo.carrentalservice.service.DocumentService;
import com.waylo.carrentalservice.service.FleetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminCarController {

    private final DocumentService documents;
    private final FleetService fleet;

    public AdminCarController(DocumentService documents, FleetService fleet) {
        this.documents = documents;
        this.fleet = fleet;
    }

    @GetMapping("/car-documents")
    public ApiResponse<List<AdminDocumentResponse>> list(@RequestParam(value = "status", required = false) DocumentStatus status) {
        return ApiResponse.of(documents.adminList(status));
    }

    @GetMapping("/car-documents/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        return PartnerController.attachment(documents.adminFile(id));
    }

    @PostMapping("/car-documents/{id}/approve")
    public ApiResponse<DocumentResponse> approve(@RequestHeader("X-User-Id") UUID admin, @PathVariable UUID id) {
        return ApiResponse.of(documents.approve(admin, id));
    }

    @PostMapping("/car-documents/{id}/reject")
    public ApiResponse<DocumentResponse> reject(@RequestHeader("X-User-Id") UUID admin, @PathVariable UUID id,
                                                @Valid @RequestBody RejectRequest req) {
        return ApiResponse.of(documents.reject(admin, id, req.reason()));
    }

    @GetMapping("/car-vehicles")
    public ApiResponse<List<AdminVehicleResponse>> vehicles() {
        return ApiResponse.of(fleet.adminVehicles());
    }
}
