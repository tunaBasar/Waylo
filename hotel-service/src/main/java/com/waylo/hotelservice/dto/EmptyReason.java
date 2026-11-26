package com.waylo.hotelservice.dto;

import java.time.LocalDate;

/**
 * Why an availability search returned no offers. {@code date} is the first affected night
 * (NO_STOCK, NO_PRICE_FOR_OCCUPANCY) and is null for NO_ROOM_TYPE_FOR_GROUP.
 */
public record EmptyReason(String code, LocalDate date) {
    public static final String NO_STOCK = "NO_STOCK";
    public static final String NO_PRICE_FOR_OCCUPANCY = "NO_PRICE_FOR_OCCUPANCY";
    public static final String NO_ROOM_TYPE_FOR_GROUP = "NO_ROOM_TYPE_FOR_GROUP";
}
