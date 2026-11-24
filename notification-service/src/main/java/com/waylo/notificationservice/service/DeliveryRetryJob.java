package com.waylo.notificationservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries FAILED notifications whose next_attempt_at is due. Rows are claimed with FOR UPDATE SKIP LOCKED, so instances never overlap. */
@Component
public class DeliveryRetryJob {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRetryJob.class);

    private final NotificationService notificationService;
    private final int batchSize;

    public DeliveryRetryJob(NotificationService notificationService,
                            @Value("${waylo.notifications.retry.batch-size:20}") int batchSize) {
        this.notificationService = notificationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${waylo.notifications.retry.interval-ms:60000}")
    public void run() {
        try {
            int n = notificationService.retryDue(batchSize);
            if (n > 0) log.info("Delivery retry batch processed: count={}", n);
        } catch (RuntimeException ex) {
            log.warn("Delivery retry batch failed: {}", ex.getClass().getSimpleName());
        }
    }
}
