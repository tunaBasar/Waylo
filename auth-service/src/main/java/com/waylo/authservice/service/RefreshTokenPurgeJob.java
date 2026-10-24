package com.waylo.authservice.service;

import com.waylo.authservice.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RefreshTokenPurgeJob {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPurgeJob.class);
    private final RefreshTokenRepository repository;
    public RefreshTokenPurgeJob(RefreshTokenRepository repository) { this.repository = repository; }
    @org.springframework.transaction.annotation.Transactional
    @Scheduled(cron = "${auth.refresh-token.purge-cron:0 30 3 * * *}")
    public void purge() {
        Instant now = Instant.now();
        int count = repository.purgeExpiredOrOldRevoked(now, now.minus(Duration.ofDays(7)));
        log.info("Purged {} refresh tokens", count);
    }
}
