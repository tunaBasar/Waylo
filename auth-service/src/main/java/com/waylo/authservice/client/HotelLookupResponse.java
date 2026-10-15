package com.waylo.authservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelLookupResponse(
        boolean success,
        HotelData data) {
}
