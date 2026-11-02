package com.waylo.flightseatinventoryservice.supplier;

import com.waylo.flightseatinventoryservice.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OfferCodecTest {

    static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void roundTrip() {
        OfferCodec c = new OfferCodec("s1");
        String id = c.encode("TK100", "BUSINESS", new BigDecimal("99.50"), "TRY", NOW.plusSeconds(60));
        OfferCodec.Decoded d = c.decode(id, NOW);
        assertEquals("TK100", d.flightNumber());
        assertEquals(new BigDecimal("99.50"), d.price());
    }

    @Test
    void otherSecretRejected() {
        String id = new OfferCodec("s1").encode("TK100", "ECONOMY", BigDecimal.TEN, "TRY", NOW.plusSeconds(60));
        assertThrows(BusinessRuleException.class, () -> new OfferCodec("s2").decode(id, NOW));
    }

    @Test
    void garbageRejected() {
        OfferCodec c = new OfferCodec("s1");
        assertThrows(BusinessRuleException.class, () -> c.decode("nonsense", NOW));
        assertThrows(BusinessRuleException.class, () -> c.decode(null, NOW));
    }
}
