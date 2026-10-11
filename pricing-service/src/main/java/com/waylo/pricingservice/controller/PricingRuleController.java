package com.waylo.pricingservice.controller;

import com.waylo.pricingservice.domain.PricingType;
import com.waylo.pricingservice.dto.ApiResponse;
import com.waylo.pricingservice.dto.PricingRuleRequest;
import com.waylo.pricingservice.dto.PricingRuleResponse;
import com.waylo.pricingservice.service.PricingRuleService;
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
@RequestMapping("/api/pricing-rules")
public class PricingRuleController {

    private final PricingRuleService pricingRuleService;

    public PricingRuleController(PricingRuleService pricingRuleService) {
        this.pricingRuleService = pricingRuleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PricingRuleResponse>> create(@Valid @RequestBody PricingRuleRequest request) {
        PricingRuleResponse response = pricingRuleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response, "Pricing rule created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(pricingRuleService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(pricingRuleService.getAll()));
    }

    @GetMapping("/type/{pricingType}")
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> getByType(@PathVariable PricingType pricingType) {
        return ResponseEntity.ok(ApiResponse.of(pricingRuleService.getByType(pricingType)));
    }

    @GetMapping("/type/{pricingType}/active")
    public ResponseEntity<ApiResponse<List<PricingRuleResponse>>> getActiveByType(@PathVariable PricingType pricingType) {
        return ResponseEntity.ok(ApiResponse.of(pricingRuleService.getActiveByType(pricingType)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> update(@PathVariable UUID id,
                                                                    @Valid @RequestBody PricingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.of(pricingRuleService.update(id, request), "Pricing rule updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pricingRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(pricingRuleService.deactivate(id), "Pricing rule deactivated"));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(pricingRuleService.activate(id), "Pricing rule activated"));
    }
}
