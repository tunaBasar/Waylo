package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.DailyRateResponse;
import com.waylo.hotelservice.dto.RateUpdateItem;
import com.waylo.hotelservice.dto.RateUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateServiceImplTest {

    private static final UUID HOTEL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROOM_TYPE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RATE_PLAN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 10, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private DailyRateRepository dailyRateRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private HotelRoomTypeRepository roomTypeRepository;
    @Mock
    private RatePlanRepository ratePlanRepository;

    private RateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RateServiceImpl(dailyRateRepository, hotelRepository, roomTypeRepository, ratePlanRepository, FIXED_CLOCK);
    }

    // --- GET rates ---

    @Test
    void getRates_validRange_returnsEmptyList() {
        LocalDate from = FIXED_DATE.minusDays(1);
        LocalDate to = FIXED_DATE.plusDays(30);
        when(hotelRepository.existsById(HOTEL_ID)).thenReturn(true);
        when(dailyRateRepository.findByHotelIdAndDateRange(HOTEL_ID, from, to, null, null))
                .thenReturn(List.of());

        List<DailyRateResponse> result = service.getRates(HOTEL_ID, from, to, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getRates_unknownHotel_throws404() {
        LocalDate from = FIXED_DATE.minusDays(1);
        LocalDate to = FIXED_DATE.plusDays(30);
        when(hotelRepository.existsById(HOTEL_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getRates(HOTEL_ID, from, to, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getRates_withRoomTypeId_validatesRoomTypeBelongsToHotel() {
        LocalDate from = FIXED_DATE.minusDays(1);
        LocalDate to = FIXED_DATE.plusDays(30);
        when(hotelRepository.existsById(HOTEL_ID)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(ROOM_TYPE_ID, HOTEL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRates(HOTEL_ID, from, to, ROOM_TYPE_ID, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room type");

        verify(dailyRateRepository, never()).findByHotelIdAndDateRange(any(), any(), any(), any(), any());
    }

    @Test
    void getRates_withRatePlanId_validatesRatePlanBelongsToHotel() {
        LocalDate from = FIXED_DATE.minusDays(1);
        LocalDate to = FIXED_DATE.plusDays(30);
        when(hotelRepository.existsById(HOTEL_ID)).thenReturn(true);
        when(ratePlanRepository.findByIdAndHotelId(RATE_PLAN_ID, HOTEL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRates(HOTEL_ID, from, to, null, RATE_PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Rate plan");

        verify(dailyRateRepository, never()).findByHotelIdAndDateRange(any(), any(), any(), any(), any());
    }

    @Test
    void getRates_fromAfterTo_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getRates(HOTEL_ID,
                        FIXED_DATE.plusDays(10), FIXED_DATE, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after");
    }

    @Test
    void getRates_rangeExceeds366Days_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getRates(HOTEL_ID,
                        FIXED_DATE.minusDays(1), FIXED_DATE.plusDays(367), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
    }

    @Test
    void getRates_noDates_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getRates(HOTEL_ID, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    // --- UPDATE rates ---

    @Test
    void updateRates_hotelNotFound_throws404() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.empty());

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD", null);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRates_duplicateKeyInRequest_throwsIllegalArgumentException() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));

        RateUpdateItem item1 = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD", null);
        RateUpdateItem item2 = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("150.00"), "USD", null);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item1, item2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate rate entry");
    }

    @Test
    void updateRates_roomTypeNotInHotel_throwsResourceNotFound() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD", null);
        when(roomTypeRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room type");
    }

    @Test
    void updateRates_ratePlanNotInHotel_throwsResourceNotFound() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID,
                        new RateUpdateRequest(List.of(
                                new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                                        FIXED_DATE, 2, new BigDecimal("100.00"), "USD", null)))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Rate plan");
    }

    @Test
    void updateRates_occupancyExceedsMaxOccupancy_throwsBusinessRule() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 2)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 5, new BigDecimal("100.00"), "USD", null);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode")
                .isEqualTo("OCCUPANCY_RULE");
    }

    @Test
    void updateRates_stayDateOutOfRange_throwsBusinessRule() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));

        LocalDate farFuture = FIXED_DATE.plusYears(3);
        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                farFuture, 2, new BigDecimal("100.00"), "USD", null);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode")
                .isEqualTo("STAY_DATE_OUT_OF_RANGE");
    }

    @Test
    void updateRates_mixedCurrenciesInRequest_throwsBusinessRule() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));

        RateUpdateItem item1 = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD", null);
        RateUpdateItem item2 = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE.plusDays(1), 2, new BigDecimal("100.00"), "EUR", null);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID,
                        new RateUpdateRequest(List.of(item1, item2))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode")
                .isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void updateRates_currencyDiffersFromHotel_throwsBusinessRule() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of("EUR"));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD", null);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode")
                .isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void updateRates_currencyDiffersFromSecondHotelCurrency_throwsBusinessRule() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of("USD", "GBP"));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD", null);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode")
                .isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void updateRates_valid_newRow_created() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of());
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        DailyRate saved = createDailyRate(UUID.randomUUID(), HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD");
        when(dailyRateRepository.saveAllAndFlush(any()))
                .thenReturn(List.of(saved));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD", null);

        List<DailyRateResponse> responses = service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item)));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).roomTypeId()).isEqualTo(ROOM_TYPE_ID);
        assertThat(responses.get(0).amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(dailyRateRepository).saveAllAndFlush(any());
    }

    @Test
    void updateRates_existingRow_updated_keepsId() {
        UUID existingId = UUID.randomUUID();
        DailyRate existing = createDailyRate(existingId, HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("80.00"), "USD", 0L);

        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of("USD"));
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(existing));
        when(dailyRateRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("150.00"), "USD", null);

        List<DailyRateResponse> responses = service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item)));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(existingId);
        assertThat(responses.get(0).amount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(responses.get(0).version()).isEqualTo(0L);
    }

    // --- Optimistic locking tests ---

    @Test
    void updateRates_expectedVersionForNewRow_throwsOptimisticLockFailure() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of());
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("100.00"), "USD", 5L);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item))))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void updateRates_versionMismatchExistingRow_throwsOptimisticLockFailure() {
        UUID existingId = UUID.randomUUID();
        DailyRate existing = createDailyRate(existingId, HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("80.00"), "USD", 3L);

        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of("USD"));
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(existing));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("150.00"), "USD", 1L);

        assertThatThrownBy(() -> service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item))))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(dailyRateRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void updateRates_versionMatches_existingRow_updated() {
        UUID existingId = UUID.randomUUID();
        DailyRate existing = createDailyRate(existingId, HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("80.00"), "USD", 3L);

        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of("USD"));
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(existing));
        when(dailyRateRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("150.00"), "USD", 3L);

        List<DailyRateResponse> responses = service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item)));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(existingId);
        assertThat(responses.get(0).amount()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void updateRates_noOp_update_retainsVersion() {
        UUID existingId = UUID.randomUUID();
        DailyRate existing = createDailyRate(existingId, HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("80.00"), "USD", 3L);

        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of("USD"));
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(existing));
        when(dailyRateRepository.saveAllAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("80.00"), "USD", 3L);

        List<DailyRateResponse> responses = service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item)));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).amount()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    // --- Sequential updates test ---

    @Test
    void updateRates_twoSequentialUpdates_bothSucceed() {
        UUID existingId = UUID.randomUUID();
        DailyRate existing = createDailyRate(existingId, HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("80.00"), "USD", 0L);

        // Pre-configured saved entities with expected versions/amounts
        DailyRate firstSaved = createDailyRate(existingId, HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("150.00"), "USD", 1L);
        DailyRate secondSaved = createDailyRate(existingId, HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("200.00"), "USD", 2L);

        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID))
                .thenReturn(List.of("USD"));

        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(existing));
        when(dailyRateRepository.saveAllAndFlush(any())).thenReturn(List.of(firstSaved));

        RateUpdateItem item1 = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("150.00"), "USD", 0L);

        List<DailyRateResponse> first = service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item1)));

        assertThat(first).hasSize(1);
        assertThat(first.get(0).version()).isEqualTo(1L);

        // Second call: update the existing entity reference, then reset mocks
        existing.setVersion(1L);
        existing.setAmount(new BigDecimal("150.00"));
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of(existing));
        when(dailyRateRepository.saveAllAndFlush(any())).thenReturn(List.of(secondSaved));

        RateUpdateItem item2 = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE, 2, new BigDecimal("200.00"), "USD", 1L);

        List<DailyRateResponse> second = service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item2)));

        assertThat(second).hasSize(1);
        assertThat(second.get(0).version()).isEqualTo(2L);
        assertThat(second.get(0).amount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    // --- Clock.fixed test ---

    @Test
    void updateRates_stayDateOneDayBeforeToday_allowed() {
        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID)).thenReturn(List.of());
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), any(), any())).thenReturn(List.of());
        DailyRate saved = createDailyRate(UUID.randomUUID(), HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE.minusDays(1), 2, new BigDecimal("100.00"), "USD");
        when(dailyRateRepository.saveAllAndFlush(any())).thenReturn(List.of(saved));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                FIXED_DATE.minusDays(1), 2, new BigDecimal("100.00"), "USD", null);

        List<DailyRateResponse> responses = service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item)));

        assertThat(responses).hasSize(1);
    }

    // --- Query range from request dates ---

    @Test
    void updateRates_queryRangeUsesRequestDates() {
        LocalDate requestDate = FIXED_DATE.plusDays(10);

        when(hotelRepository.findByIdForUpdate(HOTEL_ID)).thenReturn(Optional.of(new Hotel()));
        when(roomTypeRepository.findAllById(any()))
                .thenReturn(List.of(createRoomType(ROOM_TYPE_ID, HOTEL_ID, 4)));
        when(ratePlanRepository.findAllById(any()))
                .thenReturn(List.of(createRatePlan(RATE_PLAN_ID, HOTEL_ID)));
        when(dailyRateRepository.findCurrenciesByHotelId(HOTEL_ID)).thenReturn(List.of());
        when(dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                any(), any(), any(), eq(requestDate), eq(requestDate)))
                .thenReturn(List.of());
        DailyRate saved = createDailyRate(UUID.randomUUID(), HOTEL_ID, ROOM_TYPE_ID, RATE_PLAN_ID,
                requestDate, 2, new BigDecimal("100.00"), "USD");
        when(dailyRateRepository.saveAllAndFlush(any())).thenReturn(List.of(saved));

        RateUpdateItem item = new RateUpdateItem(ROOM_TYPE_ID, RATE_PLAN_ID,
                requestDate, 2, new BigDecimal("100.00"), "USD", null);

        service.updateRates(HOTEL_ID, new RateUpdateRequest(List.of(item)));

        verify(dailyRateRepository).findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                eq(HOTEL_ID), any(), any(), eq(requestDate), eq(requestDate));
    }

    private HotelRoomType createRoomType(UUID id, UUID hotelId, int maxOccupancy) {
        HotelRoomType rt = new HotelRoomType();
        rt.setId(id);
        rt.setHotelId(hotelId);
        rt.setMaxOccupancy(maxOccupancy);
        return rt;
    }

    private RatePlan createRatePlan(UUID id, UUID hotelId) {
        RatePlan rp = new RatePlan();
        rp.setId(id);
        rp.setHotelId(hotelId);
        return rp;
    }

    private DailyRate createDailyRate(UUID id, UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                       LocalDate stayDate, int occupancy, BigDecimal amount, String currency) {
        return createDailyRate(id, hotelId, roomTypeId, ratePlanId, stayDate, occupancy, amount, currency, 0L);
    }

    private DailyRate createDailyRate(UUID id, UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                       LocalDate stayDate, int occupancy, BigDecimal amount, String currency, long version) {
        DailyRate dr = new DailyRate();
        dr.setId(id);
        dr.setHotelId(hotelId);
        dr.setRoomTypeId(roomTypeId);
        dr.setRatePlanId(ratePlanId);
        dr.setStayDate(stayDate);
        dr.setOccupancy(occupancy);
        dr.setAmount(amount);
        dr.setCurrency(currency);
        dr.setVersion(version);
        return dr;
    }
}
