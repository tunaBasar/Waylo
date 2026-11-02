package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.domain.DocumentType;
import com.waylo.carrentalservice.dto.ApiResponse;
import com.waylo.carrentalservice.dto.CarDtos.*;
import com.waylo.carrentalservice.service.DocumentService;
import com.waylo.carrentalservice.service.FleetService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/partner/organizations/{orgId}")
public class PartnerController {

    private final FleetService fleet;
    private final DocumentService documents;

    public PartnerController(FleetService fleet, DocumentService documents) {
        this.fleet = fleet;
        this.documents = documents;
    }

    @GetMapping("/branches")
    public ApiResponse<List<BranchResponse>> branches(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(fleet.listBranches(user, orgId));
    }

    @PostMapping("/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BranchResponse> createBranch(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                    @Valid @RequestBody BranchRequest req) {
        return ApiResponse.of(fleet.createBranch(user, orgId, req));
    }

    @PutMapping("/branches/{id}")
    public ApiResponse<BranchResponse> updateBranch(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                    @PathVariable UUID id, @Valid @RequestBody BranchRequest req) {
        return ApiResponse.of(fleet.updateBranch(user, orgId, id, req));
    }

    @GetMapping("/vehicles")
    public ApiResponse<List<VehicleResponse>> vehicles(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(fleet.listVehicles(user, orgId));
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VehicleResponse> createVehicle(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                      @Valid @RequestBody VehicleRequest req) {
        return ApiResponse.of(fleet.createVehicle(user, orgId, req));
    }

    @PutMapping("/vehicles/{id}")
    public ApiResponse<VehicleResponse> updateVehicle(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                      @PathVariable UUID id, @Valid @RequestBody VehicleRequest req) {
        return ApiResponse.of(fleet.updateVehicle(user, orgId, id, req));
    }

    @PostMapping("/vehicles/{id}/deactivate")
    public ApiResponse<VehicleResponse> deactivate(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                   @PathVariable UUID id) {
        return ApiResponse.of(fleet.setActive(user, orgId, id, false));
    }

    @PostMapping("/vehicles/{id}/activate")
    public ApiResponse<VehicleResponse> activate(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                 @PathVariable UUID id) {
        return ApiResponse.of(fleet.setActive(user, orgId, id, true));
    }

    @GetMapping("/rates")
    public ApiResponse<List<RateResponse>> rates(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(fleet.listRates(user, orgId));
    }

    @PutMapping("/rates")
    public ApiResponse<List<RateResponse>> upsertRates(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                       @Valid @RequestBody RatesRequest req) {
        return ApiResponse.of(fleet.upsertRates(user, orgId, req));
    }

    @GetMapping("/one-way-routes")
    public ApiResponse<List<RouteResponse>> routes(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(fleet.listRoutes(user, orgId));
    }

    @PutMapping("/one-way-routes")
    public ApiResponse<List<RouteResponse>> replaceRoutes(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                          @Valid @RequestBody RoutesRequest req) {
        return ApiResponse.of(fleet.replaceRoutes(user, orgId, req));
    }

    @GetMapping("/documents")
    public ApiResponse<List<DocumentResponse>> documents(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(documents.list(user, orgId));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentResponse> upload(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                @RequestPart("file") MultipartFile file,
                                                @RequestParam("type") DocumentType type,
                                                @RequestParam("documentNumber") String documentNumber,
                                                @RequestParam(value = "validUntil", required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
                                                @RequestParam(value = "vehicleId", required = false) UUID vehicleId) {
        return ApiResponse.of(documents.upload(user, orgId, file, type, documentNumber, validUntil, vehicleId));
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> file(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId, @PathVariable UUID id) {
        return attachment(documents.partnerFile(user, orgId, id));
    }

    @GetMapping("/rentals")
    public ApiResponse<List<RentalResponse>> rentals(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(fleet.partnerRentals(user, orgId));
    }

    @GetMapping("/status")
    public ApiResponse<PartnerStatus> status(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(fleet.status(user, orgId));
    }

    static ResponseEntity<byte[]> attachment(DocumentService.StoredFile f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.content());
    }
}
