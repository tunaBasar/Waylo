package com.waylo.crmservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/** package-booking-service internal order read (ownership check). Signed X-Service-Token is added globally (service-auth-common). */
@FeignClient(name = "package-booking-service", contextId = "supportOrderClient", path = "/internal/orders")
public interface OrderClient {

    @GetMapping("/{orderId}")
    Envelope<OrderDto> getOrder(@PathVariable("orderId") UUID orderId);

    record Envelope<T>(boolean success, T data, String message) {}

    record OrderDto(UUID id, UUID customerId, String status, List<ItemDto> items) {}

    record ItemDto(UUID orderItemId, UUID supplierOrganizationId) {}
}
