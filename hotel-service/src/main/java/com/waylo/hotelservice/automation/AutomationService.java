package com.waylo.hotelservice.automation;

import com.waylo.hotelservice.automation.AutomationDtos.AutomationOverview;
import com.waylo.hotelservice.automation.AutomationDtos.Coverage;
import com.waylo.hotelservice.automation.AutomationDtos.InventoryRuleRequest;
import com.waylo.hotelservice.automation.AutomationDtos.InventoryRuleResponse;
import com.waylo.hotelservice.automation.AutomationDtos.InventoryRuleResult;
import com.waylo.hotelservice.automation.AutomationDtos.RateRuleRequest;
import com.waylo.hotelservice.automation.AutomationDtos.RateRuleResponse;
import com.waylo.hotelservice.automation.AutomationDtos.RateRuleResult;
import com.waylo.hotelservice.automation.AutomationDtos.ResetRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Non-transactional facade: the rule is saved (and committed) by {@link AutomationRuleStore} first, then the
 * generator runs in its own transaction (contract 1: generation never runs inside the rule's transaction).
 */
@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);
    static final int MAX_RESET_DAYS = 730;

    private final AutomationRuleStore store;
    private final HotelRuleGenerator generator;
    private final HotelInventoryRuleRepository inventoryRuleRepository;
    private final HotelRateRuleRepository rateRuleRepository;
    private final DailyInventoryRepository inventoryRepository;
    private final DailyRateRepository rateRepository;

    public AutomationService(AutomationRuleStore store,
                             HotelRuleGenerator generator,
                             HotelInventoryRuleRepository inventoryRuleRepository,
                             HotelRateRuleRepository rateRuleRepository,
                             DailyInventoryRepository inventoryRepository,
                             DailyRateRepository rateRepository) {
        this.store = store;
        this.generator = generator;
        this.inventoryRuleRepository = inventoryRuleRepository;
        this.rateRuleRepository = rateRuleRepository;
        this.inventoryRepository = inventoryRepository;
        this.rateRepository = rateRepository;
    }

    public AutomationOverview overview(UUID hotelId) {
        List<InventoryRuleResponse> inv = inventoryRuleRepository.findByHotelId(hotelId).stream()
                .map(InventoryRuleResponse::of).toList();
        List<RateRuleResponse> rates = rateRuleRepository.findByHotelId(hotelId).stream()
                .map(RateRuleResponse::of).toList();
        Map<UUID, LocalDate> invUntil = lastDates(inventoryRepository.findLastStayDates(hotelId));
        Map<UUID, LocalDate> rateUntil = lastDates(rateRepository.findLastStayDates(hotelId));
        Set<UUID> ids = new LinkedHashSet<>(invUntil.keySet());
        ids.addAll(rateUntil.keySet());
        List<Coverage> coverage = ids.stream()
                .map(id -> new Coverage(id, invUntil.get(id), rateUntil.get(id))).toList();
        return new AutomationOverview(inv, rates, coverage);
    }

    public InventoryRuleResult saveInventoryRule(UUID hotelId, UUID roomTypeId, InventoryRuleRequest req) {
        HotelInventoryRule rule = store.saveInventoryRule(hotelId, roomTypeId, req);
        AutomationSummary summary = generator.apply(hotelId, AutomationScope.inventoryOf(roomTypeId));
        return new InventoryRuleResult(InventoryRuleResponse.of(rule), summary);
    }

    public RateRuleResult createRateRule(UUID hotelId, RateRuleRequest req) {
        HotelRateRule rule = store.createRateRule(hotelId, req);
        return new RateRuleResult(RateRuleResponse.of(rule), applyRates(hotelId, rule));
    }

    public RateRuleResult updateRateRule(UUID hotelId, UUID ruleId, RateRuleRequest req) {
        HotelRateRule rule = store.updateRateRule(hotelId, ruleId, req);
        return new RateRuleResult(RateRuleResponse.of(rule), applyRates(hotelId, rule));
    }

    public void deleteRateRule(UUID hotelId, UUID ruleId) {
        store.deleteRateRule(hotelId, ruleId);
    }

    public AutomationSummary apply(UUID hotelId) {
        return generator.apply(hotelId, AutomationScope.ALL);
    }

    public AutomationSummary reset(UUID hotelId, ResetRequest req) {
        LocalDate today = generator.today();
        if (req.from().isBefore(today) || req.to().isBefore(req.from())) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "from must be today or later and to must not be before from");
        }
        if (ChronoUnit.DAYS.between(req.from(), req.to()) + 1 > MAX_RESET_DAYS) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "Range must be at most " + MAX_RESET_DAYS + " days");
        }
        return generator.reset(hotelId, req.roomTypeId(), req.from(), req.to());
    }

    /** Daily job: every hotel with an active rule, each in its own transaction; a failure never stops the run. */
    public AutomationSummary applyAllHotels() {
        Set<UUID> hotels = new LinkedHashSet<>(inventoryRuleRepository.findHotelIdsWithActiveRules());
        hotels.addAll(rateRuleRepository.findHotelIdsWithActiveRules());
        AutomationSummary total = AutomationSummary.EMPTY;
        for (UUID hotelId : hotels) {
            try {
                total = total.plus(generator.apply(hotelId, AutomationScope.ALL));
            } catch (RuntimeException e) {
                log.error("Hotel automation failed for hotelId={}", hotelId, e);
            }
        }
        log.info("Hotel automation run: hotels={} summary={}", hotels.size(), total);
        return total;
    }

    private AutomationSummary applyRates(UUID hotelId, HotelRateRule rule) {
        return generator.apply(hotelId, AutomationScope.ratesOf(rule.getRoomTypeId(), rule.getRatePlanId()));
    }

    private static Map<UUID, LocalDate> lastDates(List<Object[]> rows) {
        Map<UUID, LocalDate> out = new TreeMap<>();
        for (Object[] r : rows) {
            out.put((UUID) r[0], (LocalDate) r[1]);
        }
        return out;
    }
}
