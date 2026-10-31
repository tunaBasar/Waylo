package com.waylo.packagebookingservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Per-organization failed redemption counter in Redis (fixed one-minute window). More than {@link #MAX_FAILURES}
 * failures in the window block further attempts (429). Fails open if Redis is unreachable: a Redis outage must not stop
 * the front desk from redeeming, and the secret code is 128-bit so brute force is infeasible anyway.
 */
@Component
public class RedemptionAttemptLimiter {

    public static final int MAX_FAILURES = 30;
    private static final Logger log = LoggerFactory.getLogger(RedemptionAttemptLimiter.class);

    private final StringRedisTemplate redis;

    public RedemptionAttemptLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(UUID orgId) {
        return "voucher:redeem:fail:" + orgId + ":" + (System.currentTimeMillis() / 60_000L);
    }

    public boolean isBlocked(UUID orgId) {
        try {
            String v = redis.opsForValue().get(key(orgId));
            return v != null && Long.parseLong(v) > MAX_FAILURES;
        } catch (RuntimeException e) {
            log.warn("Redemption limiter unavailable (fail open)");
            return false;
        }
    }

    public void recordFailure(UUID orgId) {
        try {
            String k = key(orgId);
            Long n = redis.opsForValue().increment(k);
            if (n != null && n == 1L) redis.expire(k, Duration.ofSeconds(90));
        } catch (RuntimeException e) {
            log.warn("Redemption limiter unavailable (fail open)");
        }
    }
}
