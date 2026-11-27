package com.waylo.hotelservice.automation;

import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.domain.RowSource;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rolling inventory and rate generator (contract 2.3). One hotel per transaction. Never touches MANUAL rows
 * (except {@link #reset}), never deletes, never creates past nights, idempotent.
 */
@Component
public class HotelRuleGenerator {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    static final int DEFAULT_HORIZON_DAYS = 365;

    private final HotelRepository hotelRepository;
    private final HotelRoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final HotelInventoryRuleRepository inventoryRuleRepository;
    private final HotelRateRuleRepository rateRuleRepository;
    private final DailyInventoryRepository inventoryRepository;
    private final DailyRateRepository rateRepository;
    private final RuleRowWriter writer;
    private final Clock clock;

    public HotelRuleGenerator(HotelRepository hotelRepository,
                              HotelRoomTypeRepository roomTypeRepository,
                              RatePlanRepository ratePlanRepository,
                              HotelInventoryRuleRepository inventoryRuleRepository,
                              HotelRateRuleRepository rateRuleRepository,
                              DailyInventoryRepository inventoryRepository,
                              DailyRateRepository rateRepository,
                              RuleRowWriter writer,
                              Clock clock) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.inventoryRuleRepository = inventoryRuleRepository;
        this.rateRuleRepository = rateRuleRepository;
        this.inventoryRepository = inventoryRepository;
        this.rateRepository = rateRepository;
        this.writer = writer;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    @Transactional
    public AutomationSummary apply(UUID hotelId, AutomationScope scope) {
        return run(hotelId, scope, null, null, false);
    }

    /** Sets MANUAL rows of [from, to] back to RULE and regenerates that range. */
    @Transactional
    public AutomationSummary reset(UUID hotelId, UUID roomTypeId, LocalDate from, LocalDate to) {
        return run(hotelId, new AutomationScope(roomTypeId, null, true, true), from, to, true);
    }

    private AutomationSummary run(UUID hotelId, AutomationScope scope, LocalDate from, LocalDate to, boolean reset) {
        LocalDate today = today();
        // Serialize with manual rate edits (RateServiceImpl takes the same lock first).
        hotelRepository.findByIdForUpdate(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));

        List<HotelRoomType> roomTypes = new ArrayList<>(
                roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE));
        roomTypes.removeIf(rt -> scope.roomTypeId() != null && !scope.roomTypeId().equals(rt.getId()));
        roomTypes.sort(Comparator.comparing(HotelRoomType::getId)); // same lock order as holds: room type, date

        Map<UUID, HotelInventoryRule> inventoryRules = new HashMap<>();
        for (HotelInventoryRule r : inventoryRuleRepository.findByHotelId(hotelId)) {
            inventoryRules.put(r.getRoomTypeId(), r);
        }
        Map<String, List<HotelRateRule>> rateRules = new HashMap<>();
        for (HotelRateRule r : rateRuleRepository.findByHotelIdAndActiveTrue(hotelId)) {
            rateRules.computeIfAbsent(r.getRoomTypeId() + "|" + r.getRatePlanId(), k -> new ArrayList<>()).add(r);
        }
        List<RatePlan> plans = new ArrayList<>(ratePlanRepository.findByHotelIdOrderByNameAsc(hotelId));
        plans.removeIf(p -> scope.ratePlanId() != null && !scope.ratePlanId().equals(p.getId()));

        Totals t = new Totals();
        for (HotelRoomType rt : roomTypes) {
            HotelInventoryRule invRule = inventoryRules.get(rt.getId());
            int horizon = invRule != null ? invRule.getHorizonDays() : DEFAULT_HORIZON_DAYS;
            LocalDate wFrom = reset ? from : today;
            LocalDate wTo = reset ? to : today.plusDays(horizon - 1L);
            if (wFrom.isBefore(today)) {
                t.skippedPast += (int) Math.min(ChronoUnit.DAYS.between(wFrom, today), ChronoUnit.DAYS.between(wFrom, wTo) + 1);
                wFrom = today;
            }
            if (wTo.isBefore(wFrom)) {
                continue;
            }
            if (scope.inventory() && invRule != null && invRule.isActive()) {
                inventory(hotelId, rt, invRule, wFrom, wTo, reset, t);
            }
            if (scope.rates()) {
                rates(hotelId, rt, plans, rateRules, wFrom, wTo, reset, t);
            }
        }
        return new AutomationSummary(t.inventoryCreated + t.ratesCreated, t.updated, t.skippedExisting,
                t.manualNights.size(), t.skippedPast, t.inventoryCreated, t.ratesCreated, t.skippedUnpriced);
    }

    private void inventory(UUID hotelId, HotelRoomType rt, HotelInventoryRule rule, LocalDate from, LocalDate to,
                           boolean reset, Totals t) {
        List<DailyInventory> rows = inventoryRepository.lockNightsForHold(hotelId, rt.getId(), from, to);
        Map<LocalDate, DailyInventory> byDate = new HashMap<>();
        for (DailyInventory row : rows) {
            byDate.put(row.getStayDate(), row);
        }
        Set<DailyInventory> changed = Collections.newSetFromMap(new IdentityHashMap<>());
        List<RuleRowWriter.InventoryInsert> inserts = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyInventory row = byDate.get(d);
            if (row == null) {
                inserts.add(new RuleRowWriter.InventoryInsert(rt.getId(), d, rule.getDefaultAllotment()));
                continue;
            }
            if (row.getSource() == RowSource.MANUAL) {
                if (!reset) {
                    t.manualNights.add(rt.getId() + "|" + d);
                    continue;
                }
                row.setSource(RowSource.RULE);
                row.setStopSell(false);
                changed.add(row);
            }
            int committed = row.getOutOfService() + row.getHeld() + row.getConfirmed();
            int target = Math.max(rule.getDefaultAllotment(), committed);
            if (row.getAllotment() != target) {
                row.setAllotment(target);
                changed.add(row);
            } else if (!changed.contains(row)) {
                t.skippedExisting++;
            }
        }
        t.updated += changed.size();
        if (!inserts.isEmpty()) {
            int n = writer.insertInventory(hotelId, inserts);
            t.inventoryCreated += n;
            t.skippedExisting += inserts.size() - n;
        }
    }

    private void rates(UUID hotelId, HotelRoomType rt, List<RatePlan> plans, Map<String, List<HotelRateRule>> rateRules,
                       LocalDate from, LocalDate to, boolean reset, Totals t) {
        if (plans.isEmpty()) {
            return;
        }
        List<UUID> planIds = plans.stream().map(RatePlan::getId).toList();
        Map<String, DailyRate> existing = new HashMap<>();
        Set<DailyRate> changed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (DailyRate r : rateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                hotelId, List.of(rt.getId()), planIds, from, to)) {
            existing.put(key(r.getRatePlanId(), r.getStayDate(), r.getOccupancy()), r);
            if (reset && r.getSource() == RowSource.MANUAL) {
                r.setSource(RowSource.RULE); // uncovered nights keep their amount and simply become RULE
                changed.add(r);
            }
        }
        List<RuleRowWriter.RateInsert> inserts = new ArrayList<>();
        for (RatePlan plan : plans) {
            List<HotelRateRule> rules = rateRules.get(rt.getId() + "|" + plan.getId());
            if (rules == null || rules.isEmpty()) {
                continue;
            }
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                HotelRateRule rule = RateRuleMath.pickRule(rules, d).orElse(null);
                if (rule == null) {
                    continue;
                }
                for (int occ = 1; occ <= rt.getMaxOccupancy(); occ++) {
                    BigDecimal amount = RateRuleMath.amountFor(rule, d, occ);
                    if (amount.signum() <= 0) {
                        t.skippedUnpriced++;
                        continue;
                    }
                    DailyRate row = existing.get(key(plan.getId(), d, occ));
                    if (row == null) {
                        inserts.add(new RuleRowWriter.RateInsert(rt.getId(), plan.getId(), d, occ, amount, rule.getCurrency()));
                    } else if (row.getSource() == RowSource.MANUAL) {
                        t.manualNights.add(rt.getId() + "|" + d);
                    } else if (row.getAmount().compareTo(amount) != 0 || !rule.getCurrency().equals(row.getCurrency())) {
                        row.setAmount(amount);
                        row.setCurrency(rule.getCurrency());
                        changed.add(row);
                    } else if (!changed.contains(row)) {
                        t.skippedExisting++;
                    }
                }
            }
        }
        t.updated += changed.size();
        if (!inserts.isEmpty()) {
            int n = writer.insertRates(hotelId, inserts);
            t.ratesCreated += n;
            t.skippedExisting += inserts.size() - n;
        }
    }

    private static String key(UUID planId, LocalDate date, int occupancy) {
        return planId + "|" + date + "|" + occupancy;
    }

    private static final class Totals {
        int inventoryCreated;
        int ratesCreated;
        int updated;
        int skippedExisting;
        int skippedPast;
        int skippedUnpriced;
        final Set<String> manualNights = new HashSet<>();
    }
}
