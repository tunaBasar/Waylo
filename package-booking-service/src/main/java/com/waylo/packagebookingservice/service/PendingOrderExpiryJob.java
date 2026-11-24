package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RM-27: closes stale PENDING orders. A PENDING order whose hold_expires_at is older than now minus the grace period becomes
 * EXPIRED (holds and pricing reservation released, ORDER_EXPIRED outbox event). Each order is handled in its own transaction
 * by {@link OrderService#expireIfStale}, which locks the row (SKIP LOCKED) and re-checks the state, so it is idempotent and
 * never touches CONFIRMED, CANCELLED or FAILED orders. "now" comes from the injected Clock so tests can drive it.
 */
@Component
public class PendingOrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderExpiryJob.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock clock;
    private final int batchSize;
    private final Duration grace;

    public PendingOrderExpiryJob(OrderRepository orderRepository, OrderService orderService, Clock businessClock,
                                 @Value("${waylo.orders.expiry.batch-size:100}") int batchSize,
                                 @Value("${waylo.orders.expiry.grace-seconds:120}") long graceSeconds) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.clock = businessClock;
        this.batchSize = batchSize;
        this.grace = Duration.ofSeconds(graceSeconds);
    }

    @Scheduled(fixedDelayString = "${waylo.orders.expiry.interval-ms:60000}", initialDelay = 30000)
    public void scheduled() {
        try {
            int n = expireStale();
            if (n > 0) log.info("Pending order expiry job expired {} order(s)", n);
        } catch (RuntimeException e) {
            log.warn("Pending order expiry job failed, will retry: {}", e.getMessage());
        }
    }

    /** Returns the number of orders expired in this run. */
    public int expireStale() {
        Instant cutoff = clock.instant().minus(grace);
        List<UUID> ids = orderRepository.findExpiredPendingIds(cutoff, PageRequest.of(0, batchSize));
        int done = 0;
        for (UUID id : ids) {
            try {
                if (orderService.expireIfStale(id, cutoff)) done++;
            } catch (RuntimeException e) {
                log.warn("Expiring order {} failed, will retry: {}", id, e.getMessage());
            }
        }
        return done;
    }
}
