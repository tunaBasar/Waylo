package com.waylo.flightservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily generation run (contract 1). */
@Component
public class FlightScheduleJob {

    private static final Logger log = LoggerFactory.getLogger(FlightScheduleJob.class);

    private final FlightScheduleGenerator generator;
    private final boolean enabled;

    public FlightScheduleJob(FlightScheduleGenerator generator,
                             @Value("${waylo.automation.enabled:true}") boolean enabled) {
        this.generator = generator;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${waylo.automation.cron:0 30 3 * * *}", zone = "Europe/Istanbul")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            log.info("Daily flight schedule run: {}", generator.applyAll());
        } catch (RuntimeException ex) {
            log.error("Daily flight schedule run failed", ex);
        }
    }
}
