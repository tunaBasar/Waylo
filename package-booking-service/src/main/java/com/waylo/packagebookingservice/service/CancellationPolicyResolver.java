package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Chooses the policy snapshot written on a new order item. Hotel: the rate plan policy from the hold. Car, bus, flight:
 * platform configuration (waylo.cancellation.{car,bus,flight}.*); unconfigured means FREE_CANCELLATION. No numbers are
 * invented here, see CANCELLATION-CONTRACT.md.
 */
@Component
public class CancellationPolicyResolver {

    static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final String[] policy = new String[3];
    private final Integer[] hours = new Integer[3];

    public CancellationPolicyResolver(
            @Value("${waylo.cancellation.car.policy:}") String carPolicy,
            @Value("${waylo.cancellation.car.free-hours-before:}") String carHours,
            @Value("${waylo.cancellation.bus.policy:}") String busPolicy,
            @Value("${waylo.cancellation.bus.free-hours-before:}") String busHours,
            @Value("${waylo.cancellation.flight.policy:}") String flightPolicy,
            @Value("${waylo.cancellation.flight.free-hours-before:}") String flightHours) {
        set(0, carPolicy, carHours);
        set(1, busPolicy, busHours);
        set(2, flightPolicy, flightHours);
    }

    private void set(int i, String p, String h) {
        String pol = p == null || p.isBlank() ? "FREE_CANCELLATION" : p.trim();
        if (!pol.equals("FREE_CANCELLATION") && !pol.equals("NON_REFUNDABLE") && !pol.equals("FREE_UNTIL")) {
            throw new IllegalStateException("Invalid cancellation policy '" + pol + "'");
        }
        Integer hrs = h == null || h.isBlank() ? null : Integer.valueOf(h.trim());
        if (pol.equals("FREE_UNTIL") && (hrs == null || hrs < 0)) {
            throw new IllegalStateException("FREE_UNTIL needs a non-negative free-hours-before");
        }
        policy[i] = pol;
        hours[i] = pol.equals("FREE_UNTIL") ? hrs : null;
    }

    /** Hotel: rate plan policy from the hold. A missing value (older hotel-service) falls back to FREE_CANCELLATION. */
    public void applyHotel(OrderItem item, String holdPolicy) {
        item.setCancellationPolicy("NON_REFUNDABLE".equals(holdPolicy) ? "NON_REFUNDABLE" : "FREE_CANCELLATION");
        item.setCancelFreeHoursBefore(null);
    }

    public void applyConfigured(OrderItem item) {
        int i = switch (item.getVertical()) {
            case CAR -> 0;
            case BUS -> 1;
            case FLIGHT -> 2;
            default -> -1;
        };
        if (i < 0) return;
        item.setCancellationPolicy(policy[i]);
        item.setCancelFreeHoursBefore(hours[i]);
    }

    /** Start of the service, the reference point of FREE_UNTIL. Null when unknown. */
    public static Instant serviceStart(OrderItem item) {
        if (item.getVertical() == OrderItemVertical.HOTEL && item.getCheckIn() != null) {
            return item.getCheckIn().atStartOfDay(ZONE).toInstant();
        }
        if (item.getVertical() == OrderItemVertical.FLIGHT) return item.getDepartureAt();
        if (item.getPickupAt() != null) return item.getPickupAt().atZone(ZONE).toInstant();
        return null;
    }
}
