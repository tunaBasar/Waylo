package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.DailyRate;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Single source of truth for which nightly rate row prices a requested occupancy.
 *
 * <p>Rule: an exact row for the requested occupancy wins. Otherwise the row with the smallest
 * occupancy M greater than the requested one is used, as long as M does not exceed the room
 * type's maxOccupancy. It never falls back downward. Stateless and repository-free: callers pass
 * the rate rows of one (roomType, ratePlan, night).
 */
@Component
public class OccupancyRateResolver {

    /**
     * @param nightRows    rate rows of ONE room type, rate plan and night (any occupancies)
     * @param occupancy    requested occupancy
     * @param maxOccupancy the room type's maxOccupancy
     * @return the resolved row, or null when the occupancy cannot be priced
     */
    public DailyRate resolve(Collection<DailyRate> nightRows, int occupancy, int maxOccupancy) {
        if (nightRows == null || occupancy < 1 || occupancy > maxOccupancy) {
            return null;
        }
        DailyRate best = null;
        for (DailyRate row : nightRows) {
            int m = row.getOccupancy();
            if (m < occupancy || m > maxOccupancy) {
                continue;
            }
            if (best == null || m < best.getOccupancy()) {
                best = row;
            }
        }
        return best;
    }
}
