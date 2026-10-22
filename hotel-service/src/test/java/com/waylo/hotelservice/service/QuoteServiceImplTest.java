package com.waylo.hotelservice.service;

import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.MealPlan;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.QuoteCreateRequest;
import com.waylo.hotelservice.dto.QuoteResponse;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelQuoteRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
class QuoteServiceImplTest {

    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private HotelRoomTypeRepository roomTypeRepository;
    @Mock
    private RatePlanRepository ratePlanRepository;
    @Mock
    private DailyRateRepository dailyRateRepository;
    @Mock
    private DailyInventoryRepository dailyInventoryRepository;
    @Mock
    private HotelQuoteRepository hotelQuoteRepository;
    @Mock
    private AuthAccessClient authAccessClient;
    @Mock
    private Clock clock;

    @Captor
    private ArgumentCaptor<com.waylo.hotelservice.domain.HotelQuote> quoteCaptor;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID roomTypeId = UUID.randomUUID();
    private final UUID ratePlanId = UUID.randomUUID();
    private final UUID principalUserId = UUID.randomUUID();
    private final LocalDate checkIn = LocalDate.of(2026, 12, 1);
    private final LocalDate checkOut = LocalDate.of(2026, 12, 3);
    private final Instant now = Instant.parse("2026-10-01T00:00:00Z");

    private QuoteServiceImpl service;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneId.systemDefault());
        service = new QuoteServiceImpl(
                hotelRepository, roomTypeRepository, ratePlanRepository,
                dailyRateRepository, dailyInventoryRepository, hotelQuoteRepository,
                authAccessClient, clock);
    }

    // ===== Helper builders =====

    private Hotel buildHotel(boolean active) {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setName("Test Hotel");
        hotel.setCity("Istanbul");
        hotel.setCountry("Turkey");
        hotel.setActive(active);
        hotel.setOrganizationId(orgId);
        return hotel;
    }

    private HotelRoomType buildRoomType(int maxAdults, int maxOccupancy, int maxChildren) {
        HotelRoomType rt = new HotelRoomType();
        rt.setId(roomTypeId);
        rt.setHotelId(hotelId);
        rt.setCode("DT");
        rt.setName("Double Room");
        rt.setMaxAdults(maxAdults);
        rt.setMaxOccupancy(maxOccupancy);
        rt.setMaxChildren(maxChildren);
        rt.setStatus(CatalogStatus.ACTIVE);
        return rt;
    }

    private RatePlan buildRatePlan() {
        RatePlan rp = new RatePlan();
        rp.setId(ratePlanId);
        rp.setHotelId(hotelId);
        rp.setCode("RK");
        rp.setName("Rate Plan");
        rp.setMealPlan(MealPlan.BREAKFAST);
        rp.setCancellationPolicy(com.waylo.hotelservice.domain.CancellationPolicy.FREE_CANCELLATION);
        rp.setFreeCancellationDaysBefore(1);
        rp.setStatus(CatalogStatus.ACTIVE);
        return rp;
    }

    private QuoteCreateRequest buildRequest(int rooms, int adults, List<Integer> childrenAges) {
        return new QuoteCreateRequest(hotelId, roomTypeId, ratePlanId,
                checkIn, checkOut, rooms, adults, childrenAges);
    }

    private DailyRate buildRate(LocalDate date, int occupancy, BigDecimal amount) {
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

    private DailyInventory buildInventory(LocalDate date, int allotment) {
        return buildInventory(date, allotment, false);
    }

    private DailyInventory buildInventory(LocalDate date, int allotment, boolean stopSell) {
        DailyInventory di = new DailyInventory();
        di.setHotelId(hotelId);
        di.setRoomTypeId(roomTypeId);
        di.setStayDate(date);
        di.setAllotment(allotment);
        di.setStopSell(stopSell);
        return di;
    }

    private static final List<DailyInventory> EMPTY_LIST = List.of();

    // ===== Bug-fix tests: organization ACTIVE check (fail closed) =====

    @Test
    void suspendedOrg_throwsHotelNotBookable() {
        Hotel hotel = buildHotel(true);
        UUID inactiveOrgId = UUID.randomUUID();
        hotel.setOrganizationId(inactiveOrgId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of()), principalUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("organization is not active")
                .extracting("ruleCode").isEqualTo("HOTEL_NOT_BOOKABLE");
    }

    @Test
    void nullOrg_throwsHotelNotBookable() {
        Hotel hotel = buildHotel(true);
        hotel.setOrganizationId(null);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of()), principalUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("organization is not active")
                .extracting("ruleCode").isEqualTo("HOTEL_NOT_BOOKABLE");
    }

    @Test
    void authClientThrows_failsClosed() {
        Hotel hotel = buildHotel(true);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(authAccessClient.activeOrganizationIds()).thenThrow(new RuntimeException("auth-service down"));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of()), principalUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Authorization service unavailable")
                .extracting("ruleCode").isEqualTo("HOTEL_NOT_BOOKABLE");
    }

    // ===== Bug-fix tests: children ages validation =====

    @Test
    void childrenAgesOutOfRange_throws() {
        Hotel hotel = buildHotel(true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of(18)), principalUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 17");

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of(-1)), principalUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 17");
    }

    @Test
    void tooManyChildrenAges_throws() {
        Hotel hotel = buildHotel(true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        List<Integer> tooMany = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 1, tooMany), principalUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Up to 10 children ages");
    }

    @Test
    void childrenExceedsRoomMax_throwsOccupancyExceeded() {
        Hotel hotel = buildHotel(true);
        HotelRoomType rt = buildRoomType(2, 4, 1);
        RatePlan rp = buildRatePlan();
        LocalDate lastNight = checkOut.minusDays(1);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(rt));
        when(ratePlanRepository.findById(ratePlanId)).thenReturn(Optional.of(rp));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(
                eq(hotelId), eq(roomTypeId), eq(checkIn), eq(lastNight)))
                .thenReturn(List.of(
                        buildInventory(checkIn, 5),
                        buildInventory(lastNight, 5)));
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of(5, 6)), principalUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Children count (2) exceeds room max (1)")
                .extracting("ruleCode").isEqualTo("OCCUPANCY_EXCEEDED");
    }

    // ===== Bug-fix tests: inventory check =====

    @Test
    void missingInventoryNight_throwsInventoryUnavailable() {
        Hotel hotel = buildHotel(true);
        HotelRoomType rt = buildRoomType(2, 4, 2);
        RatePlan rp = buildRatePlan();

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(rt));
        when(ratePlanRepository.findById(ratePlanId)).thenReturn(Optional.of(rp));

        LocalDate lastNight = checkOut.minusDays(1);
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(
                eq(hotelId), eq(roomTypeId), eq(checkIn), eq(lastNight)))
                .thenReturn(List.of(
                        buildInventory(checkIn, 5)));
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of()), principalUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No inventory record found")
                .extracting("ruleCode").isEqualTo("INVENTORY_UNAVAILABLE");
    }

    @Test
    void stopSellNight_throwsInventoryUnavailable() {
        Hotel hotel = buildHotel(true);
        HotelRoomType rt = buildRoomType(2, 4, 2);
        RatePlan rp = buildRatePlan();
        LocalDate lastNight = checkOut.minusDays(1);

        List<DailyInventory> inventories = List.of(
                buildInventory(checkIn, 5),
                buildInventory(lastNight, 5, true));

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(rt));
        when(ratePlanRepository.findById(ratePlanId)).thenReturn(Optional.of(rp));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(
                eq(hotelId), eq(roomTypeId), eq(checkIn), eq(lastNight)))
                .thenReturn(inventories);
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of()), principalUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("stop-sold")
                .extracting("ruleCode").isEqualTo("INVENTORY_UNAVAILABLE");
    }

    @Test
    void insufficientAvailableRooms_throwsInventoryUnavailable() {
        Hotel hotel = buildHotel(true);
        HotelRoomType rt = buildRoomType(2, 4, 2);
        RatePlan rp = buildRatePlan();
        LocalDate lastNight = checkOut.minusDays(1);

        // allotment=0 means getAvailable() = 0 - 0 - 0 - 0 = 0 < 1 requested room
        List<DailyInventory> inventories = List.of(
                buildInventory(checkIn, 5),
                buildInventory(lastNight, 0));

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(rt));
        when(ratePlanRepository.findById(ratePlanId)).thenReturn(Optional.of(rp));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(
                eq(hotelId), eq(roomTypeId), eq(checkIn), eq(lastNight)))
                .thenReturn(inventories);
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of()), principalUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient inventory")
                .extracting("ruleCode").isEqualTo("INVENTORY_UNAVAILABLE");
    }

    // ===== Bug-fix tests: rooms and adults limits =====

    @Test
    void roomsOutOfRange_throws() {
        Hotel hotel = buildHotel(true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.createQuote(buildRequest(0, 1, List.of()), principalUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rooms must be between 1 and 10");

        assertThatThrownBy(() -> service.createQuote(buildRequest(11, 1, List.of()), principalUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rooms must be between 1 and 10");
    }

    @Test
    void adultsOutOfRange_throws() {
        Hotel hotel = buildHotel(true);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 0, List.of()), principalUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adults must be between 1 and 20");

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 21, List.of()), principalUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adults must be between 1 and 20");
    }

    // ===== Happy path tests =====

    @Test
    void happyPath_computesTotalAndSetsPrincipalUserId() {
        Hotel hotel = buildHotel(true);
        HotelRoomType rt = buildRoomType(2, 4, 2);
        RatePlan rp = buildRatePlan();

        LocalDate lastNight = checkOut.minusDays(1);
        int occupancy = 2;
        BigDecimal nightlyRate = BigDecimal.valueOf(100);

        List<DailyInventory> inventories = List.of(
                buildInventory(checkIn, 5),
                buildInventory(lastNight, 5));
        List<DailyRate> rates = List.of(
                buildRate(checkIn, occupancy, nightlyRate),
                buildRate(lastNight, occupancy, nightlyRate));

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(rt));
        when(ratePlanRepository.findById(ratePlanId)).thenReturn(Optional.of(rp));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(
                eq(hotelId), eq(roomTypeId), eq(checkIn), eq(lastNight)))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(
                eq(hotelId), eq(checkIn), eq(lastNight), eq(roomTypeId), eq(ratePlanId)))
                .thenReturn(rates);
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));
        when(hotelQuoteRepository.save(any())).thenAnswer(invocation -> {
            com.waylo.hotelservice.domain.HotelQuote q = invocation.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        QuoteCreateRequest request = buildRequest(1, 2, List.of());
        QuoteResponse response = service.createQuote(request, principalUserId);

        // total = sum(nightly rates) * rooms = (100 + 100) * 1 = 200
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(200));
        assertThat(response.rooms()).isEqualTo(1);
        assertThat(response.adults()).isEqualTo(2);
        assertThat(response.nights()).isEqualTo(2);

        verify(hotelQuoteRepository).save(quoteCaptor.capture());
        com.waylo.hotelservice.domain.HotelQuote savedQuote = quoteCaptor.getValue();
        assertThat(savedQuote.getPrincipalUserId()).isEqualTo(principalUserId);
    }

    @Test
    void happyPathWithChildren_computesCorrectOccupancy() {
        Hotel hotel = buildHotel(true);
        HotelRoomType rt = buildRoomType(2, 4, 3);
        RatePlan rp = buildRatePlan();

        LocalDate lastNight = checkOut.minusDays(1);
        int occupancy = 4;
        BigDecimal nightlyRate = BigDecimal.valueOf(150);

        List<DailyInventory> inventories = List.of(
                buildInventory(checkIn, 5),
                buildInventory(lastNight, 5));
        List<DailyRate> rates = List.of(
                buildRate(checkIn, occupancy, nightlyRate),
                buildRate(lastNight, occupancy, nightlyRate));

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(rt));
        when(ratePlanRepository.findById(ratePlanId)).thenReturn(Optional.of(rp));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(
                eq(hotelId), eq(roomTypeId), eq(checkIn), eq(lastNight)))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(
                eq(hotelId), eq(checkIn), eq(lastNight), eq(roomTypeId), eq(ratePlanId)))
                .thenReturn(rates);
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));
        when(hotelQuoteRepository.save(any())).thenAnswer(invocation -> {
            com.waylo.hotelservice.domain.HotelQuote q = invocation.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        QuoteCreateRequest request = buildRequest(1, 2, List.of(5, 6));
        QuoteResponse response = service.createQuote(request, principalUserId);

        // total = (150 + 150) * 1 = 300
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(300));
        assertThat(response.occupancy()).isEqualTo(4);
        assertThat(response.childrenAges()).isEqualTo(List.of(5, 6));
    }

    @Test
    void happyPathMultiRoom_computesCorrectTotal() {
        Hotel hotel = buildHotel(true);
        HotelRoomType rt = buildRoomType(2, 4, 2);
        RatePlan rp = buildRatePlan();

        LocalDate lastNight = checkOut.minusDays(1);
        int occupancy = 2;
        BigDecimal nightlyRate = BigDecimal.valueOf(100);

        List<DailyInventory> inventories = List.of(
                buildInventory(checkIn, 10),
                buildInventory(lastNight, 10));
        List<DailyRate> rates = List.of(
                buildRate(checkIn, occupancy, nightlyRate),
                buildRate(lastNight, occupancy, nightlyRate));

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));
        when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(rt));
        when(ratePlanRepository.findById(ratePlanId)).thenReturn(Optional.of(rp));
        when(dailyInventoryRepository.findByHotelIdAndRoomTypeIdAndStayDateBetween(
                eq(hotelId), eq(roomTypeId), eq(checkIn), eq(lastNight)))
                .thenReturn(inventories);
        when(dailyRateRepository.findByHotelIdAndDateRange(
                eq(hotelId), eq(checkIn), eq(lastNight), eq(roomTypeId), eq(ratePlanId)))
                .thenReturn(rates);
        when(authAccessClient.activeOrganizationIds()).thenReturn(List.of(orgId));
        when(hotelQuoteRepository.save(any())).thenAnswer(invocation -> {
            com.waylo.hotelservice.domain.HotelQuote q = invocation.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        QuoteCreateRequest request = buildRequest(3, 2, List.of());
        QuoteResponse response = service.createQuote(request, principalUserId);

        // total = (100 + 100) * 3 = 600
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(response.rooms()).isEqualTo(3);
    }

    // ===== Edge case tests =====

    @Test
    void hotelNotFound_throws() {
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of()), principalUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel");
    }

    @Test
    void inactiveHotel_throws() {
        Hotel hotel = buildHotel(false);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(hotel));

        assertThatThrownBy(() -> service.createQuote(buildRequest(1, 2, List.of()), principalUserId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Hotel is not bookable")
                .extracting("ruleCode").isEqualTo("HOTEL_NOT_BOOKABLE");
    }
}
