package com.waylo.busservice.rules;

import com.waylo.busservice.dto.BusDtos.FareItem;
import com.waylo.busservice.dto.BusDtos.StopItem;
import com.waylo.busservice.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripRulesTest {

    private static final Instant NOW = Instant.parse("2027-02-01T00:00:00Z");

    private StopItem stop(LocalDateTime arr, LocalDateTime dep) { return new StopItem(UUID.randomUUID(), arr, dep); }

    private final LocalDateTime t0 = LocalDateTime.of(2027, 2, 2, 22, 0);

    @Test
    void validRoutePasses() {
        TripRules.validateStops(NOW, List.of(stop(t0, t0), stop(t0.plusHours(3), t0.plusHours(3).plusMinutes(15)),
                stop(t0.plusHours(6), t0.plusHours(6))));
    }

    @Test
    void needsTwoStopsFutureAndIncreasingTimes() {
        assertThatThrownBy(() -> TripRules.validateStops(NOW, List.of(stop(t0, t0)))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> TripRules.validateStops(NOW, List.of(stop(t0.minusDays(5), t0.minusDays(5)),
                stop(t0, t0)))).hasMessageContaining("future");
        assertThatThrownBy(() -> TripRules.validateStops(NOW, List.of(stop(t0, t0), stop(t0.minusHours(1), t0.minusHours(1)))))
                .hasMessageContaining("increase");
    }

    @Test
    void sameTerminalTwiceIsRejected() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> TripRules.validateStops(NOW, List.of(new StopItem(id, t0, t0),
                new StopItem(id, t0.plusHours(1), t0.plusHours(1))))).hasMessageContaining("twice");
    }

    @Test
    void faresNeedFullRouteAndValidRange() {
        BigDecimal p = new BigDecimal("100");
        TripRules.validateFares(3, List.of(new FareItem(0, 2, p), new FareItem(0, 1, p)));
        assertThatThrownBy(() -> TripRules.validateFares(3, List.of(new FareItem(0, 1, p)))).hasMessageContaining("full route");
        assertThatThrownBy(() -> TripRules.validateFares(3, List.of(new FareItem(0, 3, p)))).hasMessageContaining("out of range");
        assertThatThrownBy(() -> TripRules.validateFares(3, List.of(new FareItem(0, 2, p), new FareItem(0, 2, p))))
                .hasMessageContaining("Duplicate");
        assertThat(TripRules.toInstant(t0)).isEqualTo(Instant.parse("2027-02-02T19:00:00Z"));
    }
}
