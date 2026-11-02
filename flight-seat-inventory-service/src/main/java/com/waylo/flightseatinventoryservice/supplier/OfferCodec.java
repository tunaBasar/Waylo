package com.waylo.flightseatinventoryservice.supplier;

import com.waylo.flightseatinventoryservice.exception.BusinessRuleException;
import com.waylo.flightseatinventoryservice.exception.ConflictException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/** Stateless signed offer ids: base64url(payload).base64url(hmac). The price is inside the signature. */
public final class OfferCodec {

    public record Decoded(String flightNumber, String cabin, BigDecimal price, String currency, Instant expiresAt) {}

    private final byte[] key;

    public OfferCodec(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Offer signing secret must be configured");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String encode(String flightNumber, String cabin, BigDecimal price, String currency, Instant expiresAt) {
        String payload = flightNumber + "|" + cabin + "|" + price.toPlainString() + "|" + currency + "|"
                + expiresAt.getEpochSecond();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + b64.encodeToString(sign(payload));
    }

    public Decoded decode(String offerId, Instant now) {
        if (offerId == null) throw invalid();
        int dot = offerId.indexOf('.');
        if (dot <= 0 || dot == offerId.length() - 1) throw invalid();
        String payload;
        byte[] sig;
        try {
            payload = new String(Base64.getUrlDecoder().decode(offerId.substring(0, dot)), StandardCharsets.UTF_8);
            sig = Base64.getUrlDecoder().decode(offerId.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(sig, sign(payload))) throw invalid();
        String[] p = payload.split("\\|");
        if (p.length != 5) throw invalid();
        Instant expires;
        BigDecimal price;
        try {
            expires = Instant.ofEpochSecond(Long.parseLong(p[4]));
            price = new BigDecimal(p[2]);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (!expires.isAfter(now)) {
            throw new ConflictException("OFFER_EXPIRED", "Offer expired, search again");
        }
        return new Decoded(p[0], p[1], price, p[3], expires);
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException("INVALID_OFFER", "Offer id is not valid");
    }
}
