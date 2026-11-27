package com.waylo.busservice.config;

import com.waylo.busservice.dto.BusDtos.ScheduleSummary;
import com.waylo.busservice.service.ScheduleRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily generation of weekly timetables (SERVICE mode: scheduler threads have no tenant context). */
@Component
@ConditionalOnProperty(name = "waylo.automation.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleAutomation {

    private static final Logger log = LoggerFactory.getLogger(ScheduleAutomation.class);

    private final ScheduleRunner runner;

    public ScheduleAutomation(ScheduleRunner runner) { this.runner = runner; }

    @Scheduled(cron = "${waylo.automation.cron:0 20 3 * * *}", zone = "Europe/Istanbul")
    public void run() {
        try {
            ScheduleSummary s = runner.applyAll(null);
            log.info("Trip schedules applied: {}", s);
        } catch (Exception ex) {
            log.error("Trip schedule run failed", ex);
        }
    }
}
