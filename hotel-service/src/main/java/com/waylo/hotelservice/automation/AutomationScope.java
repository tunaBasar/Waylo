package com.waylo.hotelservice.automation;

import java.util.UUID;

/** What one generator run covers. Null ids mean "all". */
public record AutomationScope(UUID roomTypeId, UUID ratePlanId, boolean inventory, boolean rates) {

    public static final AutomationScope ALL = new AutomationScope(null, null, true, true);

    public static AutomationScope inventoryOf(UUID roomTypeId) {
        return new AutomationScope(roomTypeId, null, true, false);
    }

    public static AutomationScope ratesOf(UUID roomTypeId, UUID ratePlanId) {
        return new AutomationScope(roomTypeId, ratePlanId, false, true);
    }
}
