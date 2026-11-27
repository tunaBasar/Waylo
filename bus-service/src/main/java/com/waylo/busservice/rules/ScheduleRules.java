package com.waylo.busservice.rules;

import com.waylo.busservice.dto.BusDtos.ScheduleRequest;
import com.waylo.busservice.dto.BusDtos.ScheduleStopItem;
import com.waylo.busservice.exception.BusinessRuleException;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure validation and weekday mask helpers of weekly timetables. */
public final class ScheduleRules {

    public static final int MAX_ROUTE_MINUTES = 4320;

    private ScheduleRules() { }

    /** Bit (dayOfWeek.getValue() - 1): MONDAY = 1 ... SUNDAY = 64. */
    public static int mask(Collection<DayOfWeek> days) {
        int m = 0;
        for (DayOfWeek d : days) m |= 1 << (d.getValue() - 1);
        return m;
    }

    public static List<DayOfWeek> days(int mask) {
        List<DayOfWeek> out = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) if ((mask & (1 << (d.getValue() - 1))) != 0) out.add(d);
        return out;
    }

    public static boolean runsOn(int mask, DayOfWeek d) { return (mask & (1 << (d.getValue() - 1))) != 0; }

    /** INVALID_SCHEDULE / INVALID_STOPS / INVALID_FARES. Terminals and bus are checked by the service. */
    public static void validate(ScheduleRequest req) {
        if (req.weekdays().isEmpty()) invalid("At least one weekday is required");
        if (new HashSet<>(req.weekdays()).size() != req.weekdays().size()) invalid("Weekdays must not repeat");
        if (req.horizonDays() < 1 || req.horizonDays() > 120) invalid("horizonDays must be between 1 and 120");
        if (req.validTo() != null && req.validTo().isBefore(req.validFrom())) invalid("validTo is before validFrom");
        validateOffsets(req.stops());
        TripRules.validateFares(req.stops().size(), req.fares());
    }

    static void validateOffsets(List<ScheduleStopItem> stops) {
        if (stops.size() < 2) throw new BusinessRuleException("INVALID_STOPS", "A schedule needs at least two stops");
        Set<UUID> seen = new HashSet<>();
        int previousDeparture = -1;
        for (int i = 0; i < stops.size(); i++) {
            ScheduleStopItem s = stops.get(i);
            if (!seen.add(s.terminalId())) {
                throw new BusinessRuleException("INVALID_STOPS", "A terminal cannot appear twice on one schedule");
            }
            if (s.departureOffsetMinutes() < s.arrivalOffsetMinutes()) invalid("Departure is before arrival at stop " + i);
            if (i == 0 && (s.arrivalOffsetMinutes() != 0 || s.departureOffsetMinutes() != 0)) {
                invalid("The first stop must have offsets 0/0");
            }
            if (i > 0 && s.arrivalOffsetMinutes() <= previousDeparture) invalid("Offsets must increase along the route");
            previousDeparture = s.departureOffsetMinutes();
        }
        if (previousDeparture > MAX_ROUTE_MINUTES) invalid("The route may not be longer than 3 days");
    }

    private static void invalid(String message) { throw new BusinessRuleException("INVALID_SCHEDULE", message); }
}
