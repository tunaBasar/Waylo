package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.PartnerEarningsResponse;
import com.waylo.paymentservice.service.PartnerEarningsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/partner/organizations/{organizationId}/earnings")
public class PartnerEarningsController {

    private final PartnerEarningsService service;

    public PartnerEarningsController(PartnerEarningsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PartnerEarningsResponse>> earnings(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.of(service.earnings(userId, organizationId, from, to)));
    }
}
