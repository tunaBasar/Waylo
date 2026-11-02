package com.waylo.carrentalservice.rules;

import com.waylo.carrentalservice.domain.Rental;
import com.waylo.carrentalservice.domain.Vehicle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final Instant t0 = Instant.parse("2027-03-01T10:00:00Z");

    private Vehicle vehicle(UUID home) {
        Vehicle v = new Vehicle();
        v.homeBranchId = home;
        return v;
    }

    private Rental rental(UUID from, UUID to, int startDay, int endDay) {
        Rental r = new Rental();
        r.pickupBranchId = from;
        r.returnBranchId = to;
        r.pickupAt = t0.plusSeconds(startDay * 86400L);
        r.returnAt = t0.plusSeconds(endDay * 86400L);
        return r;
    }

    @Test
    void homeBranchIsLocationWhenNoHistory() {
        Vehicle v = vehicle(a);
        assertThat(Availability.isFree(v, List.of(), t0, t0.plusSeconds(86400), a, a)).isTrue();
        assertThat(Availability.isFree(v, List.of(), t0, t0.plusSeconds(86400), b, b)).isFalse();
    }

    @Test
    void overlappingRentalBlocks() {
        assertThat(Availability.isFree(vehicle(a), List.of(rental(a, a, 0, 2)), t0.plusSeconds(86400), t0.plusSeconds(3 * 86400), a, a)).isFalse();
    }

    @Test
    void backToBackIsAllowedWhenLocationMatches() {
        Vehicle v = vehicle(a);
        Rental prev = rental(a, b, -3, 0);
        assertThat(Availability.isFree(v, List.of(prev), t0, t0.plusSeconds(86400), b, b)).isTrue();
        assertThat(Availability.isFree(v, List.of(prev), t0, t0.plusSeconds(86400), a, a)).isFalse();
    }

    @Test
    void latestPreviousRentalDeterminesLocation() {
        Vehicle v = vehicle(a);
        List<Rental> history = List.of(rental(a, b, -6, -4), rental(b, c, -3, -1));
        assertThat(Availability.isFree(v, history, t0, t0.plusSeconds(86400), c, c)).isTrue();
        assertThat(Availability.isFree(v, history, t0, t0.plusSeconds(86400), b, b)).isFalse();
    }

    @Test
    void nextRentalMustPickUpAtRequestedReturnBranch() {
        Vehicle v = vehicle(a);
        Rental next = rental(b, b, 2, 3);
        assertThat(Availability.isFree(v, List.of(next), t0, t0.plusSeconds(2 * 86400), a, b)).isTrue();
        assertThat(Availability.isFree(v, List.of(next), t0, t0.plusSeconds(2 * 86400), a, a)).isFalse();
    }
}
