package com.waylo.hotelservice.service;

import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.DailyInventory;
import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.AvailableOfferResponse;
import com.waylo.hotelservice.dto.AvailabilityResponse;
import com.waylo.hotelservice.dto.NightlyRateResponse;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyInventoryRepository;
import com.waylo.hotelservice.repository.DailyRateRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityServiceImpl.class);
    private static final int MAX_NIGHTS = 30;

    private final HotelRepository hotelRepository;
    private final HotelRoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final DailyInventoryRepository dailyInventoryRepository;
    private final DailyRateRepository dailyRateRepository;
    private final AuthAccessClient authAccessClient;
    private final Clock clock;

    public AvailabilityServiceImpl(HotelRepository hotelRepository,
                                   HotelRoomTypeRepository roomTypeRepository,
                                   RatePlanRepository ratePlanRepository,
                                   DailyInventoryRepository dailyInventoryRepository,
                                   DailyRateRepository dailyRateRepository,
                                   AuthAccessClient authAccessClient,
                                   Clock clock) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.dailyInventoryRepository = dailyInventoryRepository;
        this.dailyRateRepository = dailyRateRepository;
        this.authAccessClient = authAccessClient;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID hotelId, LocalDate checkIn, LocalDate checkOut,
                                                 int rooms, int adults, List<Integer> childrenAges) {
        // 1. Validate stay range
        validateStayRange(checkIn, checkOut);

        // 2. Validate rooms and adults
        if (rooms < 1 || rooms > 10) {
            throw new IllegalArgumentException("rooms must be between 1 and 10");
        }
        if (adults < 1 || adults > 20) {
            throw new IllegalArgumentException("adults must be between 1 and 20");
        }

        List<Integer> ages = childrenAges != null ? childrenAges : List.of();
        if (ages.size() > 10) {
            throw new IllegalArgumentException("Up to 10 children ages allowed");
        }
        for (Integer age : ages) {
            if (age == null || age < 0 || age > 17) {
                throw new IllegalArgumentException("Each children age must be between 0 and 17");
            }
        }

        int occupancy = adults + ages.size();

        // 3. Check hotel exists and is active
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));

        if (!hotel.isActive()) {
            throw new BusinessRuleException("HOTEL_NOT_BOOKABLE", "Hotel is not bookable");
        }

        // 4. Check organization is ACTIVE (fail closed)
        if (!checkOrganizationActive(hotel.getOrganizationId())) {
            return new AvailabilityResponse(
                    hotelId, checkIn, checkOut,
                    (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut),
                    rooms, adults, ages, occupancy, List.of());
        }

        // 5. Get all ACTIVE room types and rate plans for this hotel
        List<HotelRoomType> roomTypes = roomTypeRepository
                .findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE);
        List<RatePlan> ratePlans = ratePlanRepository
                .findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE);

        if (roomTypes.isEmpty() || ratePlans.isEmpty()) {
            return new AvailabilityResponse(
                    hotelId, checkIn, checkOut,
                    (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut),
                    rooms, adults, ages, occupancy, List.of());
        }

        // 6. Index inventory and rates for all nights of the stay
        LocalDate lastNight = checkOut.minusDays(1);
        List<DailyInventory> allInventory = dailyInventoryRepository
                .findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight);

        // 7. Index rates for all dates in the range
        List<DailyRate> allRates = dailyRateRepository
                .findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null);

        // Group inventory by (roomTypeId, stayDate)
        Map<String, DailyInventory> inventoryMap = new HashMap<>();
        for (DailyInventory di : allInventory) {
            String key = di.getRoomTypeId() + "|" + di.getStayDate();
            inventoryMap.put(key, di);
        }

        // Group rates by (roomTypeId, ratePlanId, stayDate, occupancy)
        Map<String, DailyRate> rateMap = new HashMap<>();
        for (DailyRate dr : allRates) {
            String key = dr.getRoomTypeId() + "|" + dr.getRatePlanId() + "|" + dr.getStayDate() + "|" + dr.getOccupancy();
            rateMap.put(key, dr);
        }

        // 8. Build offers for each (roomType, ratePlan) pair
        List<AvailableOfferResponse> offers = new ArrayList<>();

        for (HotelRoomType roomType : roomTypes) {
            // Validate occupancy against room type limits
            if (adults > roomType.getMaxAdults()) {
                continue;
            }
            if (occupancy > roomType.getMaxOccupancy()) {
                continue;
            }
            if (ages.size() > roomType.getMaxChildren()) {
                continue;
            }

            for (RatePlan ratePlan : ratePlans) {
                List<NightlyRateResponse> nightlyRates = new ArrayList<>();
                BigDecimal totalAmount = BigDecimal.ZERO;
                String currency = null;
                boolean sellable = true;

                for (LocalDate date = checkIn; !date.isAfter(lastNight); date = date.plusDays(1)) {
                    // Check inventory
                    String invKey = roomType.getId() + "|" + date;
                    DailyInventory inv = inventoryMap.get(invKey);
                    if (inv == null || inv.isStopSell() || inv.getAvailable() < rooms) {
                        sellable = false;
                        break;
                    }

                    // Check rate
                    String rateKey = roomType.getId() + "|" + ratePlan.getId() + "|" + date + "|" + occupancy;
                    DailyRate rate = rateMap.get(rateKey);
                    if (rate == null) {
                        sellable = false;
                        break;
                    }

                    // Check currency consistency - mark sellable=false, not throw
                    if (currency == null) {
                        currency = rate.getCurrency();
                    } else if (!currency.equals(rate.getCurrency())) {
                        sellable = false;
                        break;
                    }

                    nightlyRates.add(new NightlyRateResponse(date, rate.getAmount(), rate.getCurrency()));
                    totalAmount = totalAmount.add(rate.getAmount());
                }

                if (sellable) {
                    totalAmount = totalAmount.multiply(BigDecimal.valueOf(rooms));
                    offers.add(new AvailableOfferResponse(
                            roomType.getId(),
                            roomType.getCode(),
                            roomType.getName(),
                            roomType.getMaxAdults(),
                            roomType.getMaxChildren(),
                            roomType.getMaxOccupancy(),
                            ratePlan.getId(),
                            ratePlan.getCode(),
                            ratePlan.getName(),
                            ratePlan.getMealPlan(),
                            ratePlan.getCancellationPolicy(),
                            ratePlan.getFreeCancellationDaysBefore(),
                            (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut),
                            rooms,
                            occupancy,
                            currency,
                            totalAmount,
                            nightlyRates
                    ));
                }
            }
        }

        return new AvailabilityResponse(
                hotelId, checkIn, checkOut,
                (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut),
                rooms, adults, ages, occupancy, offers);
    }

    private void validateStayRange(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("checkIn and checkOut are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessRuleException("STAY_RANGE_INVALID", "checkOut must be after checkIn");
        }
        long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) {
            throw new BusinessRuleException("STAY_RANGE_INVALID", "Stay cannot exceed " + MAX_NIGHTS + " nights");
        }
        LocalDate today = LocalDate.now(clock);
        if (checkIn.isBefore(today)) {
            throw new BusinessRuleException("STAY_RANGE_INVALID", "checkIn cannot be in the past");
        }
    }

    private boolean checkOrganizationActive(UUID organizationId) {
        if (organizationId == null) {
            return false; // legacy null-org: return empty offers
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
        return true;
    }
}