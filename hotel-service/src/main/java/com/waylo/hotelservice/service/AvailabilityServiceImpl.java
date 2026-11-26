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
import com.waylo.hotelservice.dto.EmptyReason;
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
    static final int NEXT_AVAILABLE_WINDOW_DAYS = 60;

    private final HotelRepository hotelRepository;
    private final HotelRoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final DailyInventoryRepository dailyInventoryRepository;
    private final DailyRateRepository dailyRateRepository;
    private final AuthAccessClient authAccessClient;
    private final Clock clock;
    private final OccupancyRateResolver rateResolver;

    public AvailabilityServiceImpl(HotelRepository hotelRepository,
                                   HotelRoomTypeRepository roomTypeRepository,
                                   RatePlanRepository ratePlanRepository,
                                   DailyInventoryRepository dailyInventoryRepository,
                                   DailyRateRepository dailyRateRepository,
                                   AuthAccessClient authAccessClient,
                                   Clock clock,
                                   OccupancyRateResolver rateResolver) {
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.dailyInventoryRepository = dailyInventoryRepository;
        this.dailyRateRepository = dailyRateRepository;
        this.authAccessClient = authAccessClient;
        this.clock = clock;
        this.rateResolver = rateResolver;
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

        if (!hotel.isActive() || hotel.getPublicationStatus() != com.waylo.hotelservice.domain.PublicationStatus.PUBLISHED) {
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
                    rooms, adults, ages, occupancy, List.of(),
                    new EmptyReason(roomTypes.isEmpty() ? EmptyReason.NO_ROOM_TYPE_FOR_GROUP
                            : EmptyReason.NO_PRICE_FOR_OCCUPANCY, null),
                    null);
        }

        int nights = (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
        List<HotelRoomType> fitting = new ArrayList<>();
        for (HotelRoomType roomType : roomTypes) {
            if (fitsGroup(roomType, adults, occupancy, ages.size())) {
                fitting.add(roomType);
            }
        }

        // 6. Index inventory and rates for all nights of the stay
        LocalDate lastNight = checkOut.minusDays(1);
        Index index = new Index(
                dailyInventoryRepository.findByHotelIdAndStayDateBetween(hotelId, checkIn, lastNight),
                dailyRateRepository.findByHotelIdAndDateRange(hotelId, checkIn, lastNight, null, null));

        List<AvailableOfferResponse> offers = buildOffers(fitting, ratePlans, index, checkIn, checkOut, rooms, occupancy);
        if (!offers.isEmpty()) {
            return new AvailabilityResponse(hotelId, checkIn, checkOut, nights, rooms, adults, ages, occupancy, offers);
        }

        // 7. No offers: explain why and look for the next workable check-in date
        EmptyReason reason = explainEmpty(fitting, ratePlans, index, checkIn, checkOut, rooms, occupancy);
        LocalDate next = null;
        if (!fitting.isEmpty()) {
            LocalDate searchEnd = checkIn.plusDays(NEXT_AVAILABLE_WINDOW_DAYS);
            Index wide = new Index(
                    dailyInventoryRepository.findByHotelIdAndStayDateBetween(
                            hotelId, checkIn, searchEnd.plusDays(nights - 1L)),
                    dailyRateRepository.findByHotelIdAndDateRange(
                            hotelId, checkIn, searchEnd.plusDays(nights - 1L), null, null));
            next = findNextCheckIn(fitting, ratePlans, wide, checkIn, nights, rooms, occupancy);
        }
        return new AvailabilityResponse(hotelId, checkIn, checkOut, nights, rooms, adults, ages, occupancy,
                List.of(), reason, next);
    }

    private static boolean fitsGroup(HotelRoomType rt, int adults, int occupancy, int children) {
        return adults <= rt.getMaxAdults() && occupancy <= rt.getMaxOccupancy() && children <= rt.getMaxChildren();
    }

    /** Inventory and rate lookups keyed for quick per-night access. */
    private static final class Index {
        private final Map<String, DailyInventory> inventory = new HashMap<>();
        private final Map<String, List<DailyRate>> rates = new HashMap<>();

        Index(List<DailyInventory> inv, List<DailyRate> rt) {
            for (DailyInventory di : inv) {
                inventory.put(di.getRoomTypeId() + "|" + di.getStayDate(), di);
            }
            for (DailyRate dr : rt) {
                rates.computeIfAbsent(dr.getRoomTypeId() + "|" + dr.getRatePlanId() + "|" + dr.getStayDate(),
                        k -> new ArrayList<>()).add(dr);
            }
        }

        boolean hasStock(UUID roomTypeId, LocalDate date, int rooms) {
            DailyInventory inv = inventory.get(roomTypeId + "|" + date);
            return inv != null && !inv.isStopSell() && inv.getAvailable() >= rooms;
        }

        List<DailyRate> rates(UUID roomTypeId, UUID ratePlanId, LocalDate date) {
            return rates.get(roomTypeId + "|" + ratePlanId + "|" + date);
        }
    }

    private List<AvailableOfferResponse> buildOffers(List<HotelRoomType> roomTypes, List<RatePlan> ratePlans,
                                                      Index index, LocalDate checkIn, LocalDate checkOut,
                                                      int rooms, int occupancy) {
        List<AvailableOfferResponse> offers = new ArrayList<>();
        LocalDate lastNight = checkOut.minusDays(1);
        int nights = (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);

        for (HotelRoomType roomType : roomTypes) {
            for (RatePlan ratePlan : ratePlans) {
                List<NightlyRateResponse> nightlyRates = new ArrayList<>();
                BigDecimal totalAmount = BigDecimal.ZERO;
                String currency = null;
                boolean sellable = true;

                for (LocalDate date = checkIn; !date.isAfter(lastNight); date = date.plusDays(1)) {
                    if (!index.hasStock(roomType.getId(), date, rooms)) {
                        sellable = false;
                        break;
                    }
                    DailyRate rate = rateResolver.resolve(
                            index.rates(roomType.getId(), ratePlan.getId(), date), occupancy, roomType.getMaxOccupancy());
                    if (rate == null) {
                        sellable = false;
                        break;
                    }
                    // Mixed currency marks the offer unsellable instead of throwing
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
                            roomType.getId(), roomType.getCode(), roomType.getName(),
                            roomType.getMaxAdults(), roomType.getMaxChildren(), roomType.getMaxOccupancy(),
                            ratePlan.getId(), ratePlan.getCode(), ratePlan.getName(),
                            ratePlan.getMealPlan(), ratePlan.getCancellationPolicy(),
                            ratePlan.getFreeCancellationDaysBefore(),
                            nights, rooms, occupancy, currency, totalAmount, nightlyRates));
                }
            }
        }
        return offers;
    }

    private EmptyReason explainEmpty(List<HotelRoomType> fitting, List<RatePlan> ratePlans, Index index,
                                     LocalDate checkIn, LocalDate checkOut, int rooms, int occupancy) {
        if (fitting.isEmpty()) {
            return new EmptyReason(EmptyReason.NO_ROOM_TYPE_FOR_GROUP, null);
        }
        LocalDate lastNight = checkOut.minusDays(1);
        for (LocalDate date = checkIn; !date.isAfter(lastNight); date = date.plusDays(1)) {
            boolean stocked = false;
            for (HotelRoomType rt : fitting) {
                if (index.hasStock(rt.getId(), date, rooms)) {
                    stocked = true;
                    break;
                }
            }
            if (!stocked) {
                return new EmptyReason(EmptyReason.NO_STOCK, date);
            }
        }
        for (LocalDate date = checkIn; !date.isAfter(lastNight); date = date.plusDays(1)) {
            if (!anyPricedAndStocked(fitting, ratePlans, index, date, rooms, occupancy)) {
                return new EmptyReason(EmptyReason.NO_PRICE_FOR_OCCUPANCY, date);
            }
        }
        // Every night is stocked and priced somewhere, but no single offer spans the stay (e.g. mixed currency)
        return new EmptyReason(EmptyReason.NO_PRICE_FOR_OCCUPANCY, checkIn);
    }

    private boolean anyPricedAndStocked(List<HotelRoomType> fitting, List<RatePlan> ratePlans, Index index,
                                        LocalDate date, int rooms, int occupancy) {
        for (HotelRoomType rt : fitting) {
            if (!index.hasStock(rt.getId(), date, rooms)) {
                continue;
            }
            for (RatePlan rp : ratePlans) {
                if (rateResolver.resolve(index.rates(rt.getId(), rp.getId(), date), occupancy, rt.getMaxOccupancy()) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private LocalDate findNextCheckIn(List<HotelRoomType> fitting, List<RatePlan> ratePlans, Index index,
                                      LocalDate checkIn, int nights, int rooms, int occupancy) {
        for (int offset = 1; offset <= NEXT_AVAILABLE_WINDOW_DAYS; offset++) {
            LocalDate candidate = checkIn.plusDays(offset);
            if (!buildOffers(fitting, ratePlans, index, candidate, candidate.plusDays(nights), rooms, occupancy).isEmpty()) {
                return candidate;
            }
        }
        return null;
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