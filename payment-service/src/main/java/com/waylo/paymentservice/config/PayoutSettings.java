package com.waylo.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** waylo.payouts.* (PAYOUT-CONTRACT.md sec. 5). A currency without a minimum entry is not payable. */
@Component
@ConfigurationProperties(prefix = "waylo.payouts")
public class PayoutSettings {

    private int holdbackDays = 0;
    private Map<String, BigDecimal> minimumAmount = new LinkedHashMap<>(Map.of("TRY", new BigDecimal("100.00")));
    private boolean requireDifferentAdmins = false;

    public int getHoldbackDays() { return holdbackDays; }
    public void setHoldbackDays(int holdbackDays) { this.holdbackDays = holdbackDays; }
    public Map<String, BigDecimal> getMinimumAmount() { return minimumAmount; }
    public void setMinimumAmount(Map<String, BigDecimal> minimumAmount) { this.minimumAmount = minimumAmount; }
    public boolean isRequireDifferentAdmins() { return requireDifferentAdmins; }
    public void setRequireDifferentAdmins(boolean requireDifferentAdmins) { this.requireDifferentAdmins = requireDifferentAdmins; }

    /** Minimum payout for the currency, or null when the currency is not payable. */
    public BigDecimal minimumFor(String currency) {
        if (minimumAmount == null || currency == null) return null;
        for (Map.Entry<String, BigDecimal> e : minimumAmount.entrySet()) {
            if (e.getKey().equalsIgnoreCase(currency) && e.getValue() != null) return e.getValue().setScale(2, java.math.RoundingMode.HALF_EVEN);
        }
        return null;
    }
}
