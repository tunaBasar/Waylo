package com.waylo.crmservice.controller;

import com.waylo.crmservice.domain.CustomerTier;
import com.waylo.crmservice.dto.ApiResponse;
import com.waylo.crmservice.dto.CustomerRequest;
import com.waylo.crmservice.dto.CustomerResponse;
import com.waylo.crmservice.dto.MyCustomerResponse;
import com.waylo.crmservice.service.CustomerService;
import com.waylo.crmservice.service.LoyaltyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService customerService;
    private final LoyaltyService loyaltyService;
    public CustomerController(CustomerService customerService, LoyaltyService loyaltyService) {
        this.customerService = customerService;
        this.loyaltyService = loyaltyService;
    }

    /** Own record only (identity from the gateway-verified X-User-Id). Loyalty fields are hidden while the feature is disabled. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyCustomerResponse>> me(@RequestHeader("X-User-Id") UUID userId) {
        CustomerResponse c = customerService.getById(userId);
        boolean on = loyaltyService.isEnabled();
        return ResponseEntity.ok(ApiResponse.of(new MyCustomerResponse(c.id(), c.firstName(), c.lastName(), c.email(),
                on, on ? c.loyaltyPoints() : null, on ? c.tier() : null)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(customerService.create(request), "Customer created"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(customerService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.of(customerService.getAll()));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByEmail(@PathVariable String email) {
        return ResponseEntity.ok(ApiResponse.of(customerService.getByEmail(email)));
    }

    @GetMapping("/tier/{tier}")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getByTier(@PathVariable CustomerTier tier) {
        return ResponseEntity.ok(ApiResponse.of(customerService.getByTier(tier)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.of(customerService.search(q)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.of(customerService.update(id, request), "Customer updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/loyalty-points")
    public ResponseEntity<ApiResponse<CustomerResponse>> addLoyaltyPoints(@PathVariable UUID id, @RequestParam int points) {
        return ResponseEntity.ok(ApiResponse.of(customerService.addLoyaltyPoints(id, points), "Loyalty points added"));
    }
}
