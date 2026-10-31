package com.waylo.paymentservice.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Selects the provider from {@code waylo.payments.provider}. The app refuses to start when the provider is TEST under the
 * {@code prod} profile, or when the value is unknown. A real adapter (iyzico Pazaryeri) adds a branch here.
 */
@Configuration
public class PaymentProviderConfig {

    @Bean
    public PaymentProvider paymentProvider(@Value("${waylo.payments.provider:}") String provider, Environment environment) {
        return create(provider, environment.acceptsProfiles(Profiles.of("prod")));
    }

    static PaymentProvider create(String provider, boolean prodProfile) {
        String value = provider == null ? "" : provider.trim().toUpperCase();
        if (TestPaymentProvider.NAME.equals(value)) {
            if (prodProfile) {
                throw new IllegalStateException(
                        "waylo.payments.provider=TEST is not allowed with the prod profile, configure a real provider");
            }
            return new TestPaymentProvider();
        }
        throw new IllegalStateException("Unsupported or missing waylo.payments.provider '" + provider + "' (supported: TEST)");
    }
}
