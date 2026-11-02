package com.waylo.busservice.controller;

import com.waylo.busservice.domain.DocumentType;
import com.waylo.busservice.dto.ApiResponse;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.service.BusService;
import com.waylo.busservice.service.DocumentService;
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
@RequestMapping("/api/bus-partner/organizations/{orgId}")
public class PartnerBusController {

    private final BusService service;
    private final DocumentService documents;

    public PartnerBusController(BusService service, DocumentService documents) {
        this.service = service;
        this.documents = documents;
    }

    @GetMapping("/terminals")
    public ApiResponse<List<TerminalResponse>> terminals(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        service.listBuses(user, orgId); // access check only
        return ApiResponse.of(service.listTerminals(true));
    }

    @GetMapping("/buses")
    public ApiResponse<List<BusResponse>> buses(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(service.listBuses(user, orgId));
    }

    @PostMapping("/buses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusResponse> createBus(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                              @Valid @RequestBody BusRequest req) {
        return ApiResponse.of(service.createBus(user, orgId, req));
    }

    @PutMapping("/buses/{id}")
    public ApiResponse<BusResponse> updateBus(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                              @PathVariable UUID id, @Valid @RequestBody BusRequest req) {
        return ApiResponse.of(service.updateBus(user, orgId, id, req));
    }

    @PostMapping("/buses/{id}/deactivate")
    public ApiResponse<BusResponse> deactivate(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                               @PathVariable UUID id) {
        return ApiResponse.of(service.setBusActive(user, orgId, id, false));
    }

    @PostMapping("/buses/{id}/activate")
    public ApiResponse<BusResponse> activate(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                             @PathVariable UUID id) {
        return ApiResponse.of(service.setBusActive(user, orgId, id, true));
    }

    @GetMapping("/trips")
    public ApiResponse<List<TripResponse>> trips(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(service.listTrips(user, orgId));
    }

    @PostMapping("/trips")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TripResponse> createTrip(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                @Valid @RequestBody TripRequest req) {
        return ApiResponse.of(service.createTrip(user, orgId, req));
    }

    @PostMapping("/trips/{id}/cancel")
    public ApiResponse<TripResponse> cancelTrip(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                @PathVariable UUID id) {
        return ApiResponse.of(service.cancelTrip(user, orgId, id));
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
                                                @RequestParam(value = "busId", required = false) UUID busId) {
        return ApiResponse.of(documents.upload(user, orgId, file, type, documentNumber, validUntil, busId));
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> file(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId, @PathVariable UUID id) {
        return attachment(documents.partnerFile(user, orgId, id));
    }

    @GetMapping("/bookings")
    public ApiResponse<List<BookingResponse>> bookings(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(service.partnerBookings(user, orgId));
    }

    @GetMapping("/status")
    public ApiResponse<PartnerStatus> status(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(service.status(user, orgId));
    }

    static ResponseEntity<byte[]> attachment(DocumentService.StoredFile f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.content());
    }
}
