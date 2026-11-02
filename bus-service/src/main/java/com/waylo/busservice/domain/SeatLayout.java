package com.waylo.busservice.domain;

import java.util.Optional;

/** Seat plan per row. Seats are numbered row by row, left to right. Neighbours share a 2-seat block. */
public enum SeatLayout {
    TWO_PLUS_TWO(4), TWO_PLUS_ONE(3);

    private final int perRow;

    SeatLayout(int perRow) { this.perRow = perRow; }

    public int perRow() { return perRow; }

    public int seatCount(int rows) { return perRow * rows; }

    /** The seat sharing a 2-seat block with {@code seatNo}, empty for the single seat of a 2+1 row. */
    public Optional<Integer> neighbor(int seatNo) {
        int idx = (seatNo - 1) % perRow;
        if (this == TWO_PLUS_ONE && idx == 2) return Optional.empty();
        return Optional.of(idx % 2 == 0 ? seatNo + 1 : seatNo - 1);
    }
}
