package com.waylo.hotelservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Short TTL in-memory cache for the active-organization lookup used by the public hotel projection.
 * Only successful lookups are cached: when auth-service fails the exception propagates (callers fail
 * closed) and no stale value is served.
 */
@Component
public class ActiveOrganizationIdsCache {

    private final AuthAccessClient authAccessClient;
    private final long ttlNanos;

    private volatile List<UUID> cached;
    private volatile long loadedAtNanos;

    public ActiveOrganizationIdsCache(AuthAccessClient authAccessClient,
                                      @Value("${hotel.public-projection.org-cache-ttl-seconds:30}") long ttlSeconds) {
        this.authAccessClient = authAccessClient;
        this.ttlNanos = ttlSeconds * 1_000_000_000L;
    }

    public List<UUID> get() {
        List<UUID> snapshot = cached;
        if (snapshot != null && ttlNanos > 0 && System.nanoTime() - loadedAtNanos < ttlNanos) {
            return snapshot;
        }
        List<UUID> fresh = authAccessClient.activeOrganizationIds();
        if (fresh == null) {
            return null;
        }
        cached = List.copyOf(fresh);
        loadedAtNanos = System.nanoTime();
        return cached;
    }
}
