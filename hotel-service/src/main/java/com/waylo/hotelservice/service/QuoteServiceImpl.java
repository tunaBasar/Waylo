package com.waylo.hotelservice.service;

import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelQuote;
import com.waylo.hotelservice.domain.HotelQuoteNight;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.MealPlan;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.NightlyRateResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QuoteServiceImpl implements QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteServiceImpl.class);
    private static final int QUOTE_LIFETIME_MINUTES = 15;

    private final HotelRepository hotelRepository;
    private final HotelRoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final DailyRateRepository dailyRateRepository;
    private final DailyInventoryRepository dailyInventoryRepository;
    private final HotelQuoteRepository hotelQuoteRepository;
    private final AuthAccessClient authAccessClient;
    private final Clock clock;

    public QuoteServiceImpl(HotelRepository hotelRepository,
                            HotelRoomTypeRepository roomTypeRepository,
                            RatePlanRepository ratePlanRepository,
                            DailyRateRepository dailyRateRepository,
                            DailyInventoryRepository dailyInventoryRepository,
                            HotelQuoteRepository hotelQuoteRepository,
                            AuthAccessClient authAccessClient,
                            Clock clock) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.dailyRateRepository = dailyRateRepository;
        this.dailyInventoryRepository = dailyInventoryRepository;
        this.hotelQuoteRepository = hotelQuoteRepository;
        this.authAccessClient = authAccessClient;
        this.clock = clock;
    }

    @Override
    @Transactional
    public QuoteResponse createQuote(QuoteCreateRequest request, UUID principalUserId) {
        // 1. Validate stay range
        validateStayRange(request.checkIn(), request.checkOut());

        int rooms = request.rooms();
        int adults = request.adults();
        List<Integer> childrenAges = request.childrenAges() != null ? request.childrenAges() : List.of();

        // 1a. Validate rooms and adults ranges
        if (rooms < 1 || rooms > 10) {
            throw new IllegalArgumentException("rooms must be between 1 and 10");
        }
        if (adults < 1 || adults > 20) {
            throw new IllegalArgumentException("adults must be between 1 and 20");
        }

        // 1b. Validate children ages
        if (childrenAges.size() > 10) {
            throw new IllegalArgumentException("Up to 10 children ages allowed");
        }
        for (Integer age : childrenAges) {
            if (age == null || age < 0 || age > 17) {
                throw new IllegalArgumentException("Each children age must be between 0 and 17");
            }
        }

        int occupancy = adults + childrenAges.size();

        // 2. Fetch hotel
        Hotel hotel = hotelRepository.findById(request.hotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", request.hotelId()));
        if (!hotel.isActive()) {
            throw new BusinessRuleException("HOTEL_NOT_BOOKABLE", "Hotel is not bookable");
        }

        // 3. Check organization is ACTIVE (fail closed)
        checkOrganizationActive(hotel.getOrganizationId());

        // 4. Fetch room type
        HotelRoomType roomType = roomTypeRepository.findById(request.roomTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", request.roomTypeId()));
        if (roomType.getStatus() != CatalogStatus.ACTIVE) {
            throw new BusinessRuleException("ROOM_TYPE_NOT_BOOKABLE", "Room type is not bookable");
        }
        if (roomType.getHotelId() != null && !roomType.getHotelId().equals(request.hotelId())) {
            throw new BusinessRuleException("ROOM_TYPE_MISMATCH", "Room type does not belong to the hotel");
        }

        // Validate room type capacity
        if (adults > roomType.getMaxAdults()) {
            throw new BusinessRuleException("ROOM_CAPACITY_EXCEEDED",
                    "Adults (" + adults + ") exceeds room max (" + roomType.getMaxAdults() + ")");
        }
        if (occupancy > roomType.getMaxOccupancy()) {
            throw new BusinessRuleException("ROOM_CAPACITY_EXCEEDED",
                    "Occupancy (" + occupancy + ") exceeds room max (" + roomType.getMaxOccupancy() + ")");
        }

        // Validate max children
        if (childrenAges.size() > roomType.getMaxChildren()) {
            throw new BusinessRuleException("OCCUPANCY_EXCEEDED",
                    "Children count (" + childrenAges.size() + ") exceeds room max (" + roomType.getMaxChildren() + ")");
        }

        // 5. Fetch rate plan
        RatePlan ratePlan = ratePlanRepository.findById(request.ratePlanId())
                .orElseThrow(() -> new ResourceNotFoundException("RatePlan", "id", request.ratePlanId()));
        if (ratePlan.getStatus() != CatalogStatus.ACTIVE) {
            throw new BusinessRuleException("RATE_PLAN_NOT_BOOKABLE", "Rate plan is not bookable");
        }
        if (ratePlan.getHotelId() != null && !ratePlan.getHotelId().equals(request.hotelId())) {
            throw new BusinessRuleException("RATE_PLAN_MISMATCH", "Rate plan does not belong to the hotel");
        }

        // 6. Inventory check: one row per night, !stopSell and getAvailable() >= rooms
        LocalDate lastNight = request.checkOut().minusDays(1);
        List<DailyInventory> inventories = dailyInventoryRepository
                .findByHotelIdAndRoomTypeIdAndStayDateBetween(request.hotelId(), request.roomTypeId(),
                        request.checkIn(), lastNight);

        Map<LocalDate, DailyInventory> inventoryByDate = new HashMap<>();
        for (DailyInventory di : inventories) {
            inventoryByDate.put(di.getStayDate(), di);
        }

        for (LocalDate date = request.checkIn(); !date.isAfter(lastNight); date = date.plusDays(1)) {
            DailyInventory inv = inventoryByDate.get(date);
            if (inv == null) {
                throw new BusinessRuleException("INVENTORY_UNAVAILABLE",
                        "No inventory record found for room " + request.roomTypeId()
                                + ", date " + date);
            }
            if (inv.isStopSell()) {
                throw new BusinessRuleException("INVENTORY_UNAVAILABLE",
                        "Room type is stop-sold for room " + request.roomTypeId()
                                + ", date " + date);
            }
            if (inv.getAvailable() < rooms) {
                throw new BusinessRuleException("INVENTORY_UNAVAILABLE",
                        "Insufficient inventory for room " + request.roomTypeId()
                                + ", date " + date
                                + ": available=" + inv.getAvailable() + ", requested=" + rooms);
            }
        }

        // 7. Fetch rates for all nights
        List<DailyRate> allRates = dailyRateRepository
                .findByHotelIdAndDateRange(request.hotelId(), request.checkIn(), lastNight,
                        request.roomTypeId(), request.ratePlanId());

        // Index by date
        Map<LocalDate, DailyRate> rateByDate = new HashMap<>();
        for (DailyRate dr : allRates) {
            if (dr.getRoomTypeId().equals(request.roomTypeId())
                    && dr.getRatePlanId().equals(request.ratePlanId())
                    && dr.getOccupancy() == occupancy) {
                rateByDate.put(dr.getStayDate(), dr);
            }
        }

        // 8. Calculate nightly rates and total, verifying determinism
        List<NightlyRateResponse> nightlyRates = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        String currency = null;

        for (LocalDate date = request.checkIn(); !date.isAfter(lastNight); date = date.plusDays(1)) {
            DailyRate dr = rateByDate.get(date);
            if (dr == null) {
                throw new BusinessRuleException("RATE_NOT_FOUND",
                        "No rate found for room " + request.roomTypeId()
                                + ", rate plan " + request.ratePlanId()
                                + ", date " + date
                                + ", occupancy " + occupancy);
            }

            if (currency == null) {
                currency = dr.getCurrency();
            } else if (!currency.equals(dr.getCurrency())) {
                throw new BusinessRuleException("CURRENCY_MISMATCH",
                        "Nights of quote have different currencies");
            }

            nightlyRates.add(new NightlyRateResponse(date, dr.getAmount(), dr.getCurrency()));
            totalAmount = totalAmount.add(dr.getAmount());
        }

        // 9. Multiply total by number of rooms (server computed, never trust client)
        totalAmount = totalAmount.multiply(BigDecimal.valueOf(request.rooms()));

        int nights = (int) ChronoUnit.DAYS.between(request.checkIn(), request.checkOut());

        // 10. Build the quote entity (snapshot contract)
        HotelQuote quote = new HotelQuote();
        quote.setHotelId(request.hotelId());
        quote.setRoomTypeId(request.roomTypeId());
        quote.setRatePlanId(request.ratePlanId());
        quote.setCheckIn(request.checkIn());
        quote.setCheckOut(request.checkOut());
        quote.setRooms(request.rooms());
        quote.setAdults(request.adults());
        quote.setChildrenAges(HotelQuote.encodeChildrenAges(request.childrenAges()));
        quote.setOccupancy(occupancy);
        quote.setCurrency(currency);
        quote.setTotalAmount(totalAmount);
        quote.setMealPlan(ratePlan.getMealPlan());
        quote.setCancellationPolicy(ratePlan.getCancellationPolicy());
        quote.setFreeCancellationDaysBefore(ratePlan.getFreeCancellationDaysBefore());
        quote.setRoomTypeCode(roomType.getCode());
        quote.setRoomTypeName(roomType.getName());
        quote.setRatePlanCode(ratePlan.getCode());
        quote.setRatePlanName(ratePlan.getName());
        quote.setPrincipalUserId(principalUserId);
        quote.setExpiresAt(clock.instant().plus(QUOTE_LIFETIME_MINUTES, ChronoUnit.MINUTES));

        // 11. Add night snapshots
        for (NightlyRateResponse nr : nightlyRates) {
            HotelQuoteNight night = new HotelQuoteNight();
            night.setStayDate(nr.stayDate());
            night.setAmount(nr.amount());
            night.setCurrency(nr.currency());
            quote.addNight(night);
        }

        // 12. Save
        HotelQuote saved = hotelQuoteRepository.save(quote);

        // 13. Convert to response
        return toQuoteResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public QuoteResponse getQuote(UUID quoteId, UUID principalUserId) {
        HotelQuote quote = hotelQuoteRepository.findByIdWithNights(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", "id", quoteId));

        // Ownership check: hide foreign quotes as 404
        if (!quote.getPrincipalUserId().equals(principalUserId)) {
            throw new ResourceNotFoundException("Quote", "id", quoteId);
        }

        return toQuoteResponse(quote);
    }

    private QuoteResponse toQuoteResponse(HotelQuote quote) {
        boolean expired = quote.isExpired(clock);

        List<NightlyRateResponse> nightly = new ArrayList<>();
        if (quote.getNights() != null) {
            for (HotelQuoteNight night : quote.getNights()) {
                nightly.add(new NightlyRateResponse(
                        night.getStayDate(),
                        night.getAmount(),
                        night.getCurrency()));
            }
        }

        List<Integer> childrenAges = HotelQuote.decodeChildrenAges(quote.getChildrenAges());

        return new QuoteResponse(
                quote.getId(),
                quote.getHotelId(),
                quote.getRoomTypeId(),
                quote.getRoomTypeCode(),
                quote.getRoomTypeName(),
                quote.getRatePlanId(),
                quote.getRatePlanCode(),
                quote.getRatePlanName(),
                quote.getMealPlan(),
                quote.getCancellationPolicy(),
                quote.getFreeCancellationDaysBefore(),
                quote.getCheckIn(),
                quote.getCheckOut(),
                quote.getNightCount(),
                quote.getRooms(),
                quote.getAdults(),
                childrenAges,
                quote.getOccupancy(),
                quote.getCurrency(),
                quote.getTotalAmount(),
                nightly,
                quote.getCreatedAt(),
                quote.getExpiresAt(),
                expired);
    }

    private void validateStayRange(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("checkIn and checkOut are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessRuleException("STAY_RANGE_INVALID", "checkOut must be after checkIn");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > 30) {
            throw new BusinessRuleException("STAY_RANGE_INVALID", "Stay cannot exceed 30 nights");
        }
        LocalDate today = LocalDate.now(clock);
        if (checkIn.isBefore(today)) {
            throw new BusinessRuleException("STAY_RANGE_INVALID", "checkIn cannot be in the past");
        }
    }

    private void checkOrganizationActive(UUID organizationId) {
        if (organizationId == null) {
            throw new BusinessRuleException("HOTEL_NOT_BOOKABLE",
                    "Hotel's organization is not active");
        }
        List<UUID> activeOrgs;
        try {
            activeOrgs = authAccessClient.activeOrganizationIds();
        } catch (RuntimeException e) {
            log.warn("AuthAccessClient unavailable for organization check, failing closed: {}", e.getMessage());
            throw new BusinessRuleException("HOTEL_NOT_BOOKABLE",
                    "Authorization service unavailable - organization status cannot be verified");
        }
        if (!activeOrgs.contains(organizationId)) {
            throw new BusinessRuleException("HOTEL_NOT_BOOKABLE",
                    "Hotel's organization is not active");
        }
    }
}
