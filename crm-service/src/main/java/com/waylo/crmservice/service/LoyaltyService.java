package com.waylo.crmservice.service;

import com.waylo.crmservice.domain.Customer;
import com.waylo.crmservice.domain.CustomerTier;
import com.waylo.crmservice.domain.ProcessedEvent;
import com.waylo.crmservice.repository.CustomerRepository;
import com.waylo.crmservice.repository.ProcessedEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Loyalty mechanism only. points-per-try = points per 1 TRY of a redeemed order item (0 = disabled).
 * Tier thresholds (0 = that tier disabled) only ever raise a tier. The numbers are an open business decision.
 */
@Service
public class LoyaltyService {

    private static final String GROUP = "crm-service";

    private final CustomerRepository customers;
    private final ProcessedEventRepository processed;
    private final BigDecimal pointsPerTry;
    private final int silver;
    private final int gold;
    private final int platinum;

    public LoyaltyService(CustomerRepository customers, ProcessedEventRepository processed,
                          @Value("${waylo.crm.loyalty.points-per-try:0}") BigDecimal pointsPerTry,
                          @Value("${waylo.crm.loyalty.tier-silver-points:0}") int silver,
                          @Value("${waylo.crm.loyalty.tier-gold-points:0}") int gold,
                          @Value("${waylo.crm.loyalty.tier-platinum-points:0}") int platinum) {
        this.customers = customers;
        this.processed = processed;
        this.pointsPerTry = pointsPerTry;
        this.silver = silver;
        this.gold = gold;
        this.platinum = platinum;
    }

    public boolean isEnabled() {
        return pointsPerTry != null && pointsPerTry.signum() > 0;
    }

    /** Awards points once per order item. Returns points awarded (0 if disabled, duplicate, non-TRY or unknown customer). */
    @Transactional
    public int awardForRedeemedItem(UUID orderItemId, UUID customerId, String currency, BigDecimal amount) {
        if (!isEnabled() || orderItemId == null || customerId == null || amount == null || !"TRY".equalsIgnoreCase(currency)) {
            return 0;
        }
        String key = "order-item-redeemed:" + orderItemId;
        if (processed.existsByEventIdAndConsumerGroup(key, GROUP)) return 0;
        Customer c = customers.findById(customerId).filter(Customer::isActive).orElse(null);
        if (c == null) return 0;
        int points = amount.multiply(pointsPerTry).setScale(0, RoundingMode.FLOOR).intValue();
        processed.saveAndFlush(new ProcessedEvent(key, GROUP));
        if (points <= 0) return 0;
        c.setLoyaltyPoints(c.getLoyaltyPoints() + points);
        CustomerTier target = tierFor(c.getLoyaltyPoints());
        if (target.ordinal() > c.getTier().ordinal()) c.setTier(target);
        customers.save(c);
        return points;
    }

    CustomerTier tierFor(int points) {
        if (platinum > 0 && points >= platinum) return CustomerTier.PLATINUM;
        if (gold > 0 && points >= gold) return CustomerTier.GOLD;
        if (silver > 0 && points >= silver) return CustomerTier.SILVER;
        return CustomerTier.BRONZE;
    }
}
