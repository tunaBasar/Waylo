package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.DailyRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OccupancyRateResolverTest {

    private final OccupancyRateResolver resolver = new OccupancyRateResolver();

    private static DailyRate row(int occupancy, int amount) {
        DailyRate dr = new DailyRate();
        dr.setOccupancy(occupancy);
        dr.setAmount(BigDecimal.valueOf(amount));
        dr.setCurrency("USD");
        return dr;
    }

    @Test
    void exactHitWins() {
        DailyRate r = resolver.resolve(List.of(row(1, 80), row(2, 100)), 1, 2);
        assertThat(r.getOccupancy()).isEqualTo(1);
        assertThat(r.getAmount()).isEqualByComparingTo("80");
    }

    @Test
    void oneAdultFallsBackToTwo() {
        DailyRate r = resolver.resolve(List.of(row(2, 100)), 1, 2);
        assertThat(r.getOccupancy()).isEqualTo(2);
        assertThat(r.getAmount()).isEqualByComparingTo("100");
    }

    @Test
    void neverFallsBackDownward() {
        assertThat(resolver.resolve(List.of(row(2, 100)), 3, 4)).isNull();
    }

    @Test
    void gapUsesSmallestHigherOccupancy() {
        DailyRate r = resolver.resolve(List.of(row(1, 80), row(3, 150)), 2, 3);
        assertThat(r.getOccupancy()).isEqualTo(3);
    }

    @Test
    void rowsAboveMaxOccupancyAreRejected() {
        assertThat(resolver.resolve(List.of(row(3, 150)), 1, 2)).isNull();
        assertThat(resolver.resolve(List.of(row(2, 100)), 3, 2)).isNull();
    }

    @Test
    void emptyOrNullRowsResolveToNull() {
        assertThat(resolver.resolve(null, 1, 2)).isNull();
        assertThat(resolver.resolve(List.of(), 1, 2)).isNull();
    }
}
