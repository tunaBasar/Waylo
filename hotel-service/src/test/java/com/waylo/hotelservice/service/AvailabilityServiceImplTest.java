package com.waylo.hotelservice.service;

import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.AvailabilityResponse;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityServiceImplTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private HotelRoomTypeRepository roomTypeRepository;

    @Mock
    private RatePlanRepository ratePlanRepository;

    @Mock
    private DailyInventoryRepository dailyInventoryRepository;

    @Mock
    private DailyRateRepository dailyRateRepository;

    @Mock
    private AuthAccessClient authAccessClient;

    @Mock
    private Clock clock;

    @Captor
    private ArgumentCaptor<LocalDate> dateCaptor;

    @InjectMocks
    private AvailabilityServiceImpl availabilityService;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID roomTypeId = UUID.randomUUID();
    private final UUID ratePlanId = UUID.randomUUID();
    private final LocalDate checkIn = LocalDate.of(2026, 12, 1);
    private final LocalDate checkOut = LocalDate.of(2026, 12, 3);
    private final Instant now = Instant.parse("2026-10-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        lenient().when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));
    }

    private Hotel buildHotel(UUID id, boolean active) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName("Test Hotel");
        hotel.setCity("Istanbul");
        hotel.setCountry("Turkey");
        hotel.setAddress("123 Main St");
        hotel.setStarRating(4);
        hotel.setDescription("Test");
        hotel.setActive(active);
        hotel.setOrganizationId(orgId);
        hotel.setLatitude(BigDecimal.valueOf(41.0));
        hotel.setLongitude(BigDecimal.valueOf(29.0));
        return hotel;
    }

    private HotelRoomType buildRoomType(UUID id, int maxAdults, int maxOccupancy, int maxChildren) {
        HotelRoomType rt = new HotelRoomType();
        rt.setId(id);
        rt.setHotelId(hotelId);
        rt.setCode("DT");
        rt.setName("Double Room");
        rt.setMaxAdults(maxAdults);
        rt.setMaxOccupancy(maxOccupancy);
        rt.setMaxChildren(maxChildren);
        rt.setStatus(CatalogStatus.ACTIVE);
        return rt;
    }

    private RatePlan buildRatePlan(UUID id) {
        RatePlan rp = new RatePlan();
        rp.setId(id);
        rp.setHotelId(hotelId);
        rp.setCode("RK");
        rp.setName("Rate Plan");
        rp.setMealPlan(com.waylo.hotelservice.domain.MealPlan.BREAKFAST);
        rp.setCancellationPolicy(com.waylo.hotelservice.domain.CancellationPolicy.FREE_CANCELLATION);
        rp.setFreeCancellationDaysBefore(1);
        rp.setStatus(CatalogStatus.ACTIVE);
        return rp;
    }

    private DailyRate buildDailyRate(UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                     LocalDate date, int occupancy, BigDecimal amount) {
        DailyRate dr = new DailyRate();
        dr.setHotelId(hotelId);
        dr.setRoomTypeId(roomTypeId);
        dr.setRatePlanId(ratePlanId);
        dr.setStayDate(date);
        dr.setOccupancy(occupancy);
        dr.setAmount(amount);
        dr.setCurrency("USD");
        return dr;
    }

    private DailyInventory buildDailyInventory(UUID hotelId, UUID roomTypeId, LocalDate date,
                                                int available, boolean stopSell) {
        DailyInventory di = new DailyInventory();
        di.setHotelId(hotelId);
        di.setRoomTypeId(roomTypeId);
        di.setStayDate(date);
        di.setAllotment(available);
        di.setStopSell(stopSell);
        return di;
    }

    private DailyRate buildDailyRateWithCurrency(UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                                 LocalDate date, int occupancy, BigDecimal amount, String currency) {
        DailyRate dr = new DailyRate();
        dr.setHotelId(hotelId);
        dr.setRoomTypeId(roomTypeId);
        dr.setRatePlanId(ratePlanId);
        dr.setStayDate(date);
        dr.setOccupancy(occupancy);
        dr.setAmount(amount);
        dr.setCurrency(currency);
        return dr;
    }

    // === Regression tests for root-flagged bugs ===

    @Test
    void inventoryQueryUsesCorrectMethodNotWithNull() {
        // Regression: must call findByHotelIdAndStayDateBetween (not findByHotelIdAndRoomTypeIdAndStayDateBetween with null)
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        // Verify the correct repo method was called (not the broken one with null roomTypeId)
        verify(dailyInventoryRepository).findByHotelIdAndStayDateBetween(eq(hotelId), eq(checkIn), eq(lastNight));
        // Verify the broken method was NOT called
        verify(dailyInventoryRepository, never())
                .findByHotelIdAndRoomTypeIdAndStayDateBetween(any(), isNull(), any(), any());
    }

    @Test
    void inventoryQueryActualDataReturned() {
        // Regression: verify actual repo data is used (not empty results from broken null query)
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        List<DailyInventory> inventories = List.of(
                buildDailyInventory(hotelId, roomTypeId, checkIn, 5, false),
                buildDailyInventory(hotelId, roomTypeId, lastNight, 5, false)
        );
        List<DailyRate> rates = List.of(
                buildDailyRate(hotelId, roomTypeId, ratePlanId, checkIn, 2, BigDecimal.valueOf(100)),
                buildDailyRate(hotelId, roomTypeId, ratePlanId, lastNight, 2, BigDecimal.valueOf(100))
        );

        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(rates);

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).hasSize(1);
        assertThat(response.offers().get(0).totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
    }

    @Test
    void childrenAgeExceedsMaxChildren_skipsRoomType() {
        // Regression: must check ages.size() > maxChildren, not just adults/total occupancy
        Hotel hotel = buildHotel(hotelId, true);
        // Room allows max 1 child but request has 2 children
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 1);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of(5, 6));

        // Should have zero offers because children (2) > maxChildren (1)
        assertThat(response.offers()).isEmpty();
    }

    @Test
    void mixedCurrencyMarksOfferNotSellable() {
        // Regression: mixed currency should mark sellable=false, not throw
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        List<DailyInventory> inventories = List.of(
                buildDailyInventory(hotelId, roomTypeId, checkIn, 5, false),
                buildDailyInventory(hotelId, roomTypeId, lastNight, 5, false)
        );
        List<DailyRate> rates = List.of(
                buildDailyRate(hotelId, roomTypeId, ratePlanId, checkIn, 2, BigDecimal.valueOf(100)),
                buildDailyRateWithCurrency(hotelId, roomTypeId, ratePlanId, lastNight, 2, BigDecimal.valueOf(110), "EUR")
        );

        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(rates);

        // Should NOT throw on mixed currency - should return empty offers
        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).isEmpty();
    }

    @Test
    void nullOrganizationReturnsEmptyOffers() {
        // Regression: null-org legacy should return empty offers, not throw
        Hotel hotel = buildHotel(hotelId, true);
        hotel.setOrganizationId(null);

        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        List<DailyInventory> inventories = List.of(
                buildDailyInventory(hotelId, roomTypeId, checkIn, 5, false),
                buildDailyInventory(hotelId, roomTypeId, lastNight, 5, false)
        );
        List<DailyRate> rates = List.of(
                buildDailyRate(hotelId, roomTypeId, ratePlanId, checkIn, 2, BigDecimal.valueOf(100)),
                buildDailyRate(hotelId, roomTypeId, ratePlanId, lastNight, 2, BigDecimal.valueOf(100))
        );

        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(rates);

        // Should NOT throw for null org - should return empty offers
        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).isEmpty();
        // Auth client should never be called for null org
        verify(authAccessClient, never()).activeOrganizationIds();
    }

    @Test
    void inactiveHotel_throws() {
        Hotel hotel = buildHotel(hotelId, false);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Hotel is not bookable");
    }

    @Test
    void hotelNotFound_throws() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel");
    }

    @Test
    void checkInInPast_throws() {
        Hotel hotel = buildHotel(hotelId, true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2), 1, 1, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("checkIn cannot be in the past");
    }

    @Test
    void occupancyExceedsMaxOccupancy_skipsRoomType() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 2, 5); // max occupancy 2
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).isEmpty();
    }

    @Test
    void adultsExceedsMaxAdults_skipsRoomType() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 1, 4, 3); // max adults 1
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).isEmpty();
    }

    @Test
    void stopSellOnAnyNight_marksNotSellable() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        List<DailyInventory> inventories = List.of(
                buildDailyInventory(hotelId, roomTypeId, checkIn, 5, false),
                buildDailyInventory(hotelId, roomTypeId, lastNight, 5, true) // stopSell on last night
        );
        List<DailyRate> rates = List.of(
                buildDailyRate(hotelId, roomTypeId, ratePlanId, checkIn, 2, BigDecimal.valueOf(100)),
                buildDailyRate(hotelId, roomTypeId, ratePlanId, lastNight, 2, BigDecimal.valueOf(100))
        );

        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(rates);

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).isEmpty();
    }

    @Test
    void insufficientInventory_marksNotSellable() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        List<DailyInventory> inventories = List.of(
                buildDailyInventory(hotelId, roomTypeId, checkIn, 0, false), // 0 available
                buildDailyInventory(hotelId, roomTypeId, lastNight, 5, false)
        );
        List<DailyRate> rates = List.of(
                buildDailyRate(hotelId, roomTypeId, ratePlanId, checkIn, 2, BigDecimal.valueOf(100)),
                buildDailyRate(hotelId, roomTypeId, ratePlanId, lastNight, 2, BigDecimal.valueOf(100))
        );

        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(rates);

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).isEmpty();
    }

    @Test
    void noRateOnAnyNight_marksNotSellable() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        List<DailyInventory> inventories = List.of(
                buildDailyInventory(hotelId, roomTypeId, checkIn, 5, false),
                buildDailyInventory(hotelId, roomTypeId, lastNight, 5, false)
        );
        // No rates returned
        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(List.of());

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).isEmpty();
    }

    @Test
    void validRequest_returnsOffer() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        List<DailyInventory> inventories = List.of(
                buildDailyInventory(hotelId, roomTypeId, checkIn, 5, false),
                buildDailyInventory(hotelId, roomTypeId, lastNight, 5, false)
        );
        List<DailyRate> rates = List.of(
                buildDailyRate(hotelId, roomTypeId, ratePlanId, checkIn, 2, BigDecimal.valueOf(100)),
                buildDailyRate(hotelId, roomTypeId, ratePlanId, lastNight, 2, BigDecimal.valueOf(100))
        );

        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(rates);

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).hasSize(1);
        assertThat(response.offers().get(0).totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(response.offers().get(0).nights()).isEqualTo(2);
        assertThat(response.offers().get(0).rooms()).isEqualTo(1);
        assertThat(response.offers().get(0).occupancy()).isEqualTo(2);
        assertThat(response.offers().get(0).currency()).isEqualTo("USD");
    }

    @Test
    void multipleRoomTypes_returnsAllMatching() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt1 = buildRoomType(UUID.randomUUID(), 2, 4, 2);
        HotelRoomType rt2 = buildRoomType(UUID.randomUUID(), 4, 6, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt1, rt2));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(List.of(
                        buildDailyInventory(hotelId, rt1.getId(), checkIn, 5, false),
                        buildDailyInventory(hotelId, rt1.getId(), lastNight, 5, false),
                        buildDailyInventory(hotelId, rt2.getId(), checkIn, 5, false),
                        buildDailyInventory(hotelId, rt2.getId(), lastNight, 5, false)
                ));
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(List.of(
                        buildDailyRate(hotelId, rt1.getId(), ratePlanId, checkIn, 2, BigDecimal.valueOf(100)),
                        buildDailyRate(hotelId, rt1.getId(), ratePlanId, lastNight, 2, BigDecimal.valueOf(100)),
                        buildDailyRate(hotelId, rt2.getId(), ratePlanId, checkIn, 2, BigDecimal.valueOf(120)),
                        buildDailyRate(hotelId, rt2.getId(), ratePlanId, lastNight, 2, BigDecimal.valueOf(120))
                ));

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of());

        assertThat(response.offers()).hasSize(2);
    }

    @Test
    void roomsGreaterThan1_multipliesTotal() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 4, 2);
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(List.of(
                        buildDailyInventory(hotelId, roomTypeId, checkIn, 5, false),
                        buildDailyInventory(hotelId, roomTypeId, lastNight, 5, false)
                ));
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(List.of(
                        buildDailyRate(hotelId, roomTypeId, ratePlanId, checkIn, 2, BigDecimal.valueOf(100)),
                        buildDailyRate(hotelId, roomTypeId, ratePlanId, lastNight, 2, BigDecimal.valueOf(100))
                ));

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 3, 2, List.of());

        // 3 rooms * 2 nights * 100 = 600
        assertThat(response.offers()).hasSize(1);
        assertThat(response.offers().get(0).totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(response.offers().get(0).rooms()).isEqualTo(3);
    }

    @Test
    void childrenAgesIncludedInOccupancy() {
        Hotel hotel = buildHotel(hotelId, true);
        HotelRoomType rt = buildRoomType(roomTypeId, 2, 5, 3); // maxOccupancy 5
        RatePlan rp = buildRatePlan(ratePlanId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rt));
        when(ratePlanRepository.findByHotelIdAndStatusOrderByNameAsc(eq(hotelId), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        // Rate for occupancy 4 (2 adults + 2 children)
        List<DailyRate> rates = List.of(
                buildDailyRate(hotelId, roomTypeId, ratePlanId, checkIn, 4, BigDecimal.valueOf(150)),
                buildDailyRate(hotelId, roomTypeId, ratePlanId, lastNight, 4, BigDecimal.valueOf(150))
        );

        when(dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight))
                .thenReturn(List.of(
                        buildDailyInventory(hotelId, roomTypeId, checkIn, 5, false),
                        buildDailyInventory(hotelId, roomTypeId, lastNight, 5, false)
                ));
        when(dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null))
                .thenReturn(rates);

        AvailabilityResponse response = availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 2, List.of(5, 6));

        assertThat(response.offers()).hasSize(1);
        assertThat(response.offers().get(0).occupancy()).isEqualTo(4); // 2 adults + 2 children
        assertThat(response.offers().get(0).totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300)); // 150 * 2 nights
    }

    @Test
    void inactiveOrg_throws() {
        Hotel hotel = buildHotel(hotelId, true);
        UUID inactiveOrgId = UUID.randomUUID();
        hotel.setOrganizationId(inactiveOrgId);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId)); // inactiveOrgId NOT included - triggers "not active"

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 1, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("organization is not active");
    }

    @Test
    void authClientUnavailable_throws() {
        Hotel hotel = buildHotel(hotelId, true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(authAccessClient.activeOrganizationIds()).thenThrow(new RuntimeException("auth-service down"));

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 1, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Authorization service unavailable");
    }

    @Test
    void invalidRooms_throws() {
        Hotel hotel = buildHotel(hotelId, true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkIn, checkOut, 0, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rooms must be between 1 and 10");
    }

    @Test
    void invalidAdults_throws() {
        Hotel hotel = buildHotel(hotelId, true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adults must be between 1 and 20");
    }

    @Test
    void invalidChildrenAge_throws() {
        Hotel hotel = buildHotel(hotelId, true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkIn, checkOut, 1, 1, List.of(18)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 17");
    }

    @Test
    void checkOutNotAfterCheckIn_throws() {
        Hotel hotel = buildHotel(hotelId, true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkOut, checkIn, 1, 1, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("checkOut must be after checkIn");
    }

    @Test
    void nightsExceed30_throws() {
        Hotel hotel = buildHotel(hotelId, true);
        LocalDate farFuture = LocalDate.of(2027, 1, 5); // 35 nights from checkIn
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> availabilityService.getAvailability(hotelId, checkIn, farFuture, 1, 1, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("30 nights");
    }
}
