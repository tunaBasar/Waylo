package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.PricingType;
import com.waylo.pricingservice.dto.PricingRuleRequest;
import com.waylo.pricingservice.dto.PricingRuleResponse;

import java.util.List;
import java.util.UUID;

public interface PricingRuleService {

    PricingRuleResponse create(PricingRuleRequest request);

    PricingRuleResponse getById(UUID id);

    List<PricingRuleResponse> getAll();

    List<PricingRuleResponse> getByType(PricingType pricingType);

    List<PricingRuleResponse> getActiveByType(PricingType pricingType);

    PricingRuleResponse update(UUID id, PricingRuleRequest request);

    void delete(UUID id);

    PricingRuleResponse deactivate(UUID id);

    PricingRuleResponse activate(UUID id);
}
