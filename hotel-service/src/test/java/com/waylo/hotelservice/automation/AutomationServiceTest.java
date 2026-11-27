package com.waylo.hotelservice.automation;

import com.waylo.hotelservice.automation.AutomationDtos.ResetRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class AutomationServiceTest {

    private static final UUID HOTEL = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2027, 6, 4);

    private AutomationRuleStore store;
    private HotelRuleGenerator generator;
    private HotelInventoryRuleRepository inventoryRuleRepository;
    private HotelRateRuleRepository rateRuleRepository;
    private AutomationService service;

    @BeforeEach
    void setUp() {
        store = mock(AutomationRuleStore.class);
        generator = mock(HotelRuleGenerator.class);
        inventoryRuleRepository = mock(HotelInventoryRuleRepository.class);
        rateRuleRepository = mock(HotelRateRuleRepository.class);
        service = new AutomationService(store, generator, inventoryRuleRepository, rateRuleRepository,
                mock(DailyInventoryRepository.class), mock(DailyRateRepository.class));
        when(generator.today()).thenReturn(TODAY);
    }

    @Test
    void resetRejectsPastStart() {
        assertThatThrownBy(() -> service.reset(HOTEL, new ResetRequest(null, TODAY.minusDays(1), TODAY)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getRuleCode()).isEqualTo("INVALID_DATE_RANGE");
        verify(generator, never()).reset(any(), any(), any(), any());
    }

    @Test
    void resetRejectsToBeforeFromAndTooLongRange() {
        assertThatThrownBy(() -> service.reset(HOTEL, new ResetRequest(null, TODAY.plusDays(5), TODAY.plusDays(4))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.reset(HOTEL, new ResetRequest(null, TODAY, TODAY.plusDays(730))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resetAcceptsExactly730Days() {
        when(generator.reset(HOTEL, null, TODAY, TODAY.plusDays(729))).thenReturn(AutomationSummary.EMPTY);

        assertThat(service.reset(HOTEL, new ResetRequest(null, TODAY, TODAY.plusDays(729)))).isEqualTo(AutomationSummary.EMPTY);
    }

    @Test
    void savingInventoryRuleSavesFirstThenAppliesOnlyThatRoomType() {
        UUID rt = UUID.randomUUID();
        HotelInventoryRule rule = new HotelInventoryRule();
        rule.setRoomTypeId(rt);
        var req = new AutomationDtos.InventoryRuleRequest(5, 365, true, null);
        when(store.saveInventoryRule(HOTEL, rt, req)).thenReturn(rule);
        when(generator.apply(HOTEL, AutomationScope.inventoryOf(rt))).thenReturn(AutomationSummary.EMPTY);

        service.saveInventoryRule(HOTEL, rt, req);

        var order = org.mockito.Mockito.inOrder(store, generator);
        order.verify(store).saveInventoryRule(HOTEL, rt, req);
        order.verify(generator).apply(HOTEL, AutomationScope.inventoryOf(rt));
    }

    @Test
    void dailyRunContinuesAfterOneHotelFails() {
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        when(inventoryRuleRepository.findHotelIdsWithActiveRules()).thenReturn(List.of(bad, good));
        when(rateRuleRepository.findHotelIdsWithActiveRules()).thenReturn(List.of(good));
        doThrow(new IllegalStateException("boom")).when(generator).apply(bad, AutomationScope.ALL);
        when(generator.apply(good, AutomationScope.ALL)).thenReturn(new AutomationSummary(3, 0, 0, 0, 0, 3, 0, 0));

        AutomationSummary total = service.applyAllHotels();

        assertThat(total.created()).isEqualTo(3);
        verify(generator).apply(good, AutomationScope.ALL);
    }
}
