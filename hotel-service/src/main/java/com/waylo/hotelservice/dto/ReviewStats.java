package com.waylo.hotelservice.dto;

import java.util.UUID;

/** PUBLISHED review count and score sum of one hotel. The average is derived, never stored. */
public record ReviewStats(UUID hotelId, long count, long scoreSum) {

    public ReviewStats(UUID hotelId, Long count, Long scoreSum) {
        this(hotelId, count == null ? 0L : count, scoreSum == null ? 0L : scoreSum);
    }

    /** Average rounded to one decimal, null when there are no reviews. */
    public Double averageScore() {
        if (count <= 0) return null;
        return Math.round(scoreSum * 10.0 / count) / 10.0;
    }
}
