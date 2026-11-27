package com.waylo.busservice.rules;

import com.waylo.busservice.dto.BusDtos.ScheduleStopItem;
import com.waylo.busservice.dto.BusDtos.StopItem;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure planning of the departures a weekly timetable still has to create. Europe/Istanbul has no DST. */
public final class TripScheduleGenerator {

    public static final int MIN_LEAD_MINUTES = 60;

    private TripScheduleGenerator() { }

    public record Plan(List<LocalDateTime> departures, int skippedExisting, int skippedPast) { }

    /** Dates in [max(today, validFrom), min(today + horizon - 1, validTo)] on the mask's weekdays. */
    public static List<LocalDate> dates(int weekdays, LocalDate validFrom, LocalDate validTo, int horizonDays,
                                        LocalDate today) {
        LocalDate from = validFrom.isAfter(today) ? validFrom : today;
        LocalDate to = today.plusDays(horizonDays - 1L);
        if (validTo != null && validTo.isBefore(to)) to = validTo;
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (ScheduleRules.runsOn(weekdays, d.getDayOfWeek())) out.add(d);
        }
        return out;
    }

    /** @param existing departure instants of trips with the schedule's (organization, code), any status */
    public static Plan plan(int weekdays, LocalTime time, LocalDate validFrom, LocalDate validTo, int horizonDays,
                            LocalDate today, Instant now, Set<Instant> existing) {
        Instant earliest = now.plusSeconds(MIN_LEAD_MINUTES * 60L);
        List<LocalDateTime> create = new ArrayList<>();
        int skippedExisting = 0;
        int skippedPast = 0;
        for (LocalDate d : dates(weekdays, validFrom, validTo, horizonDays, today)) {
            LocalDateTime dep = d.atTime(time);
            Instant at = TripRules.toInstant(dep);
            if (at.isBefore(earliest)) skippedPast++;
            else if (existing.contains(at)) skippedExisting++;
            else create.add(dep);
        }
        return new Plan(create, skippedExisting, skippedPast);
    }

    /** Applies the offsets to one departure. */
    public static List<StopItem> stops(LocalDateTime departure, List<ScheduleStopItem> offsets) {
        List<StopItem> out = new ArrayList<>(offsets.size());
        for (ScheduleStopItem o : offsets) {
            out.add(new StopItem(o.terminalId(), departure.plusMinutes(o.arrivalOffsetMinutes()),
                    departure.plusMinutes(o.departureOffsetMinutes())));
        }
        return out;
    }
}
