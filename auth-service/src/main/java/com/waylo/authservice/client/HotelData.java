package com.waylo.authservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HotelData(
        UUID id,
        UUID organizationId) {
}
