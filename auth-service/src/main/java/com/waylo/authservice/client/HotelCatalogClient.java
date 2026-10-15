package com.waylo.authservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "hotel-service")
public interface HotelCatalogClient {

    @GetMapping("/api/hotels/{id}")
    HotelLookupResponse getHotel(@PathVariable("id") UUID id);
}
