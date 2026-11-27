package com.waylo.hotelservice.automation;

import com.waylo.hotelservice.automation.AutomationDtos.RateRuleRequest;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.domain.RowSource;
import com.waylo.hotelservice.dto.InventoryUpdateItem;
import com.waylo.hotelservice.dto.InventoryUpdateRequest;
import com.waylo.hotelservice.dto.RateUpdateItem;
import com.waylo.hotelservice.dto.RateUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.InventoryAdjustmentRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.InventoryServiceImpl;
import com.waylo.hotelservice.service.RateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Validation codes of the rule store (through the controller), and MANUAL stamping of the old PUT endpoints. */
class AutomationControllerTest {

    private static final UUID HOTEL = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID RT = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-06-04T00:00:00Z"), ZoneOffset.UTC);

    private HotelRepository hotelRepository;
    private HotelRoomTypeRepository roomTypeRepository;
    private RatePlanRepository ratePlanRepository;
    private HotelRateRuleRepository rateRuleRepository;
    private DailyRateRepository dailyRateRepository;
    private HotelAccessGuard guard;
    private HotelRuleGenerator generator;
    private AutomationController controller;

    @BeforeEach
    void setUp() {
        hotelRepository = mock(HotelRepository.class);
        roomTypeRepository = mock(HotelRoomTypeRepository.class);
        ratePlanRepository = mock(RatePlanRepository.class);
        rateRuleRepository = mock(HotelRateRuleRepository.class);
        dailyRateRepository = mock(DailyRateRepository.class);
        guard = mock(HotelAccessGuard.class);
        generator = mock(HotelRuleGenerator.class);
        when(generator.apply(any(), any())).thenReturn(AutomationSummary.EMPTY);

        HotelRoomType rt = new HotelRoomType();
        rt.setId(RT);
        rt.setHotelId(HOTEL);
        rt.setMaxOccupancy(3);
        rt.setStatus(CatalogStatus.ACTIVE);
        when(roomTypeRepository.findByIdAndHotelId(RT, HOTEL)).thenReturn(Optional.of(rt));
        RatePlan plan = new RatePlan();
        plan.setId(PLAN);
        plan.setHotelId(HOTEL);
        plan.setStatus(CatalogStatus.ACTIVE);
        when(ratePlanRepository.findByIdAndHotelId(PLAN, HOTEL)).thenReturn(Optional.of(plan));
        when(hotelRepository.findByIdForUpdate(HOTEL)).thenReturn(Optional.of(new Hotel()));
        when(rateRuleRepository.findByHotelIdAndActiveTrue(HOTEL)).thenReturn(List.of());
        when(rateRuleRepository.findByHotelIdAndRoomTypeIdAndRatePlanIdAndActiveTrue(HOTEL, RT, PLAN)).thenReturn(List.of());
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL)).thenReturn(List.of());
        when(rateRuleRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        AutomationRuleStore store = new AutomationRuleStore(hotelRepository, roomTypeRepository, ratePlanRepository,
                mock(HotelInventoryRuleRepository.class), rateRuleRepository, dailyRateRepository);
        AutomationService service = new AutomationService(store, generator, mock(HotelInventoryRuleRepository.class),
                rateRuleRepository, mock(DailyInventoryRepository.class), dailyRateRepository);
        controller = new AutomationController(service, guard);
    }

    private static RateRuleRequest request(LocalDate from, LocalDate to, String currency, int baseOcc) {
        return new RateRuleRequest(RT, PLAN, "Sezon", from, to, currency, baseOcc,
                new BigDecimal("4000"), null, new BigDecimal("800"), new BigDecimal("500"), true, null);
    }

    private static HotelRateRule existing(LocalDate from, LocalDate to) {
        HotelRateRule r = new HotelRateRule();
        r.setId(UUID.randomUUID());
        r.setRoomTypeId(RT);
        r.setRatePlanId(PLAN);
        r.setName("Eski");
        r.setValidFrom(from);
        r.setValidTo(to);
        r.setCurrency("TRY");
        return r;
    }

    private String codeOf(Runnable call) {
        return ((BusinessRuleException) org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class, call::run)).getRuleCode();
    }

    @Test
    void createReturns201AndAppliesAfterSave() {
        var response = controller.createRateRule(USER, HOTEL, request(null, null, "TRY", 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(guard).requireRoomManage(USER, HOTEL);
        verify(generator).apply(HOTEL, AutomationScope.ratesOf(RT, PLAN));
    }

    @Test
    void invalidDateRangeWhenToBeforeFromOrOnlyOneBound() {
        assertThat(codeOf(() -> controller.createRateRule(USER, HOTEL,
                request(LocalDate.of(2027, 8, 1), LocalDate.of(2027, 7, 1), "TRY", 2)))).isEqualTo("INVALID_DATE_RANGE");
        assertThat(codeOf(() -> controller.createRateRule(USER, HOTEL,
                request(LocalDate.of(2027, 8, 1), null, "TRY", 2)))).isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void occupancyRuleWhenBaseOccupancyExceedsRoomType() {
        assertThat(codeOf(() -> controller.createRateRule(USER, HOTEL, request(null, null, "TRY", 4)))).isEqualTo("OCCUPANCY_RULE");
    }

    @Test
    void currencyMismatchAgainstExistingRatesAndRules() {
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL)).thenReturn(List.of("TRY"));
        assertThat(codeOf(() -> controller.createRateRule(USER, HOTEL, request(null, null, "EUR", 2)))).isEqualTo("CURRENCY_MISMATCH");

        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL)).thenReturn(List.of());
        when(rateRuleRepository.findByHotelIdAndActiveTrue(HOTEL)).thenReturn(List.of(existing(null, null)));
        assertThat(codeOf(() -> controller.createRateRule(USER, HOTEL, request(null, null, "EUR", 2)))).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void ruleOverlapForSecondDefaultAndOverlappingSeasons() {
        when(rateRuleRepository.findByHotelIdAndRoomTypeIdAndRatePlanIdAndActiveTrue(HOTEL, RT, PLAN))
                .thenReturn(List.of(existing(null, null), existing(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 8, 31))));

        assertThat(codeOf(() -> controller.createRateRule(USER, HOTEL, request(null, null, "TRY", 2)))).isEqualTo("RULE_OVERLAP");
        assertThat(codeOf(() -> controller.createRateRule(USER, HOTEL,
                request(LocalDate.of(2027, 8, 31), LocalDate.of(2027, 9, 10), "TRY", 2)))).isEqualTo("RULE_OVERLAP");
    }

    @Test
    void adjacentSeasonDoesNotOverlap() {
        when(rateRuleRepository.findByHotelIdAndRoomTypeIdAndRatePlanIdAndActiveTrue(HOTEL, RT, PLAN))
                .thenReturn(List.of(existing(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 8, 31))));

        var response = controller.createRateRule(USER, HOTEL,
                request(LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 30), "TRY", 2));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void putInventoryStampsManualOnInsertAndOnUpdateOfRuleRow() {
        DailyInventoryRepository invRepo = mock(DailyInventoryRepository.class);
        HotelRoomTypeRepository rtRepo = mock(HotelRoomTypeRepository.class);
        HotelRepository hRepo = mock(HotelRepository.class);
        when(hRepo.existsById(HOTEL)).thenReturn(true);
        when(rtRepo.findByIdAndHotelId(RT, HOTEL)).thenReturn(Optional.of(new HotelRoomType()));
        LocalDate d1 = LocalDate.of(2027, 6, 5);
        LocalDate d2 = LocalDate.of(2027, 6, 6);
        DailyInventory ruleRow = new DailyInventory();
        ruleRow.setHotelId(HOTEL);
        ruleRow.setRoomTypeId(RT);
        ruleRow.setStayDate(d1);
        ruleRow.setAllotment(5);
        ruleRow.setSource(RowSource.RULE);
        when(invRepo.findByHotelIdAndRoomTypeIdsAndDateRangeWithLock(any(), anyList(), any(), any())).thenReturn(List.of(ruleRow));
        InventoryServiceImpl svc = new InventoryServiceImpl(invRepo, mock(InventoryAdjustmentRepository.class), rtRepo, hRepo, CLOCK);

        svc.updateInventory(HOTEL, USER, new InventoryUpdateRequest("test", List.of(
                new InventoryUpdateItem(RT, d1, 7, null, null, null),
                new InventoryUpdateItem(RT, d2, 4, null, null, null))));

        assertThat(ruleRow.getSource()).isEqualTo(RowSource.MANUAL);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyInventory>> c = ArgumentCaptor.forClass(List.class);
        verify(invRepo).saveAllAndFlush(c.capture());
        assertThat(c.getValue()).hasSize(2).allMatch(r -> r.getSource() == RowSource.MANUAL);
    }

    @Test
    void putRatesStampsManualOnInsertAndOnUpdateOfRuleRow() {
        DailyRateRepository rateRepo = mock(DailyRateRepository.class);
        HotelRoomType rt = new HotelRoomType();
        rt.setId(RT);
        rt.setHotelId(HOTEL);
        rt.setMaxOccupancy(3);
        RatePlan plan = new RatePlan();
        plan.setId(PLAN);
        plan.setHotelId(HOTEL);
        when(hotelRepository.findByIdForUpdate(HOTEL)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any())).thenReturn(List.of(rt));
        when(ratePlanRepository.findAllById(any())).thenReturn(List.of(plan));
        LocalDate d1 = LocalDate.of(2027, 6, 5);
        LocalDate d2 = LocalDate.of(2027, 6, 6);
        DailyRate ruleRow = new DailyRate();
        ruleRow.setHotelId(HOTEL);
        ruleRow.setRoomTypeId(RT);
        ruleRow.setRatePlanId(PLAN);
        ruleRow.setStayDate(d1);
        ruleRow.setOccupancy(2);
        ruleRow.setAmount(new BigDecimal("100.00"));
        ruleRow.setCurrency("TRY");
        ruleRow.setSource(RowSource.RULE);
        when(rateRepo.findCurrenciesByHotelId(HOTEL)).thenReturn(List.of());
        when(rateRepo.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(any(), anyCollection(), anyCollection(), any(), any()))
                .thenReturn(List.of(ruleRow));
        when(rateRepo.saveAllAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        RateServiceImpl svc = new RateServiceImpl(rateRepo, hotelRepository, roomTypeRepository, ratePlanRepository, CLOCK);

        svc.updateRates(HOTEL, new RateUpdateRequest(List.of(
                new RateUpdateItem(RT, PLAN, d1, 2, new BigDecimal("150.00"), "TRY", null),
                new RateUpdateItem(RT, PLAN, d2, 2, new BigDecimal("160.00"), "TRY", null))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyRate>> c = ArgumentCaptor.forClass(List.class);
        verify(rateRepo).saveAllAndFlush(c.capture());
        assertThat(c.getValue()).hasSize(2).allMatch(r -> r.getSource() == RowSource.MANUAL);
        assertThat(ruleRow.getAmount()).isEqualByComparingTo("150.00");
    }
}
