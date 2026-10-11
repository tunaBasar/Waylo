package com.waylo.packagebookingservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(name = "hotel-service", path = "/api/hotels")
public interface HotelServiceClient {

    @GetMapping("/{id}")
    HotelDto getById(@PathVariable UUID id);

    record HotelDto(
            UUID id,
            String name,
            String city,
            String country,
            int starRating,
            boolean active
    ) {}
}
