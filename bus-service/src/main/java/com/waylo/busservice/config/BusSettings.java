package com.waylo.busservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class BusSettings {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    public static final int MAX_SEATS_PER_BOOKING = 5;

    private final boolean salesEnabled;
    private final int holdTtlMinutes;
    private final int salesCloseMinutes;

    public BusSettings(@Value("${waylo.bus.sales-enabled:false}") boolean salesEnabled,
                       @Value("${waylo.bus.hold-ttl-minutes:15}") int holdTtlMinutes,
                       @Value("${waylo.bus.sales-close-minutes:30}") int salesCloseMinutes) {
        this.salesEnabled = salesEnabled;
        this.holdTtlMinutes = holdTtlMinutes;
        this.salesCloseMinutes = salesCloseMinutes;
    }

    /** Legal gate: false until the lawyer approves public bus ticket sales. */
    public boolean salesEnabled() { return salesEnabled; }

    public int holdTtlMinutes() { return holdTtlMinutes; }

    /** Sales (and holds) stop this many minutes before the boarding departure. */
    public int salesCloseMinutes() { return salesCloseMinutes; }
}
