package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.DailyRate;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.domain.RatePlan;
import com.waylo.hotelservice.dto.DailyRateResponse;
import com.waylo.hotelservice.dto.HotelCurrencyResponse;
import com.waylo.hotelservice.dto.RateUpdateItem;
import com.waylo.hotelservice.dto.RateUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.DailyRateRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.RatePlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Service
public class RateServiceImpl implements RateService {

    private static final Logger log = LoggerFactory.getLogger(RateServiceImpl.class);
    private static final int MAX_DAYS = 366;

    private final DailyRateRepository dailyRateRepository;
    private final HotelRepository hotelRepository;
    private final HotelRoomTypeRepository roomTypeRepository;
    private final RatePlanRepository ratePlanRepository;
    private final Clock clock;

    public RateServiceImpl(DailyRateRepository dailyRateRepository,
                           HotelRepository hotelRepository,
                           HotelRoomTypeRepository roomTypeRepository,
                           RatePlanRepository ratePlanRepository,
                           Clock clock) {
        this.dailyRateRepository = dailyRateRepository;
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.ratePlanRepository = ratePlanRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyRateResponse> getRates(UUID hotelId, LocalDate from, LocalDate to,
                                             UUID roomTypeId, UUID ratePlanId) {
        validateDateRange(from, to);

        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", "id", hotelId);
        }

        if (roomTypeId != null) {
            roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room type", "id", roomTypeId));
        }

        if (ratePlanId != null) {
            ratePlanRepository.findByIdAndHotelId(ratePlanId, hotelId)
                    .orElseThrow(() -> new ResourceNotFoundException("Rate plan", "id", ratePlanId));
        }

        List<DailyRate> rates = dailyRateRepository.findByHotelIdAndDateRange(
                hotelId, from, to, roomTypeId, ratePlanId);

        return rates.stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<DailyRateResponse> updateRates(UUID hotelId, RateUpdateRequest request) {
        List<RateUpdateItem> items = request.items();

        // 1. Lock hotel to serialize mutations for same hotel (single-hotel-currency invariant)
        Optional<Hotel> hotelOpt = hotelRepository.findByIdForUpdate(hotelId);
        if (hotelOpt.isEmpty()) {
            throw new ResourceNotFoundException("Hotel", "id", hotelId);
        }

        // 2. Validate all roomTypeId/ratePlanId belong to the hotel
        Set<UUID> roomTypeIds = new HashSet<>();
        Set<UUID> ratePlanIds = new HashSet<>();
        for (RateUpdateItem item : items) {
            roomTypeIds.add(item.roomTypeId());
            ratePlanIds.add(item.ratePlanId());
        }

        List<HotelRoomType> roomTypes = roomTypeRepository.findAllById(roomTypeIds);
        Map<UUID, HotelRoomType> roomTypeMap = new HashMap<>();
        for (HotelRoomType rt : roomTypes) {
            if (rt.getHotelId().equals(hotelId)) {
                roomTypeMap.put(rt.getId(), rt);
            }
        }

        List<RatePlan> ratePlans = ratePlanRepository.findAllById(ratePlanIds);
        Map<UUID, RatePlan> ratePlanMap = new HashMap<>();
        for (RatePlan rp : ratePlans) {
            if (rp.getHotelId().equals(hotelId)) {
                ratePlanMap.put(rp.getId(), rp);
            }
        }

        // Check all IDs belong to hotel
        for (RateUpdateItem item : items) {
            if (!roomTypeMap.containsKey(item.roomTypeId())) {
                throw new ResourceNotFoundException("Room type", "id", item.roomTypeId());
            }
            if (!ratePlanMap.containsKey(item.ratePlanId())) {
                throw new ResourceNotFoundException("Rate plan", "id", item.ratePlanId());
            }
        }

        // 3. Check occupancy <= roomType.maxOccupancy
        for (RateUpdateItem item : items) {
            int maxOcc = roomTypeMap.get(item.roomTypeId()).getMaxOccupancy();
            if (item.occupancy() > maxOcc) {
                throw new BusinessRuleException("OCCUPANCY_RULE",
                        "Occupancy " + item.occupancy() + " exceeds room type max occupancy " + maxOcc);
            }
        }

        // 4. Check stayDate in valid range: [today - 1, today + 730]
        LocalDate today = LocalDate.now(clock);
        LocalDate minDate = today.minusDays(1);
        LocalDate maxDate = today.plusDays(730);

        for (RateUpdateItem item : items) {
            if (item.stayDate().isBefore(minDate) || item.stayDate().isAfter(maxDate)) {
                throw new BusinessRuleException("STAY_DATE_OUT_OF_RANGE",
                        "Stay date " + item.stayDate() + " is outside allowed range [" + minDate + ", " + maxDate + "]");
            }
        }

        // 5. Check currency consistency within request
        Set<String> requestCurrencies = new HashSet<>();
        for (RateUpdateItem item : items) {
            requestCurrencies.add(item.currency());
        }
        if (requestCurrencies.size() > 1) {
            throw new BusinessRuleException("CURRENCY_MISMATCH",
                    "Request contains mixed currencies: " + requestCurrencies);
        }

        String requestCurrency = requestCurrencies.iterator().next();

        // 6. Check hotel currency: reject ANY existing distinct currency different from request currency
        //    Query only the currencies that exist for this hotel (not limited to request date range)
        List<String> existingCurrencies = dailyRateRepository.findCurrenciesByHotelId(hotelId);
        if (!existingCurrencies.isEmpty()) {
            for (String existingCur : existingCurrencies) {
                if (!existingCur.equals(requestCurrency)) {
                    throw new BusinessRuleException("CURRENCY_MISMATCH",
                            "Currency " + requestCurrency + " does not match existing hotel currency " + existingCur);
                }
            }
        }

        // 7. Check for duplicate keys within request -> IllegalArgumentException (400)
        Set<String> seenKeys = new HashSet<>();
        for (RateUpdateItem item : items) {
            String key = item.roomTypeId() + "|" + item.ratePlanId() + "|" + item.stayDate() + "|" + item.occupancy();
            if (seenKeys.contains(key)) {
                throw new IllegalArgumentException("Duplicate rate entry for roomTypeId="
                        + item.roomTypeId() + ", ratePlanId=" + item.ratePlanId()
                        + ", stayDate=" + item.stayDate()
                        + ", occupancy=" + item.occupancy());
            }
            seenKeys.add(key);
        }

        // 8. Query min/max dates from REQUEST items only (not 2 years)
        LocalDate requestMinDate = null;
        LocalDate requestMaxDate = null;
        for (RateUpdateItem item : items) {
            if (requestMinDate == null || item.stayDate().isBefore(requestMinDate)) {
                requestMinDate = item.stayDate();
            }
            if (requestMaxDate == null || item.stayDate().isAfter(requestMaxDate)) {
                requestMaxDate = item.stayDate();
            }
        }

        // Bulk query existing rows only for the requested date range
        List<DailyRate> existingRates = dailyRateRepository.findByHotelIdAndRoomTypeIdsAndRatePlanIdsAndDateRange(
                hotelId, roomTypeIds, ratePlanIds,
                requestMinDate != null ? requestMinDate : minDate,
                requestMaxDate != null ? requestMaxDate : maxDate);

        // Map: key -> existing entity (for lookup by composite key)
        Map<String, DailyRate> existingByCompositeKey = new HashMap<>();
        for (DailyRate er : existingRates) {
            String key = er.getRoomTypeId() + "|" + er.getRatePlanId() + "|" + er.getStayDate() + "|" + er.getOccupancy();
            existingByCompositeKey.put(key, er);
        }

        // 9. Build list of entities to save (upsert logic)
        //    Check version BEFORE mutating entity
        List<DailyRate> entitiesToSave = new ArrayList<>();
        for (RateUpdateItem item : items) {
            String key = item.roomTypeId() + "|" + item.ratePlanId() + "|" + item.stayDate() + "|" + item.occupancy();
            DailyRate entity;

            if (existingByCompositeKey.containsKey(key)) {
                // Update existing row - check version BEFORE mutating
                entity = existingByCompositeKey.get(key);

                if (item.expectedVersion() != null && !item.expectedVersion().equals(entity.getVersion())) {
                    throw new ObjectOptimisticLockingFailureException(
                            DailyRate.class, entity.getId().toString());
                }

                // Now safe to mutate
                entity.setAmount(item.amount());
                entity.setOccupancy(item.occupancy());
                entity.setCurrency(item.currency());
            } else {
                // New row
                entity = new DailyRate();
                entity.setHotelId(hotelId);
                entity.setRoomTypeId(item.roomTypeId());
                entity.setRatePlanId(item.ratePlanId());
                entity.setStayDate(item.stayDate());
                entity.setOccupancy(item.occupancy());
                entity.setAmount(item.amount());
                entity.setCurrency(item.currency());
                // Do NOT set version for new rows - it starts at 0 by default

                if (item.expectedVersion() != null) {
                    throw new ObjectOptimisticLockingFailureException(
                            DailyRate.class, "new-row-expected-version");
                }
            }

            entitiesToSave.add(entity);
        }

        // Save all entities with flush to ensure versions are updated
        List<DailyRate> saved = dailyRateRepository.saveAllAndFlush(entitiesToSave);

        return saved.stream()
                .map(this::toResponse)
                .toList();
    }

    private DailyRateResponse toResponse(DailyRate entity) {
        return new DailyRateResponse(
                entity.getId(),
                entity.getHotelId(),
                entity.getRoomTypeId(),
                entity.getRatePlanId(),
                entity.getStayDate(),
                entity.getOccupancy(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getVersion(),
                entity.getUpdatedAt()
        );
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to date must be after or equal to from date");
        }
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        if (daysBetween > MAX_DAYS) {
            throw new IllegalArgumentException("Date range exceeds " + MAX_DAYS + " days");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HotelCurrencyResponse getHotelCurrency(UUID hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", "id", hotelId);
        }

        List<String> existingCurrencies = dailyRateRepository.findCurrenciesByHotelId(hotelId);
        if (existingCurrencies.isEmpty()) {
            return null;
        }

        // Return the first (and should be only) currency
        String currency = existingCurrencies.get(0);
        return new HotelCurrencyResponse(currency);
    }
}
