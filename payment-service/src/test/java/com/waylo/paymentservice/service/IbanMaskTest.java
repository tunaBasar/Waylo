package com.waylo.paymentservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IbanMaskTest {

    @Test
    void mask_showsOnlyTheLastTwoDigits() {
        String masked = IbanMask.mask("1326");
        assertThat(masked).isEqualTo("TR** **** **** **** **** **26");
        assertThat(masked).doesNotContain("1326").doesNotContain("13").doesNotContain("33");
    }

    @Test
    void mask_ofTheFullIban_neverContainsIt() {
        String iban = IbanValidator.validate("TR33 0006 1005 1978 6457 8413 26");
        String masked = IbanMask.mask(IbanMask.last4(iban));
        assertThat(masked).doesNotContain(iban).doesNotContain("00061005").doesNotContain("1005").doesNotContain("1978")
                .doesNotContain("6457").doesNotContain("8413");
        assertThat(IbanMask.last4(iban)).isEqualTo("1326");
    }

    @Test
    void mask_ofMissingOrMalformedLast4_revealsNothing() {
        assertThat(IbanMask.mask(null)).isEqualTo("TR** **** **** **** **** ****");
        assertThat(IbanMask.mask("12")).isEqualTo("TR** **** **** **** **** ****");
    }
}
