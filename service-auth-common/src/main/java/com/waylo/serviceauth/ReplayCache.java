package com.waylo.serviceauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/** Bounded, thread safe memory of accepted (iss, jti) pairs. Entries live until exp + skew, expired ones are evicted on access. */
final class ReplayCache {

    static final int MAX_ENTRIES = 100_000;
    private static final Logger log = LoggerFactory.getLogger(ReplayCache.class);

    enum Result { ACCEPTED, REPLAYED, FULL }

    private record Entry(String key, long until) {
    }

    private final int cap;
    private final Map<String, Long> seen = new HashMap<>();
    private final PriorityQueue<Entry> byExpiry = new PriorityQueue<>((a, b) -> Long.compare(a.until, b.until));

    ReplayCache(int cap) {
        this.cap = cap;
    }

    /** Records the pair if unseen. nowSeconds and expSeconds are epoch seconds. */
    synchronized Result register(String iss, String jti, long expSeconds, long nowSeconds) {
        if (jti == null || jti.isBlank()) return Result.REPLAYED;
        Entry head;
        while ((head = byExpiry.peek()) != null && head.until < nowSeconds) {
            byExpiry.poll();
            seen.remove(head.key);
        }
        String key = iss + '\n' + jti;
        if (seen.containsKey(key)) return Result.REPLAYED;
        if (seen.size() >= cap) {
            log.error("Service token replay cache full ({} entries, none expired): rejecting token from {} (fail closed)", cap, iss);
            return Result.FULL;
        }
        long until = expSeconds + ServiceTokens.SKEW_SECONDS;
        seen.put(key, until);
        byExpiry.add(new Entry(key, until));
        return Result.ACCEPTED;
    }
}
