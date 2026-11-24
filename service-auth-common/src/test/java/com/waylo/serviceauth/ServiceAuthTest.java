package com.waylo.serviceauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceAuthTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final KeyPair HOTEL = gen();
    private static final KeyPair PAYMENT = gen();
    private static final KeyPair ROGUE = gen();
    private static final Map<String, PublicKey> KEYS = Map.of("hotel-service", HOTEL.getPublic(), "payment-service", PAYMENT.getPublic());

    private static KeyPair gen() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ServiceAuthenticator auth() {
        return new ServiceAuthenticator("package-booking-service",
                List.of(new ServiceAuthenticator.Route("/internal/orders/items", Set.of("hotel-service"), true),
                        new ServiceAuthenticator.Route("/internal/orders", Set.of("payment-service"), true),
                        new ServiceAuthenticator.Route("/api/bookings", Set.of("payment-service"), false)),
                KEYS::get, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String token(String iss, String aud, KeyPair kp, Instant at) {
        return ServiceTokens.sign(iss, aud, kp.getPrivate(), at, 30);
    }

    private static ServiceAuthenticator.Decision decide(ServiceAuthenticator a, String uri, String token) {
        return a.decide(a.routeFor(uri), token);
    }

    @Test
    void validTokenFromAllowedCallerIsAccepted() {
        var d = decide(auth(), "/internal/orders/items/5",
                token("hotel-service", "package-booking-service", HOTEL, NOW));
        assertThat(d.allowed()).isTrue();
        assertThat(d.caller()).isEqualTo("hotel-service");
    }

    @Test
    void expiredTokenIsRejected() {
        var d = decide(auth(), "/internal/orders/items/5",
                token("hotel-service", "package-booking-service", HOTEL, NOW.minusSeconds(120)));
        assertThat(d.status()).isEqualTo(401);
        assertThat(d.reason()).isEqualTo("expired");
    }

    @Test
    void futureTokenIsRejected() {
        var d = decide(auth(), "/internal/orders/items/5",
                token("hotel-service", "package-booking-service", HOTEL, NOW.plusSeconds(120)));
        assertThat(d.status()).isEqualTo(401);
    }

    @Test
    void wrongAudienceIsRejected() {
        var d = decide(auth(), "/internal/orders/items/5",
                token("hotel-service", "payment-service", HOTEL, NOW));
        assertThat(d.status()).isEqualTo(401);
        assertThat(d.reason()).isEqualTo("wrong audience");
    }

    @Test
    void unknownIssuerIsRejected() {
        var d = decide(auth(), "/internal/orders/items/5",
                token("rogue-service", "package-booking-service", ROGUE, NOW));
        assertThat(d.status()).isEqualTo(401);
        assertThat(d.reason()).isEqualTo("unknown issuer");
    }

    @Test
    void forgedIssuerWithOtherKeyIsRejected() {
        var d = decide(auth(), "/internal/orders/items/5",
                token("hotel-service", "package-booking-service", ROGUE, NOW));
        assertThat(d.reason()).isEqualTo("bad signature");
    }

    @Test
    void callerNotInAllowlistGets403() {
        // payment-service may call /internal/orders but not /internal/orders/items
        var d = decide(auth(), "/internal/orders/items/5",
                token("payment-service", "package-booking-service", PAYMENT, NOW));
        assertThat(d.status()).isEqualTo(403);
    }

    @Test
    void tamperedPayloadIsRejected() {
        String t = token("payment-service", "package-booking-service", PAYMENT, NOW);
        String[] p = t.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8)
                .replace("payment-service\",\"aud", "hotel-service\",\"aud");
        String forged = p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + "." + p[2];
        var d = decide(auth(), "/internal/orders/items/5", forged);
        assertThat(d.status()).isEqualTo(401);
        assertThat(d.reason()).isEqualTo("bad signature");
    }

    @Test
    void tamperedSignatureAndGarbageAreRejected() {
        String t = token("hotel-service", "package-booking-service", HOTEL, NOW);
        char last = t.charAt(t.length() - 5);
        String bad = t.substring(0, t.length() - 5) + (last == 'A' ? 'B' : 'A') + t.substring(t.length() - 4);
        var a = auth();
        assertThat(decide(a, "/internal/orders/items/5", bad).status()).isEqualTo(401);
        assertThat(decide(a, "/internal/orders/items/5", "not.a.jwt").status()).isEqualTo(401);
        assertThat(decide(a, "/internal/orders/items/5", "x").status()).isEqualTo(401);
    }

    @Test
    void ttlIsCappedAtSixtySeconds() {
        String t = ServiceTokens.sign("hotel-service", "package-booking-service", HOTEL.getPrivate(), NOW, 3600);
        assertThatThrownBy(() -> ServiceTokens.verify(t, "package-booking-service", KEYS::get, NOW.plusSeconds(70)))
                .hasMessage("expired");
    }

    @Test
    void legacyInternalTokenHeaderIsNotAccepted() throws Exception {
        var a = auth();
        assertThat(decide(a, "/internal/orders/1", null).status()).isEqualTo(401);
        ServiceAuthFilter f = new ServiceAuthFilter(a);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/orders/1");
        req.addHeader("X-Internal-Token", "l".repeat(40));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void unconfiguredInternalPathFailsClosedAndOtherPathsAreUnprotected() {
        var a = auth();
        assertThat(decide(a, "/internal/other", token("hotel-service", "package-booking-service", HOTEL, NOW)).status()).isEqualTo(403);
        assertThat(a.routeFor("/internalx")).isNull();
        assertThat(a.routeFor("/api/orders")).isNull();
        assertThat(a.routeFor("/internal/ordersx").path()).isEqualTo("/internal");
    }

    @Test
    void filterRejectsMissingAndAcceptsValidOnRequiredRoute() throws Exception {
        ServiceAuthFilter f = new ServiceAuthFilter(auth());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/orders/7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();

        req = new MockHttpServletRequest("GET", "/internal/orders/7");
        req.addHeader(ServiceTokens.HEADER, token("payment-service", "package-booking-service", PAYMENT, NOW));
        res = new MockHttpServletResponse();
        chain = new MockFilterChain();
        f.doFilter(req, res, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(ServiceAuthFilter.CALLER_ATTRIBUTE)).isEqualTo("payment-service");
    }

    @Test
    void filterPassesOptionalRouteWithoutMarkingInvalidCaller() throws Exception {
        ServiceAuthFilter f = new ServiceAuthFilter(auth());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/bookings/1");
        req.addHeader("X-Internal-Token", "l".repeat(40));
        req.setAttribute(ServiceAuthFilter.CALLER_ATTRIBUTE, "spoofed");
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(ServiceAuthFilter.isServiceCall(req)).isFalse();
    }

    @Test
    void keysRoundTripFromBase64Der() {
        String priv = Base64.getEncoder().encodeToString(HOTEL.getPrivate().getEncoded());
        String pub = Base64.getEncoder().encodeToString(HOTEL.getPublic().getEncoded());
        String t = ServiceTokens.sign("hotel-service", "x", ServiceTokens.privateKey(priv), NOW, 30);
        PublicKey k = ServiceTokens.publicKey(pub);
        assertThat(t).isNotBlank();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> ServiceTokens.verify(t, "x", i -> k, NOW));
        assertThat(ServiceTokens.publicKeyEnvName("package-booking-service")).isEqualTo("WAYLO_SERVICE_KEYS_PACKAGE_BOOKING_SERVICE");
    }
}
