package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.ApiResponse;
import com.waylo.packagebookingservice.dto.PartnerOrderItemPage;
import com.waylo.packagebookingservice.service.PartnerOrderService;
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
@RequestMapping("/api/partner/organizations/{organizationId}/order-items")
public class PartnerOrderController {

    private final PartnerOrderService partnerOrderService;

    public PartnerOrderController(PartnerOrderService partnerOrderService) {
        this.partnerOrderService = partnerOrderService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PartnerOrderItemPage>> list(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID organizationId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(
                partnerOrderService.list(userId, organizationId, status, from, to, page, size)));
    }
}
