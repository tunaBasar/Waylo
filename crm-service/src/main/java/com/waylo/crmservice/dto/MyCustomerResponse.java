package com.waylo.crmservice.dto;

import com.waylo.crmservice.domain.CustomerTier;

/** Own-record view. loyaltyPoints/tier are null while the loyalty feature is disabled. */
public record MyCustomerResponse(java.util.UUID id, String firstName, String lastName, String email,
                                 boolean loyaltyEnabled, Integer loyaltyPoints, CustomerTier tier) {}
