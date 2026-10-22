package com.waylo.hotelservice.config;

import com.waylo.hotelservice.service.HotelHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled expiry worker for HELD hotel holds.
 * Invokes the service via Spring proxy to ensure transactional behavior.
 */
@Component
public class ExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiryScheduler.class);

    private final HotelHoldService holdService;
    private final int batchSize;

    public ExpiryScheduler(HotelHoldService holdService,
                           @Value("${waylo.hold.expiry-batch-size:100}") int batchSize) {
        this.holdService = holdService;
        this.batchSize = batchSize;
    }

    /**
     * Expire HELD holds whose expiresAt <= now, in batches.
     * Runs every minute by default.
     */
    @Scheduled(fixedRateString = "${waylo.hold.expiry-interval-ms:60000}")
    public void expireExpiredHolds() {
        try {
            int expired = holdService.releaseExpired(batchSize);
            if (expired > 0) {
                log.info("Expiry scheduler: expired {} holds", expired);
            }
        } catch (Exception e) {
            log.error("Expiry scheduler failed", e);
        }
    }
}
