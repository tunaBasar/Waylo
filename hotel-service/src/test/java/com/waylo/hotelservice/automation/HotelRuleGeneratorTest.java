package com.waylo.hotelservice.automation;

import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.domain.RowSource;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HotelRuleGeneratorTest {

    private static final UUID HOTEL = UUID.randomUUID();
    private static final UUID RT = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    // 2027-06-04 00:00 UTC = 03:00 in Istanbul, same date
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-06-04T00:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 4);

    @Mock private HotelRepository hotelRepository;
    @Mock private HotelRoomTypeRepository roomTypeRepository;
    @Mock private RatePlanRepository ratePlanRepository;
    @Mock private HotelInventoryRuleRepository inventoryRuleRepository;
    @Mock private HotelRateRuleRepository rateRuleRepository;
    @Mock private DailyInventoryRepository inventoryRepository;
    @Mock private DailyRateRepository rateRepository;
    @Mock private RuleRowWriter writer;

    private HotelRuleGenerator generator;
    private HotelInventoryRule invRule;
    private List<DailyInventory> existingInventory;
    private List<DailyRate> existingRates;

    @BeforeEach
    void setUp() {
        generator = new HotelRuleGenerator(hotelRepository, roomTypeRepository, ratePlanRepository,
                inventoryRuleRepository, rateRuleRepository, inventoryRepository, rateRepository, writer, CLOCK);
        when(hotelRepository.findByIdForUpdate(HOTEL)).thenReturn(Optional.of(new Hotel()));

        HotelRoomType rt = new HotelRoomType();
        rt.setId(RT);
        rt.setHotelId(HOTEL);
        rt.setMaxOccupancy(2);
        rt.setStatus(CatalogStatus.ACTIVE);
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(HOTEL, CatalogStatus.ACTIVE)).thenReturn(List.of(rt));

        RatePlan plan = new RatePlan();
        plan.setId(PLAN);
        plan.setHotelId(HOTEL);
        when(ratePlanRepository.findByHotelIdOrderByNameAsc(HOTEL)).thenReturn(List.of(plan));

        invRule = new HotelInventoryRule();
        invRule.setHotelId(HOTEL);
        invRule.setRoomTypeId(RT);
        invRule.setDefaultAllotment(5);
        invRule.setHorizonDays(30);
        when(inventoryRuleRepository.findByHotelId(HOTEL)).thenReturn(List.of(invRule));

        existingInventory = new ArrayList<>();
        when(inventoryRepository.lockNightsForHold(eq(HOTEL), eq(RT), any(), any())).thenAnswer(i -> existingInventory);
        existingRates = new ArrayList<>();
        when(rateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(eq(HOTEL), anyCollection(), anyCollection(), any(), any()))
                .thenAnswer(i -> existingRates);
        when(rateRuleRepository.findByHotelIdAndActiveTrue(HOTEL)).thenReturn(List.of());
        when(writer.insertInventory(eq(HOTEL), any())).thenAnswer(i -> ((List<?>) i.getArgument(1)).size());
        when(writer.insertRates(eq(HOTEL), any())).thenAnswer(i -> ((List<?>) i.getArgument(1)).size());
    }

    private static DailyInventory row(LocalDate date, int allotment, RowSource source) {
        DailyInventory d = new DailyInventory();
        d.setHotelId(HOTEL);
        d.setRoomTypeId(RT);
        d.setStayDate(date);
        d.setAllotment(allotment);
        d.setSource(source);
        return d;
    }

    private static DailyRate rate(LocalDate date, int occ, String amount, RowSource source) {
        DailyRate r = new DailyRate();
        r.setHotelId(HOTEL);
        r.setRoomTypeId(RT);
        r.setRatePlanId(PLAN);
        r.setStayDate(date);
        r.setOccupancy(occ);
        r.setAmount(new BigDecimal(amount));
        r.setCurrency("TRY");
        r.setSource(source);
        return r;
    }

    private void defaultRateRule(String base) {
        HotelRateRule r = new HotelRateRule();
        r.setHotelId(HOTEL);
        r.setRoomTypeId(RT);
        r.setRatePlanId(PLAN);
        r.setName("default");
        r.setCurrency("TRY");
        r.setBaseOccupancy(2);
        r.setBaseAmount(new BigDecimal(base));
        r.setUpdatedAt(Instant.EPOCH);
        when(rateRuleRepository.findByHotelIdAndActiveTrue(HOTEL)).thenReturn(List.of(r));
    }

    @SuppressWarnings("unchecked")
    private List<RuleRowWriter.InventoryInsert> capturedInventoryInserts() {
        ArgumentCaptor<List<RuleRowWriter.InventoryInsert>> c = ArgumentCaptor.forClass(List.class);
        verify(writer).insertInventory(eq(HOTEL), c.capture());
        return c.getValue();
    }

    @Test
    void insertsMissingNightsAsRuleStartingToday() {
        AutomationSummary s = generator.apply(HOTEL, AutomationScope.ALL);

        List<RuleRowWriter.InventoryInsert> inserts = capturedInventoryInserts();
        assertThat(inserts).hasSize(30);
        assertThat(inserts.get(0).stayDate()).isEqualTo(TODAY);
        assertThat(inserts.get(29).stayDate()).isEqualTo(TODAY.plusDays(29));
        assertThat(inserts).allMatch(i -> i.allotment() == 5);
        assertThat(s.inventoryCreated()).isEqualTo(30);
        assertThat(s.skippedPast()).isZero();
    }

    @Test
    void usesDefaultHorizon365WhenNoInventoryRule() {
        when(inventoryRuleRepository.findByHotelId(HOTEL)).thenReturn(List.of());
        defaultRateRule("1000");

        generator.apply(HOTEL, AutomationScope.ALL);

        verify(writer, never()).insertInventory(any(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RuleRowWriter.RateInsert>> c = ArgumentCaptor.forClass(List.class);
        verify(writer).insertRates(eq(HOTEL), c.capture());
        assertThat(c.getValue()).hasSize(365 * 2);
    }

    @Test
    void neverTouchesManualRows() {
        DailyInventory manual = row(TODAY, 99, RowSource.MANUAL);
        manual.setStopSell(true);
        existingInventory.add(manual);
        defaultRateRule("1000");
        DailyRate manualRate = rate(TODAY, 2, "777.00", RowSource.MANUAL);
        existingRates.add(manualRate);

        AutomationSummary s = generator.apply(HOTEL, AutomationScope.ALL);

        assertThat(manual.getAllotment()).isEqualTo(99);
        assertThat(manual.isStopSell()).isTrue();
        assertThat(manual.getSource()).isEqualTo(RowSource.MANUAL);
        assertThat(manualRate.getAmount()).isEqualByComparingTo("777.00");
        assertThat(s.skippedManual()).isEqualTo(1);
        assertThat(capturedInventoryInserts()).hasSize(29);
    }

    @Test
    void updatesRuleRowsWhoseAllotmentDiffers() {
        DailyInventory stale = row(TODAY.plusDays(1), 2, RowSource.RULE);
        existingInventory.add(stale);

        AutomationSummary s = generator.apply(HOTEL, AutomationScope.ALL);

        assertThat(stale.getAllotment()).isEqualTo(5);
        assertThat(s.updated()).isEqualTo(1);
    }

    @Test
    void allotmentNeverDropsBelowCommitted() {
        DailyInventory busy = row(TODAY.plusDays(2), 10, RowSource.RULE);
        busy.setOutOfService(1);
        busy.setHeld(2);
        busy.setConfirmed(4); // 7 committed > rule 5
        existingInventory.add(busy);

        generator.apply(HOTEL, AutomationScope.ALL);

        assertThat(busy.getAllotment()).isEqualTo(7);
    }

    @Test
    void updatesRuleRateAmountButNotManual() {
        defaultRateRule("1200");
        DailyRate stale = rate(TODAY, 2, "1000.00", RowSource.RULE);
        existingRates.add(stale);

        AutomationSummary s = generator.apply(HOTEL, AutomationScope.ratesOf(RT, PLAN));

        assertThat(stale.getAmount()).isEqualByComparingTo("1200.00");
        assertThat(s.updated()).isEqualTo(1);
    }

    @Test
    void secondRunChangesNothing() {
        defaultRateRule("1000");
        // state after a first run: every night and occupancy exists with the rule's values
        for (int d = 0; d < 30; d++) {
            existingInventory.add(row(TODAY.plusDays(d), 5, RowSource.RULE));
        }
        for (int d = 0; d < 365; d++) {
            for (int occ = 1; occ <= 2; occ++) {
                existingRates.add(rate(TODAY.plusDays(d), occ, "1000.00", RowSource.RULE));
            }
        }

        AutomationSummary s = generator.apply(HOTEL, AutomationScope.ALL);

        assertThat(s.created()).isZero();
        assertThat(s.updated()).isZero();
        assertThat(s.skippedManual()).isZero();
        verify(writer, never()).insertInventory(any(), any());
        verify(writer, never()).insertRates(any(), any());
    }

    @Test
    void ruleRowsNoLongerCoveredAreLeftAlone() {
        // no active rate rules at all, but an old RULE row exists
        DailyRate old = rate(TODAY, 2, "500.00", RowSource.RULE);
        existingRates.add(old);

        generator.apply(HOTEL, AutomationScope.ALL);

        assertThat(old.getAmount()).isEqualByComparingTo("500.00");
        verify(rateRepository, never()).delete(any());
        verify(rateRepository, never()).deleteAll(any());
    }

    @Test
    void resetTurnsManualBackToRuleAndReappliesRule() {
        defaultRateRule("1000");
        LocalDate d = TODAY.plusDays(3);
        DailyInventory manual = row(d, 99, RowSource.MANUAL);
        manual.setStopSell(true);
        manual.setOutOfService(2);
        existingInventory.add(manual);
        DailyRate manualRate = rate(d, 2, "777.00", RowSource.MANUAL);
        existingRates.add(manualRate);

        generator.reset(HOTEL, RT, d, d);

        assertThat(manual.getSource()).isEqualTo(RowSource.RULE);
        assertThat(manual.isStopSell()).isFalse();
        assertThat(manual.getOutOfService()).isEqualTo(2);
        assertThat(manual.getAllotment()).isEqualTo(5);
        assertThat(manualRate.getSource()).isEqualTo(RowSource.RULE);
        assertThat(manualRate.getAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void resetKeepsAmountOfNightsNoRuleCoversButMakesThemRule() {
        // season rule far in the future, so TODAY+3 is uncovered
        HotelRateRule season = new HotelRateRule();
        season.setHotelId(HOTEL);
        season.setRoomTypeId(RT);
        season.setRatePlanId(PLAN);
        season.setName("later");
        season.setCurrency("TRY");
        season.setBaseOccupancy(2);
        season.setBaseAmount(new BigDecimal("900"));
        season.setValidFrom(TODAY.plusDays(100));
        season.setValidTo(TODAY.plusDays(110));
        season.setUpdatedAt(Instant.EPOCH);
        when(rateRuleRepository.findByHotelIdAndActiveTrue(HOTEL)).thenReturn(List.of(season));
        LocalDate d = TODAY.plusDays(3);
        DailyRate manualRate = rate(d, 1, "321.00", RowSource.MANUAL);
        existingRates.add(manualRate);

        generator.reset(HOTEL, RT, d, d);

        assertThat(manualRate.getSource()).isEqualTo(RowSource.RULE);
        assertThat(manualRate.getAmount()).isEqualByComparingTo("321.00");
    }

    @Test
    void pastDatesAreNeverGenerated() {
        AutomationSummary s = generator.reset(HOTEL, RT, TODAY.minusDays(3), TODAY.plusDays(1));

        List<RuleRowWriter.InventoryInsert> inserts = capturedInventoryInserts();
        assertThat(inserts).extracting(RuleRowWriter.InventoryInsert::stayDate)
                .allMatch(day -> !day.isBefore(TODAY));
        assertThat(inserts).hasSize(2);
        assertThat(s.skippedPast()).isEqualTo(3);
    }

    @Test
    void todayIsComputedInIstanbulNotUtc() {
        Clock lateUtc = Clock.fixed(Instant.parse("2027-06-04T22:30:00Z"), ZoneOffset.UTC); // 01:30 on 06-05 in Istanbul
        HotelRuleGenerator g = new HotelRuleGenerator(hotelRepository, roomTypeRepository, ratePlanRepository,
                inventoryRuleRepository, rateRuleRepository, inventoryRepository, rateRepository, writer, lateUtc);

        assertThat(g.today()).isEqualTo(LocalDate.of(2027, 6, 5));
    }
}
