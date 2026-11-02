package com.waylo.busservice.rules;

import com.waylo.busservice.domain.*;
import com.waylo.busservice.dto.BusDtos.PassengerItem;
import com.waylo.busservice.exception.BusinessRuleException;
import com.waylo.busservice.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatRulesTest {

    private Bus bus(SeatLayout layout) {
        Bus b = new Bus();
        b.layout = layout;
        b.rowCount = 10;
        b.seatCount = layout.seatCount(10);
        return b;
    }

    private Ticket ticket(int seat, int from, int to, Gender g) {
        Ticket t = new Ticket();
        t.id = UUID.randomUUID();
        t.seatNo = seat;
        t.fromSeq = from;
        t.toSeq = to;
        t.gender = g;
        return t;
    }

    private PassengerItem p(int seat, Gender g) { return new PassengerItem(seat, "Ali Veli", g, null); }

    @Test
    void segmentsAreHalfOpen() {
        assertThat(SeatRules.overlaps(0, 2, 2, 4)).isFalse();
        assertThat(SeatRules.overlaps(0, 3, 2, 4)).isTrue();
        assertThat(SeatRules.overlaps(1, 2, 0, 3)).isTrue();
    }

    @Test
    void layoutNeighbors() {
        assertThat(SeatLayout.TWO_PLUS_TWO.neighbor(1)).contains(2);
        assertThat(SeatLayout.TWO_PLUS_TWO.neighbor(2)).contains(1);
        assertThat(SeatLayout.TWO_PLUS_TWO.neighbor(3)).contains(4);
        assertThat(SeatLayout.TWO_PLUS_TWO.neighbor(5)).contains(6);
        assertThat(SeatLayout.TWO_PLUS_ONE.neighbor(3)).isEmpty();
        assertThat(SeatLayout.TWO_PLUS_ONE.neighbor(4)).contains(5);
        assertThat(SeatLayout.TWO_PLUS_ONE.seatCount(10)).isEqualTo(30);
    }

    @Test
    void takenSeatOnOverlappingSegmentConflicts() {
        Bus b = bus(SeatLayout.TWO_PLUS_TWO);
        assertThatThrownBy(() -> SeatRules.validate(b, 1, 3, List.of(p(5, Gender.MALE)),
                List.of(ticket(5, 0, 2, Gender.MALE)))).isInstanceOf(ConflictException.class);
    }

    @Test
    void seatFreeAfterPassengerLeavesIsReusable() {
        Bus b = bus(SeatLayout.TWO_PLUS_TWO);
        SeatRules.validate(b, 2, 4, List.of(p(5, Gender.MALE)), List.of(ticket(5, 0, 2, Gender.MALE)));
    }

    @Test
    void invalidAndDuplicateSeats() {
        Bus b = bus(SeatLayout.TWO_PLUS_ONE);
        assertThatThrownBy(() -> SeatRules.validate(b, 0, 1, List.of(p(31, Gender.MALE)), List.of()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> SeatRules.validate(b, 0, 1, List.of(p(1, Gender.MALE), p(1, Gender.MALE)), List.of()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("twice");
    }

    @Test
    void neighborGenderRule() {
        Bus b = bus(SeatLayout.TWO_PLUS_TWO);
        List<Ticket> blocking = List.of(ticket(1, 0, 2, Gender.FEMALE));
        assertThatThrownBy(() -> SeatRules.validate(b, 0, 2, List.of(p(2, Gender.MALE)), blocking))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getRuleCode()).isEqualTo("SEAT_GENDER_CONFLICT");
        SeatRules.validate(b, 0, 2, List.of(p(2, Gender.FEMALE)), blocking);
        SeatRules.validate(b, 0, 2, List.of(p(3, Gender.MALE)), blocking);
        // non overlapping segment does not count as a neighbour
        SeatRules.validate(b, 2, 4, List.of(p(2, Gender.MALE)), blocking);
    }

    @Test
    void sameBookingMaySeatMixedGendersNextToEachOther() {
        Bus b = bus(SeatLayout.TWO_PLUS_TWO);
        SeatRules.validate(b, 0, 1, List.of(p(1, Gender.FEMALE), p(2, Gender.MALE)), List.of());
    }
}
