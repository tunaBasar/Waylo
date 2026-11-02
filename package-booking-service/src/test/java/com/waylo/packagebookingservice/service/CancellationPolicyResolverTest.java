package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationPolicyResolverTest {

    private OrderItem item(OrderItemVertical v) { OrderItem i = new OrderItem(); i.setVertical(v); return i; }

    @Test
    void unconfigured_defaultsToFreeCancellation() {
        CancellationPolicyResolver r = new CancellationPolicyResolver("", "", "", "", "", "");
        for (OrderItemVertical v : new OrderItemVertical[]{OrderItemVertical.CAR, OrderItemVertical.BUS, OrderItemVertical.FLIGHT}) {
            OrderItem i = item(v);
            r.applyConfigured(i);
            assertThat(i.getCancellationPolicy()).isEqualTo("FREE_CANCELLATION");
            assertThat(i.getCancelFreeHoursBefore()).isNull();
        }
    }

    @Test
    void configuredFreeUntil_isSnapshotted() {
        CancellationPolicyResolver r = new CancellationPolicyResolver("FREE_UNTIL", "48", "", "", "NON_REFUNDABLE", "");
        OrderItem car = item(OrderItemVertical.CAR);
        r.applyConfigured(car);
        assertThat(car.getCancellationPolicy()).isEqualTo("FREE_UNTIL");
        assertThat(car.getCancelFreeHoursBefore()).isEqualTo(48);
        OrderItem flight = item(OrderItemVertical.FLIGHT);
        r.applyConfigured(flight);
        assertThat(flight.getCancellationPolicy()).isEqualTo("NON_REFUNDABLE");
    }

    @Test
    void invalidConfig_failsFast() {
        assertThatThrownBy(() -> new CancellationPolicyResolver("FREE_UNTIL", "", "", "", "", "")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CancellationPolicyResolver("MAYBE", "", "", "", "", "")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hotelPolicyComesFromHold() {
        CancellationPolicyResolver r = new CancellationPolicyResolver("", "", "", "", "", "");
        OrderItem i = item(OrderItemVertical.HOTEL);
        r.applyHotel(i, "NON_REFUNDABLE");
        assertThat(i.getCancellationPolicy()).isEqualTo("NON_REFUNDABLE");
        r.applyHotel(i, null);
        assertThat(i.getCancellationPolicy()).isEqualTo("FREE_CANCELLATION");
    }

    @Test
    void serviceStart() {
        OrderItem h = item(OrderItemVertical.HOTEL);
        h.setCheckIn(LocalDate.of(2026, 12, 1));
        assertThat(CancellationPolicyResolver.serviceStart(h)).isEqualTo(Instant.parse("2026-11-30T21:00:00Z"));
        assertThat(CancellationPolicyResolver.serviceStart(item(OrderItemVertical.FLIGHT))).isNull();
    }
}
