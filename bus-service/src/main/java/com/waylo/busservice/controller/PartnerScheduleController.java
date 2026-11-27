package com.waylo.busservice.controller;

import com.waylo.busservice.dto.ApiResponse;
import com.waylo.busservice.dto.BusDtos.*;
import com.waylo.busservice.service.ScheduleRunner;
import com.waylo.busservice.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bus-partner/organizations/{orgId}/trip-schedules")
public class PartnerScheduleController {

    private final ScheduleService service;
    private final ScheduleRunner runner;

    public PartnerScheduleController(ScheduleService service, ScheduleRunner runner) {
        this.service = service;
        this.runner = runner;
    }

    @GetMapping
    public ApiResponse<List<ScheduleResponse>> list(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(service.list(user, orgId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleResult> create(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                              @Valid @RequestBody ScheduleRequest req) {
        return ApiResponse.of(runner.create(user, orgId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduleResult> update(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                              @PathVariable UUID id, @Valid @RequestBody ScheduleRequest req) {
        return ApiResponse.of(runner.update(user, orgId, id, req));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<ScheduleResult> activate(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                @PathVariable UUID id) {
        return ApiResponse.of(runner.activate(user, orgId, id));
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<ScheduleOnly> deactivate(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId,
                                                @PathVariable UUID id) {
        return ApiResponse.of(runner.deactivate(user, orgId, id));
    }

    @PostMapping("/apply")
    public ApiResponse<ScheduleSummary> apply(@RequestHeader("X-User-Id") UUID user, @PathVariable UUID orgId) {
        return ApiResponse.of(runner.applyOrganization(user, orgId));
    }
}
