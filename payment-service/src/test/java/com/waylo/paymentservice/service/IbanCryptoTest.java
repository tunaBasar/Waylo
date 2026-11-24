package com.waylo.paymentservice.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IbanCryptoTest {

    static final String IBAN = "TR330006100519786457841326";

    private static String key() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void roundTrip() {
        IbanCrypto c = new IbanCrypto(key());
        String ct = c.encrypt(IBAN);
        assertThat(ct).doesNotContain(IBAN);
        assertThat(c.decrypt(ct)).isEqualTo(IBAN);
    }

    @Test
    void twoEncryptionsOfTheSameIban_differ_randomIv() {
        IbanCrypto c = new IbanCrypto(key());
        assertThat(c.encrypt(IBAN)).isNotEqualTo(c.encrypt(IBAN));
    }

    @Test
    void tamperedCiphertext_failsAuthentication() {
        IbanCrypto c = new IbanCrypto(key());
        byte[] raw = Base64.getDecoder().decode(c.encrypt(IBAN));
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> c.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongKey_cannotDecrypt() {
        String ct = new IbanCrypto(key()).encrypt(IBAN);
        assertThatThrownBy(() -> new IbanCrypto(key()).decrypt(ct)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hash_isStable64HexAndKeyDependent() {
        String k = key();
        IbanCrypto c = new IbanCrypto(k);
        assertThat(c.hash(IBAN)).hasSize(64).matches("[0-9a-f]{64}").isEqualTo(new IbanCrypto(k).hash(IBAN));
        assertThat(c.hash(IBAN)).isNotEqualTo(new IbanCrypto(key()).hash(IBAN));
        assertThat(c.hash(IBAN)).isNotEqualTo(c.hash("TR980006100519786457841320"));
    }

    @Test
    void missingOrBlankKey_failsFastAtStartup() {
        assertThatThrownBy(() -> new IbanCrypto(null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("WAYLO_PAYOUT_IBAN_KEY");
        assertThatThrownBy(() -> new IbanCrypto("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new IbanCrypto("   ")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongLengthOrNotBase64_failsFast_withoutEchoingTheKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        String longKey = Base64.getEncoder().encodeToString(new byte[48]);
        assertThatThrownBy(() -> new IbanCrypto(shortKey)).isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(shortKey));
        assertThatThrownBy(() -> new IbanCrypto(longKey)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new IbanCrypto("not base64 !!!")).isInstanceOf(IllegalStateException.class);
    }
}
