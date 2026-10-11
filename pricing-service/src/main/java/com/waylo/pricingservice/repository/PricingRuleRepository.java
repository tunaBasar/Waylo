package com.waylo.pricingservice.repository;

import com.waylo.pricingservice.domain.PricingRule;
import com.waylo.pricingservice.domain.PricingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    List<PricingRule> findByPricingType(PricingType pricingType);

    List<PricingRule> findByActiveTrue();

    List<PricingRule> findByPricingTypeAndActiveTrue(PricingType pricingType);

    List<PricingRule> findByCurrency(String currency);

    boolean existsByName(String name);
}
