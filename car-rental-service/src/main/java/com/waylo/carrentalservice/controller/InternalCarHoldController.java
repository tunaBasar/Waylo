package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.dto.ApiResponse;
import com.waylo.carrentalservice.dto.CarDtos.HoldDto;
import com.waylo.carrentalservice.dto.CarDtos.HoldRequest;
import com.waylo.carrentalservice.service.HoldService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/car-holds")
public class InternalCarHoldController {

    private final HoldService holds;

    public InternalCarHoldController(HoldService holds) { this.holds = holds; }

    @PostMapping
    public ApiResponse<HoldDto> hold(@Valid @RequestBody HoldRequest req) { return ApiResponse.of(holds.hold(req)); }

    @PostMapping("/{id}/confirm")
    public ApiResponse<HoldDto> confirm(@PathVariable UUID id) { return ApiResponse.of(holds.confirm(id)); }

    @PostMapping("/{id}/release")
    public ApiResponse<HoldDto> release(@PathVariable UUID id) { return ApiResponse.of(holds.release(id)); }
}
