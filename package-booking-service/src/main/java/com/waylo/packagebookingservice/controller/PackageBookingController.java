package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.domain.BookingStatus;
import com.waylo.packagebookingservice.dto.ApiResponse;
import com.waylo.packagebookingservice.dto.PackageBookingRequest;
import com.waylo.packagebookingservice.dto.PackageBookingResponse;
import com.waylo.packagebookingservice.service.PackageBookingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class PackageBookingController {

    private final PackageBookingService bookingService;

    public PackageBookingController(PackageBookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PackageBookingResponse>> create(@Valid @RequestBody PackageBookingRequest request) {
        PackageBookingResponse response = bookingService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Booking created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageBookingResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(bookingService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PackageBookingResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(bookingService.getAll()));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<List<PackageBookingResponse>>> getByCustomerId(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.of(bookingService.getByCustomerId(customerId)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PackageBookingResponse>>> search(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) BookingStatus status) {
        return ResponseEntity.ok(ApiResponse.of(bookingService.search(customerId, status)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PackageBookingResponse>> update(@PathVariable UUID id,
                                                                       @Valid @RequestBody PackageBookingRequest request) {
        return ResponseEntity.ok(ApiResponse.of(bookingService.update(id, request), "Booking updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        bookingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PackageBookingResponse>> updateStatus(@PathVariable UUID id,
                                                                             @RequestParam BookingStatus status) {
        return ResponseEntity.ok(ApiResponse.of(bookingService.updateStatus(id, status), "Booking status updated"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PackageBookingResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(bookingService.cancel(id), "Booking cancelled"));
    }
}
