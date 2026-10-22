package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.HotelResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Public catalog search enriched with whole-stay sellability, without changing its response shape. */
@Service
public class HotelSearchService {
    private final HotelService hotels;
    private final AvailabilityService availability;
    private final Clock clock;

    public HotelSearchService(HotelService hotels, AvailabilityService availability, Clock clock) {
        this.hotels = hotels;
        this.availability = availability;
        this.clock = clock;
    }

    public List<HotelResponse> search(String city, String country, Integer minStars,
                                      LocalDate checkIn, LocalDate checkOut,
                                      int rooms, int adults, List<Integer> childrenAges) {
        // Validate before querying candidates, including when the public catalog is empty.
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("checkIn and checkOut must both be supplied");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (checkIn.isBefore(LocalDate.now(clock)) || nights < 1 || nights > 30) {
            throw new IllegalArgumentException("Stay must start today or later and last between 1 and 30 nights");
        }
        if (childrenAges != null && childrenAges.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Children ages must not contain null values");
        }
        List<Integer> ages = childrenAges == null ? List.of() : List.copyOf(childrenAges);
        if (rooms < 1 || rooms > 10 || adults < 1 || adults > 20 || ages.size() > 10
                || adults + ages.size() > 30
                || ages.stream().anyMatch(age -> age < 0 || age > 17)) {
            throw new IllegalArgumentException("Invalid room count, adults or children ages");
        }
        return hotels.search(city, country, minStars).stream()
                .filter(hotel -> !availability.getAvailability(hotel.id(), checkIn, checkOut, rooms, adults, ages)
                        .offers().isEmpty())
                .toList();
    }
}
