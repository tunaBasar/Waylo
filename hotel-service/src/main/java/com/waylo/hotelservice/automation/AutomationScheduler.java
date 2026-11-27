package com.waylo.hotelservice.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily rolling generation, Europe/Istanbul. Scheduler threads have no TenantContext, so RLS runs in SERVICE mode. */
@Component
public class AutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);

    private final AutomationService service;
    private final boolean enabled;

    public AutomationScheduler(AutomationService service,
                               @Value("${waylo.automation.enabled:true}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${waylo.automation.cron:0 10 3 * * *}", zone = "Europe/Istanbul")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            service.applyAllHotels();
        } catch (Exception e) {
            log.error("Hotel automation scheduler failed", e);
        }
    }
}
