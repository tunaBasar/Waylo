package com.waylo.paymentservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IbanValidatorTest {

    static final String VALID = "TR330006100519786457841326";

    @Test
    void validGroupedAndUngrouped_normalizeTo26Chars() {
        assertThat(IbanValidator.validate("TR33 0006 1005 1978 6457 8413 26")).isEqualTo(VALID).hasSize(26);
        assertThat(IbanValidator.validate(VALID)).isEqualTo(VALID);
        assertThat(IbanValidator.validate("TR33-0006-1005-1978-6457-8413-26")).isEqualTo(VALID);
    }

    @Test
    void lowercase_isAcceptedAfterNormalize() {
        assertThat(IbanValidator.validate("tr33 0006 1005 1978 6457 8413 26")).isEqualTo(VALID);
    }

    @Test
    void changedLastDigit_failsChecksum() {
        assertThatThrownBy(() -> IbanValidator.validate("TR33 0006 1005 1978 6457 8413 27"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("checksum");
        assertThat(IbanValidator.isValid("TR330006100519786457841327")).isFalse();
    }

    @Test
    void wrongLength_isRejected() {
        assertThat(IbanValidator.isValid("TR3300061005197864578413 2")).isFalse();      // 25
        assertThat(IbanValidator.isValid("TR33000610051978645784132600")).isFalse();     // 28
        assertThat(IbanValidator.isValid(VALID + "0")).isFalse();                        // 27
        assertThat(IbanValidator.isValid(VALID.substring(0, 25))).isFalse();             // 25
    }

    @Test
    void nonTurkishIban_isRejected_evenWithValidChecksum() {
        assertThat(IbanValidator.mod97Ok("DE89370400440532013000")).isTrue();
        assertThat(IbanValidator.isValid("DE89 3704 0044 0532 0130 00")).isFalse();
    }

    @Test
    void lettersInDigitPart_areRejected() {
        assertThat(IbanValidator.isValid("TR33 0006 1005 1978 6457 8413 2A")).isFalse();
        assertThat(IbanValidator.isValid("TRAB 0006 1005 1978 6457 8413 26")).isFalse();
    }

    @Test
    void nullAndBlank_areRejected() {
        assertThat(IbanValidator.isValid(null)).isFalse();
        assertThat(IbanValidator.isValid("   ")).isFalse();
    }

    @Test
    void errorMessage_neverContainsTheIban() {
        assertThatThrownBy(() -> IbanValidator.validate("TR33 0006 1005 1978 6457 8413 27"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("0006 1005").doesNotContain("00061005"));
    }
}
