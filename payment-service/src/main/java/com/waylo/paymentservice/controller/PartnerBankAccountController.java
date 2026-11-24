package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.PayoutDtos;
import com.waylo.paymentservice.service.BankAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The request body (it holds the IBAN) is never logged here and the response never echoes it. */
@RestController
@RequestMapping("/api/partner/organizations/{organizationId}/bank-account")
public class PartnerBankAccountController {

    private final BankAccountService service;

    public PartnerBankAccountController(BankAccountService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PayoutDtos.BankAccountResponse>> get(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(ApiResponse.of(service.get(userId, organizationId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<PayoutDtos.BankAccountResponse>> put(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID organizationId,
            @Valid @RequestBody PayoutDtos.BankAccountRequest body) {
        return ResponseEntity.ok(ApiResponse.of(service.put(userId, organizationId, body), "Bank account saved, pending verification"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<PayoutDtos.BankAccountResponse>> delete(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(ApiResponse.of(service.delete(userId, organizationId), "Bank account removed"));
    }
}
