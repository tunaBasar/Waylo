package com.waylo.hotelservice.client;

import com.waylo.hotelservice.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/** package-booking-service internal review read model. Signed X-Service-Token is added globally. */
@FeignClient(name = "package-booking-service", contextId = "reviewOrderItemClient", path = "/internal/orders/items")
public interface OrderItemClient {

    @GetMapping("/{orderItemId}/review-eligibility")
    ApiResponse<ReviewEligibility> reviewEligibility(@PathVariable("orderItemId") UUID orderItemId,
                                                     @RequestParam("customerId") UUID customerId);

    @GetMapping("/review-eligible")
    ApiResponse<List<ReviewEligibility>> reviewEligible(@RequestParam("customerId") UUID customerId);
}
