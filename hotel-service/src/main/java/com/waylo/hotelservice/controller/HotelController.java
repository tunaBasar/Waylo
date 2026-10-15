package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.HotelRequest;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.service.HotelService;
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
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HotelResponse>> create(@Valid @RequestBody HotelRequest request) {
        HotelResponse response = hotelService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Hotel created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(hotelService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HotelResponse>>> getAll(
            @RequestParam(required = false) UUID organizationId) {
        List<HotelResponse> hotels = organizationId != null
                ? hotelService.getByOrganization(organizationId)
                : hotelService.getAll();
        return ResponseEntity.ok(ApiResponse.of(hotels));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<HotelResponse>>> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer minStars) {
        return ResponseEntity.ok(ApiResponse.of(hotelService.search(city, country, minStars)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HotelResponse>> update(@PathVariable UUID id,
                                                              @Valid @RequestBody HotelRequest request) {
        return ResponseEntity.ok(ApiResponse.of(hotelService.update(id, request), "Hotel updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        hotelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<HotelResponse>> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(hotelService.deactivate(id), "Hotel deactivated"));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<HotelResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(hotelService.activate(id), "Hotel activated"));
    }
}
