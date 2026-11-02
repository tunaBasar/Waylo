package com.waylo.pricingservice.dto;

import java.time.LocalDateTime;

/** Request to update a campaign (§4). */
public record CampaignUpdateRequest(
        String name,
        String description,
        LocalDateTime endAt
) {}
