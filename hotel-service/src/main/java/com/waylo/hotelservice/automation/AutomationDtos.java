package com.waylo.hotelservice.automation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AutomationDtos {

    private AutomationDtos() {
    }

    public record InventoryRuleRequest(
            @NotNull @Min(0) @Max(10000) Integer defaultAllotment,
            @NotNull @Min(30) @Max(730) Integer horizonDays,
            @NotNull Boolean active,
            Long expectedVersion) {}

    public record RateRuleRequest(
            @NotNull UUID roomTypeId,
            @NotNull UUID ratePlanId,
            @NotBlank @Size(max = 80) String name,
            LocalDate validFrom,
            LocalDate validTo,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull @Min(1) @Max(30) Integer baseOccupancy,
            @NotNull BigDecimal baseAmount,
            BigDecimal weekendBaseAmount,
            @NotNull BigDecimal extraPersonAmount,
            @NotNull BigDecimal reducedPersonAmount,
            @NotNull Boolean active,
            Long expectedVersion) {}

    public record ResetRequest(UUID roomTypeId, @NotNull LocalDate from, @NotNull LocalDate to) {}

    public record InventoryRuleResponse(UUID id, UUID hotelId, UUID roomTypeId, int defaultAllotment,
                                        int horizonDays, boolean active, Long version, Instant updatedAt) {
        static InventoryRuleResponse of(HotelInventoryRule r) {
            return new InventoryRuleResponse(r.getId(), r.getHotelId(), r.getRoomTypeId(), r.getDefaultAllotment(),
                    r.getHorizonDays(), r.isActive(), r.getVersion(), r.getUpdatedAt());
        }
    }

    public record RateRuleResponse(UUID id, UUID hotelId, UUID roomTypeId, UUID ratePlanId, String name,
                                   LocalDate validFrom, LocalDate validTo, String currency, int baseOccupancy,
                                   BigDecimal baseAmount, BigDecimal weekendBaseAmount,
                                   BigDecimal extraPersonAmount, BigDecimal reducedPersonAmount,
                                   boolean active, Long version, Instant updatedAt) {
        static RateRuleResponse of(HotelRateRule r) {
            return new RateRuleResponse(r.getId(), r.getHotelId(), r.getRoomTypeId(), r.getRatePlanId(), r.getName(),
                    r.getValidFrom(), r.getValidTo(), r.getCurrency(), r.getBaseOccupancy(), r.getBaseAmount(),
                    r.getWeekendBaseAmount(), r.getExtraPersonAmount(), r.getReducedPersonAmount(),
                    r.isActive(), r.getVersion(), r.getUpdatedAt());
        }
    }

    public record Coverage(UUID roomTypeId, LocalDate inventoryUntil, LocalDate ratesUntil) {}

    public record AutomationOverview(List<InventoryRuleResponse> inventoryRules,
                                     List<RateRuleResponse> rateRules, List<Coverage> coverage) {}

    public record InventoryRuleResult(InventoryRuleResponse rule, AutomationSummary summary) {}

    public record RateRuleResult(RateRuleResponse rule, AutomationSummary summary) {}
}
