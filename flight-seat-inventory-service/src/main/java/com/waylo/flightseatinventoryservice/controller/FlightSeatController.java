package com.waylo.flightseatinventoryservice.controller;

import com.waylo.flightseatinventoryservice.dto.ApiResponse;
import com.waylo.flightseatinventoryservice.dto.BulkSeatRequest;
import com.waylo.flightseatinventoryservice.dto.BulkSeatResponse;
import com.waylo.flightseatinventoryservice.dto.FlightSeatRequest;
import com.waylo.flightseatinventoryservice.dto.FlightSeatResponse;
import com.waylo.flightseatinventoryservice.dto.SeatReservationRequest;
import com.waylo.flightseatinventoryservice.dto.SeatReservationResponse;
import com.waylo.flightseatinventoryservice.service.FlightSeatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/seat-inventory")
public class FlightSeatController {

    private final FlightSeatService flightSeatService;

    public FlightSeatController(FlightSeatService flightSeatService) {
        this.flightSeatService = flightSeatService;
    }

    @PostMapping("/seats")
    public ResponseEntity<ApiResponse<FlightSeatResponse>> createFlightSeat(
            @Valid @RequestBody FlightSeatRequest request) {
        FlightSeatResponse response = flightSeatService.createFlightSeat(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Seat created"));
    }

    @PostMapping("/seats/bulk")
    public ResponseEntity<ApiResponse<BulkSeatResponse>> createSeatsBulk(
            @Valid @RequestBody BulkSeatRequest request) {
        BulkSeatResponse response = flightSeatService.createSeatsBulk(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Seats created"));
    }

    @DeleteMapping("/seats/{flightNumber}/{seatNumber}")
    public ResponseEntity<Void> deleteSeat(@PathVariable String flightNumber,
                                           @PathVariable String seatNumber) {
        flightSeatService.deleteSeat(flightNumber, seatNumber);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/seats/{flightNumber}")
    public ResponseEntity<ApiResponse<List<FlightSeatResponse>>> getFlightSeats(
            @PathVariable String flightNumber) {
        return ResponseEntity.ok(ApiResponse.of(flightSeatService.getFlightSeats(flightNumber)));
    }

    @GetMapping("/seats/{flightNumber}/available")
    public ResponseEntity<ApiResponse<List<FlightSeatResponse>>> getAvailableSeats(
            @PathVariable String flightNumber) {
        return ResponseEntity.ok(ApiResponse.of(flightSeatService.getAvailableSeats(flightNumber)));
    }

    @PostMapping("/seats/{flightNumber}/reserve")
    public ResponseEntity<ApiResponse<SeatReservationResponse>> reserveSeat(
            @PathVariable String flightNumber,
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SeatReservationRequest request) {
        SeatReservationResponse response = flightSeatService.reserveSeat(
                flightNumber, request.seatNumber(), UUID.fromString(userId.trim()));
        return ResponseEntity.ok(ApiResponse.of(response, "Seat reserved"));
    }

    @DeleteMapping("/seats/{flightNumber}/reserve")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable String flightNumber,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody SeatReservationRequest request) {
        flightSeatService.cancelReservation(flightNumber, request.seatNumber(),
                UUID.fromString(userId.trim()), "ADMIN".equals(role));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/seats/{flightNumber}/{seatNumber}")
    public ResponseEntity<ApiResponse<FlightSeatResponse>> getFlightSeat(
            @PathVariable String flightNumber,
            @PathVariable String seatNumber) {
        return ResponseEntity.ok(ApiResponse.of(flightSeatService.getFlightSeat(flightNumber, seatNumber)));
    }

    @GetMapping("/seats/{flightNumber}/count")
    public ResponseEntity<ApiResponse<Long>> getAvailableSeatCount(
            @PathVariable String flightNumber) {
        return ResponseEntity.ok(ApiResponse.of(flightSeatService.getAvailableSeatCount(flightNumber)));
    }
}
