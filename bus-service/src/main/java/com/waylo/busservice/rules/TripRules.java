package com.waylo.busservice.rules;

import com.waylo.busservice.config.BusSettings;
import com.waylo.busservice.dto.BusDtos.FareItem;
import com.waylo.busservice.dto.BusDtos.StopItem;
import com.waylo.busservice.exception.BusinessRuleException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure validation of trip definitions. */
public final class TripRules {

    private TripRules() { }

    public static Instant toInstant(LocalDateTime local) { return local.atZone(BusSettings.ZONE).toInstant(); }

    /** Times strictly increase along the route, the first departure is in the future, no terminal repeats. */
    public static void validateStops(Instant now, List<StopItem> stops) {
        if (stops.size() < 2) throw new BusinessRuleException("INVALID_STOPS", "A trip needs at least two stops");
        Set<UUID> seen = new HashSet<>();
        Instant previous = null;
        for (int i = 0; i < stops.size(); i++) {
            StopItem s = stops.get(i);
            if (!seen.add(s.terminalId())) {
                throw new BusinessRuleException("INVALID_STOPS", "A terminal cannot appear twice on one trip");
            }
            Instant arr = toInstant(s.arrivalAt());
            Instant dep = toInstant(s.departureAt());
            if (dep.isBefore(arr)) throw new BusinessRuleException("INVALID_STOPS", "Departure is before arrival at stop " + i);
            if (previous != null && !arr.isAfter(previous)) {
                throw new BusinessRuleException("INVALID_STOPS", "Stop times must increase along the route");
            }
            previous = dep;
        }
        if (!toInstant(stops.get(0).departureAt()).isAfter(now)) {
            throw new BusinessRuleException("INVALID_STOPS", "The first departure must be in the future");
        }
    }

    /** Fare pairs are in range, unique, and the full route fare exists. */
    public static void validateFares(int stopCount, List<FareItem> fares) {
        Set<String> seen = new HashSet<>();
        boolean full = false;
        for (FareItem f : fares) {
            if (f.fromSeq() >= f.toSeq() || f.toSeq() > stopCount - 1) {
                throw new BusinessRuleException("INVALID_FARES", "Fare " + f.fromSeq() + "->" + f.toSeq() + " is out of range");
            }
            if (!seen.add(f.fromSeq() + ">" + f.toSeq())) {
                throw new BusinessRuleException("INVALID_FARES", "Duplicate fare " + f.fromSeq() + "->" + f.toSeq());
            }
            if (f.fromSeq() == 0 && f.toSeq() == stopCount - 1) full = true;
        }
        if (!full) throw new BusinessRuleException("INVALID_FARES", "A fare for the full route is required");
    }
}
