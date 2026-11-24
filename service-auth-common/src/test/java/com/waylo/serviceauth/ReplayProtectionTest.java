package com.waylo.serviceauth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReplayProtectionTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final String SELF = "package-booking-service";
    private static final KeyPair HOTEL = gen();
    private static final KeyPair PAYMENT = gen();
    private static final Map<String, PublicKey> KEYS = Map.of("hotel-service", HOTEL.getPublic(), "payment-service", PAYMENT.getPublic());

    private static final class MutableClock extends Clock {
        volatile Instant now = NOW;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();

    private static KeyPair gen() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ServiceAuthenticator auth(int cap) {
        return new ServiceAuthenticator(SELF,
                List.of(new ServiceAuthenticator.Route("/internal", Set.of("hotel-service", "payment-service"), true)),
                KEYS::get, clock, cap);
    }

    private static ServiceAuthenticator.Decision use(ServiceAuthenticator a, String token) {
        return a.decide(a.routeFor("/internal/x"), token);
    }

    private static String tok(String iss, KeyPair kp) {
        return ServiceTokens.sign(iss, SELF, kp.getPrivate(), NOW, 30);
    }

    /** Token with a chosen claim tail, signed properly. */
    private static String raw(String iss, KeyPair kp, String jtiClaim) throws Exception {
        long iat = NOW.getEpochSecond();
        String claims = "{\"iss\":\"" + iss + "\",\"aud\":\"" + SELF + "\",\"iat\":" + iat + ",\"exp\":" + (iat + 30) + jtiClaim + "}";
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        String input = e.encodeToString("{\"alg\":\"EdDSA\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + e.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(kp.getPrivate());
        s.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + e.encodeToString(s.sign());
    }

    @Test
    void secondUseOfSameTokenIsRejected() {
        var a = auth(100);
        String t = tok("hotel-service", HOTEL);
        assertThat(use(a, t).allowed()).isTrue();
        var d = use(a, t);
        assertThat(d.allowed()).isFalse();
        assertThat(d.status()).isEqualTo(401);
        assertThat(d.reason()).isEqualTo("replayed token");
    }

    @Test
    void sameJtiFromAnotherIssuerIsAccepted() throws Exception {
        var a = auth(100);
        String jti = ",\"jti\":\"shared-jti\"";
        assertThat(use(a, raw("hotel-service", HOTEL, jti)).allowed()).isTrue();
        assertThat(use(a, raw("payment-service", PAYMENT, jti)).allowed()).isTrue();
        assertThat(use(a, raw("hotel-service", HOTEL, jti)).reason()).isEqualTo("replayed token");
    }

    @Test
    void entryIsEvictedAfterExpPlusSkew() {
        var a = auth(1);
        String t = tok("hotel-service", HOTEL);
        assertThat(use(a, t).allowed()).isTrue();
        // exp = NOW+30, still within skew: remembered
        clock.now = NOW.plusSeconds(30 + ServiceTokens.SKEW_SECONDS);
        assertThat(use(a, t).reason()).isEqualTo("replayed token");
        // expired: the token itself is now rejected as expired, and the slot is free again
        clock.now = NOW.plusSeconds(30 + ServiceTokens.SKEW_SECONDS + 1);
        assertThat(use(a, t).reason()).isEqualTo("expired");
        clock.now = clock.now.plusSeconds(0);
        String fresh = ServiceTokens.sign("hotel-service", SELF, HOTEL.getPrivate(), clock.now, 30);
        assertThat(use(a, fresh).allowed()).isTrue();
    }

    @Test
    void missingOrBlankJtiIsRejected() throws Exception {
        var a = auth(100);
        assertThat(use(a, raw("hotel-service", HOTEL, "")).allowed()).isFalse();
        var d = use(a, raw("hotel-service", HOTEL, ",\"jti\":\"\""));
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).isEqualTo("missing jti");
    }

    @Test
    void fullCacheFailsClosedUntilEntriesExpire() {
        var a = auth(2);
        assertThat(use(a, tok("hotel-service", HOTEL)).allowed()).isTrue();
        assertThat(use(a, tok("hotel-service", HOTEL)).allowed()).isTrue();
        var d = use(a, tok("hotel-service", HOTEL));
        assertThat(d.allowed()).isFalse();
        assertThat(d.status()).isEqualTo(401);
        assertThat(d.reason()).isEqualTo("replay cache full");
        clock.now = NOW.plusSeconds(40);
        String fresh = ServiceTokens.sign("hotel-service", SELF, HOTEL.getPrivate(), clock.now, 30);
        assertThat(use(a, fresh).allowed()).isTrue();
    }

    @Test
    void concurrentDoubleUseAcceptsExactlyOnce() throws Exception {
        for (int round = 0; round < 50; round++) {
            var a = auth(100);
            String t = tok("hotel-service", HOTEL);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger accepted = new AtomicInteger();
            ExecutorService ex = Executors.newFixedThreadPool(2);
            try {
                List<Future<?>> fs = List.of(
                        ex.submit(() -> { go.await(); if (use(a, t).allowed()) accepted.incrementAndGet(); return null; }),
                        ex.submit(() -> { go.await(); if (use(a, t).allowed()) accepted.incrementAndGet(); return null; }));
                go.countDown();
                for (Future<?> f : fs) f.get();
            } finally {
                ex.shutdownNow();
            }
            assertThat(accepted.get()).isEqualTo(1);
        }
    }
}
