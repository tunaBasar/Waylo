package com.waylo.packagebookingservice.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Hotel review eligibility of one order item. reason is null when eligible, otherwise a stable rejection code. */
public record ReviewEligibilityResponse(UUID orderItemId, boolean eligible, String reason, UUID customerId,
                                        UUID fulfillmentRef, UUID supplierOrganizationId, String description,
                                        LocalDate checkIn, LocalDate checkOut) {}
