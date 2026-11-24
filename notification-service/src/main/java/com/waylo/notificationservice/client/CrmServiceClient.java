package com.waylo.notificationservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Looks up a customer's contact details for notification delivery.
 * Response is wrapped in the standard ApiResponse envelope used by all services.
 */
@FeignClient(name = "crm-service", path = "/api/customers")
public interface CrmServiceClient {

    @GetMapping("/{id}")
    CustomerLookupResponse getById(@PathVariable UUID id);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CustomerLookupResponse(boolean success, CustomerDto data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CustomerDto(
            UUID id,
            String firstName,
            String lastName,
            String email,
            String phone
    ) {}
}
