package com.waylo.carrentalservice.rules;

import com.waylo.carrentalservice.domain.Rental;
import com.waylo.carrentalservice.domain.Vehicle;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure unit availability incl. the location chain. {@code blocking} = HELD (unexpired) and CONFIRMED rentals of the vehicle. */
public final class Availability {

    private Availability() { }

    public static boolean isFree(Vehicle vehicle, List<Rental> blocking, Instant pickupAt, Instant returnAt,
                                 UUID pickupBranchId, UUID returnBranchId) {
        for (Rental r : blocking) {
            if (r.pickupAt.isBefore(returnAt) && r.returnAt.isAfter(pickupAt)) {
                return false;
            }
        }
        UUID location = blocking.stream()
                .filter(r -> !r.returnAt.isAfter(pickupAt))
                .max(Comparator.comparing((Rental r) -> r.returnAt))
                .map(r -> r.returnBranchId)
                .orElse(vehicle.homeBranchId);
        if (!location.equals(pickupBranchId)) {
            return false;
        }
        return blocking.stream()
                .filter(r -> !r.pickupAt.isBefore(returnAt))
                .min(Comparator.comparing((Rental r) -> r.pickupAt))
                .map(r -> r.pickupBranchId.equals(returnBranchId))
                .orElse(true);
    }
}
