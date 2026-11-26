package com.waylo.paymentservice.controller;

import com.waylo.paymentservice.domain.BankAccountStatus;
import com.waylo.paymentservice.domain.PayoutStatus;
import com.waylo.paymentservice.dto.ApiResponse;
import com.waylo.paymentservice.dto.PayoutDtos;
import com.waylo.paymentservice.service.BankAccountService;
import com.waylo.paymentservice.service.PayoutService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform admin payout operations (X-User-Role = ADMIN, checked in the services). */
@RestController
@RequestMapping("/api/admin")
public class AdminPayoutController {

    private final PayoutService payouts;
    private final BankAccountService bankAccounts;

    public AdminPayoutController(PayoutService payouts, BankAccountService bankAccounts) {
        this.payouts = payouts;
        this.bankAccounts = bankAccounts;
    }

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<PayoutDtos.PageResponse<PayoutDtos.PayoutResponse>>> queue(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) List<PayoutStatus> status, @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.of(payouts.adminQueue(userId, role, status, organizationId, currency, page, size)));
    }

    @GetMapping("/payouts/{id}")
    public ResponseEntity<ApiResponse<PayoutDtos.AdminPayoutDetail>> detail(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(payouts.adminDetail(userId, role, id)));
    }

    @GetMapping("/payouts/{id}/bank-details")
    public ResponseEntity<ApiResponse<PayoutDtos.BankDetailsResponse>> bankDetails(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.of(bankAccounts.reveal(userId, role, id)));
    }

    @PostMapping("/payouts/{id}/approve")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> approve(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id,
            @RequestBody(required = false) PayoutDtos.TransitionRequest body) {
        return ResponseEntity.ok(ApiResponse.of(payouts.approve(userId, role, id, version(body))));
    }

    @PostMapping("/payouts/{id}/processing")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> processing(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id,
            @RequestBody(required = false) PayoutDtos.TransitionRequest body) {
        return ResponseEntity.ok(ApiResponse.of(payouts.startProcessing(userId, role, id, version(body))));
    }

    @PostMapping("/payouts/{id}/paid")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> paid(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id,
            @Valid @RequestBody PayoutDtos.PaidRequest body) {
        return ResponseEntity.ok(ApiResponse.of(payouts.markPaid(userId, role, id, body.reference(), body.paidAt(), body.expectedVersion())));
    }

    @PostMapping("/payouts/{id}/failed")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> failed(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id,
            @Valid @RequestBody PayoutDtos.ReasonRequest body) {
        return ResponseEntity.ok(ApiResponse.of(payouts.markFailed(userId, role, id, body.reason(), body.expectedVersion())));
    }

    @PostMapping("/payouts/{id}/cancel")
    public ResponseEntity<ApiResponse<PayoutDtos.PayoutResponse>> cancel(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id,
            @Valid @RequestBody PayoutDtos.ReasonRequest body) {
        return ResponseEntity.ok(ApiResponse.of(payouts.adminCancel(userId, role, id, body.reason(), body.expectedVersion())));
    }

    @GetMapping("/bank-accounts")
    public ResponseEntity<ApiResponse<List<PayoutDtos.AdminBankAccountResponse>>> bankAccountQueue(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) BankAccountStatus status, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.of(bankAccounts.adminList(userId, role, status, size)));
    }

    @GetMapping("/bank-accounts/{id}/iban")
    public ResponseEntity<ApiResponse<PayoutDtos.BankAccountIbanResponse>> bankAccountIban(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.of(bankAccounts.revealAccount(userId, role, id)));
    }

    @PostMapping("/bank-accounts/{id}/verify")
    public ResponseEntity<ApiResponse<PayoutDtos.AdminBankAccountResponse>> verify(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(bankAccounts.verify(userId, role, id)));
    }

    @PostMapping("/bank-accounts/{id}/reject")
    public ResponseEntity<ApiResponse<PayoutDtos.AdminBankAccountResponse>> reject(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID id,
            @Valid @RequestBody PayoutDtos.RejectBankAccountRequest body) {
        return ResponseEntity.ok(ApiResponse.of(bankAccounts.reject(userId, role, id, body.reason())));
    }

    @GetMapping("/organizations/{organizationId}/payout-balance")
    public ResponseEntity<ApiResponse<PayoutDtos.AdminBalanceResponse>> payoutBalance(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID organizationId) {
        return ResponseEntity.ok(ApiResponse.of(payouts.adminBalance(userId, role, organizationId)));
    }

    @PostMapping("/organizations/{organizationId}/payout-adjustments")
    public ResponseEntity<ApiResponse<PayoutDtos.AdjustmentResponse>> adjust(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String role, @PathVariable UUID organizationId,
            @Valid @RequestBody PayoutDtos.AdjustmentRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(payouts.adjust(userId, role, organizationId, body), "Adjustment recorded"));
    }

    private static Long version(PayoutDtos.TransitionRequest body) {
        return body == null ? null : body.expectedVersion();
    }
}
