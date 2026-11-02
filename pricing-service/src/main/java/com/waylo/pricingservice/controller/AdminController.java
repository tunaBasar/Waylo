package com.waylo.pricingservice.controller;

import com.waylo.pricingservice.dto.*;
import com.waylo.pricingservice.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD endpoints for campaigns, promo codes, and tax rates (§4).
 * Protected by AdminRoleFilter.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // --- Campaigns ---

    @PostMapping("/campaigns")
    public ResponseEntity<CampaignResponse> createCampaign(
            @RequestBody @Valid CampaignRequest req) {
        return ResponseEntity.ok(adminService.createCampaign(req));
    }

    @GetMapping("/campaigns/{id}")
    public ResponseEntity<CampaignResponse> getCampaign(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getCampaign(id));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignResponse>> listCampaigns() {
        return ResponseEntity.ok(adminService.listCampaigns());
    }

    @PatchMapping("/campaigns/{id}")
    public ResponseEntity<CampaignResponse> updateCampaign(
            @PathVariable UUID id,
            @RequestBody @Valid CampaignUpdateRequest req) {
        return ResponseEntity.ok(adminService.updateCampaign(id, req));
    }

    @DeleteMapping("/campaigns/{id}")
    public ResponseEntity<Void> deleteCampaign(@PathVariable UUID id) {
        adminService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    // --- Promo Codes ---

    @PostMapping("/promo-codes")
    public ResponseEntity<List<PromoCodeResponse>> createPromoCodes(
            @RequestBody @Valid PromoCodeRequest req) {
        return ResponseEntity.ok(adminService.createPromoCodes(req));
    }

    @GetMapping("/campaigns/{campaignId}/promo-codes")
    public ResponseEntity<List<PromoCodeResponse>> listPromoCodes(
            @PathVariable UUID campaignId) {
        return ResponseEntity.ok(adminService.listPromoCodes(campaignId));
    }

    @DeleteMapping("/promo-codes/{id}")
    public ResponseEntity<Void> deletePromoCode(@PathVariable UUID id) {
        adminService.deletePromoCode(id);
        return ResponseEntity.noContent().build();
    }

    // --- Tax Rates ---

    @PostMapping("/tax-rates")
    public ResponseEntity<TaxRateResponse> createTaxRate(
            @RequestBody @Valid TaxRateRequest req) {
        return ResponseEntity.ok(adminService.createTaxRate(req));
    }

    @GetMapping("/tax-rates")
    public ResponseEntity<List<TaxRateResponse>> listTaxRates() {
        return ResponseEntity.ok(adminService.listTaxRates());
    }

    @GetMapping("/tax-rates/{id}")
    public ResponseEntity<TaxRateResponse> getTaxRate(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getTaxRate(id));
    }

    @DeleteMapping("/tax-rates/{id}")
    public ResponseEntity<Void> deleteTaxRate(@PathVariable UUID id) {
        adminService.deleteTaxRate(id);
        return ResponseEntity.noContent().build();
    }
}
