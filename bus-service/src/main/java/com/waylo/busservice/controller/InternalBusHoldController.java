package com.waylo.busservice.controller;

import com.waylo.busservice.dto.ApiResponse;
import com.waylo.busservice.dto.BusDtos.HoldDto;
import com.waylo.busservice.dto.BusDtos.HoldRequest;
import com.waylo.busservice.service.HoldService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/bus-holds")
public class InternalBusHoldController {

    private final HoldService holds;

    public InternalBusHoldController(HoldService holds) { this.holds = holds; }

    @PostMapping
    public ApiResponse<HoldDto> hold(@Valid @RequestBody HoldRequest req) { return ApiResponse.of(holds.hold(req)); }

    @PostMapping("/{id}/confirm")
    public ApiResponse<HoldDto> confirm(@PathVariable UUID id) { return ApiResponse.of(holds.confirm(id)); }

    @PostMapping("/{id}/release")
    public ApiResponse<HoldDto> release(@PathVariable UUID id) { return ApiResponse.of(holds.release(id)); }
}
