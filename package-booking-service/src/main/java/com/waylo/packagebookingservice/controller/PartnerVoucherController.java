package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.dto.ApiResponse;
import com.waylo.packagebookingservice.dto.RedeemRequest;
import com.waylo.packagebookingservice.dto.RedeemResponse;
import com.waylo.packagebookingservice.service.VoucherRedemptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/partner/organizations/{organizationId}/vouchers")
public class PartnerVoucherController {

    private final VoucherRedemptionService redemptionService;

    public PartnerVoucherController(VoucherRedemptionService redemptionService) {
        this.redemptionService = redemptionService;
    }

    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<RedeemResponse>> redeem(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID organizationId,
            @Valid @RequestBody RedeemRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                redemptionService.redeem(userId, organizationId, request.payload()), "Voucher redeemed"));
    }
}
