package com.waylo.busservice.config;

import com.waylo.busservice.service.HoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HoldExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryScheduler.class);

    private final HoldService holds;

    public HoldExpiryScheduler(HoldService holds) { this.holds = holds; }

    @Scheduled(fixedRateString = "${waylo.bus.hold-expiry-interval-ms:60000}")
    public void expire() {
        try {
            int n = holds.expireOverdue();
            if (n > 0) log.info("Expired {} bus holds", n);
        } catch (Exception ex) {
            log.error("Bus hold expiry failed", ex);
        }
    }
}
