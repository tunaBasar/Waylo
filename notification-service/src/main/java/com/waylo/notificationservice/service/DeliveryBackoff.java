package com.waylo.notificationservice.service;

import java.time.Duration;

/** Retry delay after the n-th failed attempt (n = retry_count, 0 = the first send). Beyond the table the last value is used. */
public final class DeliveryBackoff {

    static final Duration[] SCHEDULE = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(12)
    };

    private DeliveryBackoff() {}

    public static Duration delay(int failedAttempts) {
        int i = Math.max(0, Math.min(failedAttempts, SCHEDULE.length - 1));
        return SCHEDULE[i];
    }
}
