package com.waylo.carrentalservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class CarSettings {

    public static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final boolean individualPartnersEnabled;
    private final int holdTtlMinutes;

    public CarSettings(@Value("${waylo.car.individual-partners-enabled:false}") boolean individualPartnersEnabled,
                       @Value("${waylo.car.hold-ttl-minutes:15}") int holdTtlMinutes) {
        this.individualPartnersEnabled = individualPartnersEnabled;
        this.holdTtlMinutes = holdTtlMinutes;
    }

    public boolean individualPartnersEnabled() { return individualPartnersEnabled; }

    public int holdTtlMinutes() { return holdTtlMinutes; }
}
