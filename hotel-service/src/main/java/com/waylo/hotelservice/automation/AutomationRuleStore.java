package com.waylo.hotelservice.automation;

import com.waylo.hotelservice.automation.AutomationDtos.InventoryRuleRequest;
import com.waylo.hotelservice.automation.AutomationDtos.RateRuleRequest;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Validates and saves rules, one transaction per call. Generation is done afterwards by the facade. */
@Service
public class AutomationRuleStore {

    private final HotelRepository hotelRepository;
    private final HotelRoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final HotelInventoryRuleRepository inventoryRuleRepository;
    private final HotelRateRuleRepository rateRuleRepository;
    private final DailyRateRepository dailyRateRepository;

    public AutomationRuleStore(HotelRepository hotelRepository,
                               HotelRoomTypeRepository roomTypeRepository,
                               RatePlanRepository ratePlanRepository,
                               HotelInventoryRuleRepository inventoryRuleRepository,
                               HotelRateRuleRepository rateRuleRepository,
                               DailyRateRepository dailyRateRepository) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.inventoryRuleRepository = inventoryRuleRepository;
        this.rateRuleRepository = rateRuleRepository;
        this.dailyRateRepository = dailyRateRepository;
    }

    @Transactional
    public HotelInventoryRule saveInventoryRule(UUID hotelId, UUID roomTypeId, InventoryRuleRequest req) {
        requireActiveRoomType(hotelId, roomTypeId);
        HotelInventoryRule rule = inventoryRuleRepository.findByHotelIdAndRoomTypeId(hotelId, roomTypeId).orElse(null);
        if (rule == null) {
            if (req.expectedVersion() != null) {
                throw new ObjectOptimisticLockingFailureException(HotelInventoryRule.class, "new-rule");
            }
            rule = new HotelInventoryRule();
            rule.setHotelId(hotelId);
            rule.setRoomTypeId(roomTypeId);
        } else if (req.expectedVersion() != null && !req.expectedVersion().equals(rule.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(HotelInventoryRule.class, rule.getId().toString());
        }
        rule.setDefaultAllotment(req.defaultAllotment());
        rule.setHorizonDays(req.horizonDays());
        rule.setActive(req.active());
        return inventoryRuleRepository.saveAndFlush(rule);
    }

    @Transactional
    public HotelRateRule createRateRule(UUID hotelId, RateRuleRequest req) {
        HotelRateRule rule = new HotelRateRule();
        rule.setHotelId(hotelId);
        rule.setRoomTypeId(req.roomTypeId());
        rule.setRatePlanId(req.ratePlanId());
        return validateAndSave(hotelId, rule, req);
    }

    @Transactional
    public HotelRateRule updateRateRule(UUID hotelId, UUID ruleId, RateRuleRequest req) {
        HotelRateRule rule = rateRuleRepository.findByIdAndHotelId(ruleId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Rate rule", "id", ruleId));
        if (!rule.getRoomTypeId().equals(req.roomTypeId()) || !rule.getRatePlanId().equals(req.ratePlanId())) {
            throw new IllegalArgumentException("roomTypeId and ratePlanId of a rate rule cannot change");
        }
        if (req.expectedVersion() != null && !req.expectedVersion().equals(rule.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(HotelRateRule.class, ruleId.toString());
        }
        return validateAndSave(hotelId, rule, req);
    }

    @Transactional
    public void deleteRateRule(UUID hotelId, UUID ruleId) {
        HotelRateRule rule = rateRuleRepository.findByIdAndHotelId(ruleId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Rate rule", "id", ruleId));
        rateRuleRepository.delete(rule);
    }

    private HotelRateRule validateAndSave(UUID hotelId, HotelRateRule rule, RateRuleRequest req) {
        // Serialize rule changes (and manual rate edits) of this hotel: overlap and currency checks stay race free.
        hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));
        HotelRoomType roomType = requireActiveRoomType(hotelId, req.roomTypeId());
        RatePlan plan = ratePlanRepository.findByIdAndHotelId(req.ratePlanId(), hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Rate plan", "id", req.ratePlanId()));
        if (plan.getStatus() != CatalogStatus.ACTIVE) {
            throw new BusinessRuleException("RATE_PLAN_INACTIVE", "Rate plan is not active");
        }
        if ((req.validFrom() == null) != (req.validTo() == null)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "validFrom and validTo must both be set or both empty");
        }
        if (req.validFrom() != null && req.validTo().isBefore(req.validFrom())) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "validTo must be on or after validFrom");
        }
        if (req.baseOccupancy() > roomType.getMaxOccupancy()) {
            throw new BusinessRuleException("OCCUPANCY_RULE",
                    "Base occupancy " + req.baseOccupancy() + " exceeds room type max occupancy " + roomType.getMaxOccupancy());
        }
        if (req.baseAmount().signum() <= 0
                || (req.weekendBaseAmount() != null && req.weekendBaseAmount().signum() <= 0)
                || req.extraPersonAmount().signum() < 0 || req.reducedPersonAmount().signum() < 0) {
            throw new IllegalArgumentException("Amounts must be positive (extra and reduced amounts may be zero)");
        }
        for (String existing : dailyRateRepository.findCurrenciesByHotelId(hotelId)) {
            if (!existing.equals(req.currency())) {
                throw new BusinessRuleException("CURRENCY_MISMATCH",
                        "Currency " + req.currency() + " does not match existing hotel currency " + existing);
            }
        }
        for (HotelRateRule other : rateRuleRepository.findByHotelIdAndActiveTrue(hotelId)) {
            if (!other.getCurrency().equals(req.currency())) {
                throw new BusinessRuleException("CURRENCY_MISMATCH",
                        "Currency " + req.currency() + " does not match existing hotel currency " + other.getCurrency());
            }
        }
        if (req.active()) {
            checkOverlap(hotelId, rule, req);
        }
        rule.setName(req.name().trim());
        rule.setValidFrom(req.validFrom());
        rule.setValidTo(req.validTo());
        rule.setCurrency(req.currency());
        rule.setBaseOccupancy(req.baseOccupancy());
        rule.setBaseAmount(scale(req.baseAmount()));
        rule.setWeekendBaseAmount(req.weekendBaseAmount() == null ? null : scale(req.weekendBaseAmount()));
        rule.setExtraPersonAmount(scale(req.extraPersonAmount()));
        rule.setReducedPersonAmount(scale(req.reducedPersonAmount()));
        rule.setActive(req.active());
        return rateRuleRepository.saveAndFlush(rule);
    }

    private void checkOverlap(UUID hotelId, HotelRateRule self, RateRuleRequest req) {
        List<HotelRateRule> others = rateRuleRepository.findByHotelIdAndRoomTypeIdAndRatePlanIdAndActiveTrue(
                hotelId, req.roomTypeId(), req.ratePlanId());
        boolean season = req.validFrom() != null;
        for (HotelRateRule o : others) {
            if (self.getId() != null && self.getId().equals(o.getId())) {
                continue;
            }
            if (!season && !o.isSeason()) {
                throw new BusinessRuleException("RULE_OVERLAP", "An active default rule already exists for this room type and rate plan");
            }
            if (season && o.isSeason()
                    && !req.validFrom().isAfter(o.getValidTo()) && !o.getValidFrom().isAfter(req.validTo())) {
                throw new BusinessRuleException("RULE_OVERLAP", "Season overlaps with rule '" + o.getName() + "'");
            }
        }
    }

    private HotelRoomType requireActiveRoomType(UUID hotelId, UUID roomTypeId) {
        HotelRoomType rt = roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type", "id", roomTypeId));
        if (rt.getStatus() != CatalogStatus.ACTIVE) {
            throw new BusinessRuleException("ROOM_TYPE_INACTIVE", "Room type is not active");
        }
        return rt;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
