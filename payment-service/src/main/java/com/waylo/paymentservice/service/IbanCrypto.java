package com.waylo.paymentservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM for IBANs at rest (random 12 byte IV prefixed to the ciphertext, base64). The key comes from
 * WAYLO_PAYOUT_IBAN_KEY (base64 of exactly 32 bytes) and the service refuses to start without it. The duplicate-detection
 * hash is HMAC-SHA256 keyed with a key derived from the same secret (a different derivation label, so the two uses
 * never share key material).
 */
@Component
public class IbanCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom random = new SecureRandom();

    public IbanCrypto(@Value("${waylo.payouts.iban-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("WAYLO_PAYOUT_IBAN_KEY (waylo.payouts.iban-key) is required: base64 of 32 bytes");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("WAYLO_PAYOUT_IBAN_KEY must be valid base64 of exactly 32 bytes");
        }
        if (key.length != 32) {
            throw new IllegalStateException("WAYLO_PAYOUT_IBAN_KEY must be base64 of exactly 32 bytes");
        }
        this.aesKey = new SecretKeySpec(key, "AES");
        this.hmacKey = new SecretKeySpec(hmac(key, "waylo-payout-iban-hash-v1".getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
    }

    public String encrypt(String normalizedIban) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(normalizedIban.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("IBAN encryption failed");
        }
    }

    /** Throws IllegalStateException on tampered data or a wrong key. */
    public String decrypt(String ciphertext) {
        try {
            byte[] in = Base64.getDecoder().decode(ciphertext);
            if (in.length <= IV_BYTES) throw new IllegalStateException("IBAN decryption failed");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("IBAN decryption failed");
        }
    }

    /** Lowercase hex HMAC-SHA256 (64 chars) of the normalized IBAN. */
    public String hash(String normalizedIban) {
        return HexFormat.of().formatHex(hmac(hmacKey.getEncoded(), normalizedIban.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failed");
        }
    }
}
