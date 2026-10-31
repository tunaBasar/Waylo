package com.waylo.paymentservice.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProviderConfigTest {

    @Test
    void testProviderRefusedUnderProdProfile() {
        assertThatThrownBy(() -> PaymentProviderConfig.create("TEST", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("prod");
    }

    @Test
    void testProviderAllowedOutsideProd() {
        PaymentProvider p = PaymentProviderConfig.create("test", false);
        assertThat(p).isInstanceOf(TestPaymentProvider.class);
        assertThat(p.name()).isEqualTo("TEST");
    }

    @Test
    void unknownOrMissingProviderFailsStartup() {
        assertThatThrownBy(() -> PaymentProviderConfig.create("", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaymentProviderConfig.create("IYZICO", false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testProviderTokens() {
        TestPaymentProvider p = new TestPaymentProvider();
        var req = (java.util.function.Function<String, PaymentProvider.AuthorizeRequest>) t -> new PaymentProvider.AuthorizeRequest(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), new java.math.BigDecimal("10.00"),
                "TRY", t, "k", java.util.List.of());
        assertThat(p.authorize(req.apply("TEST_OK")).success()).isTrue();
        var declined = p.authorize(req.apply("TEST_DECLINE"));
        assertThat(declined.success()).isFalse();
        assertThat(declined.failureCode()).isEqualTo("CARD_DECLINED");
        assertThatThrownBy(() -> p.authorize(req.apply("4242"))).isInstanceOf(IllegalArgumentException.class);
    }
}
