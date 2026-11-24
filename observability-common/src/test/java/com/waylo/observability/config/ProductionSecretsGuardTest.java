package com.waylo.observability.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecretsGuardTest {

    private static final String STRONG = "Zk3vQ8nL0pXw7RtY2uHs5BdFg9JaMc1EoVi4NqTz6Kb=";

    private MockEnvironment prod() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }

    @Test
    void noOpWithoutProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("CONFIG_PASSWORD", "dev-config-password");
        env.setProperty("JWT_SECRET", "");
        assertThatCode(() -> ProductionSecretsGuard.check(env)).doesNotThrowAnyException();
    }

    @Test
    void failsOnDevLiteral() {
        MockEnvironment env = prod();
        env.setProperty("CONFIG_PASSWORD", "dev-config-password");
        assertThatThrownBy(() -> ProductionSecretsGuard.check(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIG_PASSWORD");
    }

    @Test
    void failsOnShortSecret() {
        MockEnvironment env = prod();
        env.setProperty("JWT_SECRET", "too-short");
        assertThatThrownBy(() -> ProductionSecretsGuard.check(env)).hasMessageContaining("JWT_SECRET");
    }

    @Test
    void failsOnBlank() {
        MockEnvironment env = prod();
        env.setProperty("EUREKA_PASSWORD", "  ");
        assertThatThrownBy(() -> ProductionSecretsGuard.check(env)).hasMessageContaining("EUREKA_PASSWORD");
    }

    @Test
    void passesOnStrongValues() {
        MockEnvironment env = prod();
        env.setProperty("CONFIG_PASSWORD", STRONG);
        env.setProperty("EUREKA_PASSWORD", STRONG);
        env.setProperty("JWT_SECRET", STRONG);
        env.setProperty("VOUCHER_SECRET", STRONG);
        env.setProperty("WAYLO_PAYOUT_IBAN_KEY", STRONG);
        assertThatCode(() -> ProductionSecretsGuard.check(env)).doesNotThrowAnyException();
    }

    @Test
    void messageNeverContainsTheValue() {
        MockEnvironment env = prod();
        env.setProperty("CONFIG_PASSWORD", "dev-config-password");
        env.setProperty("VOUCHER_SECRET", "short-voucher-value");
        assertThatThrownBy(() -> ProductionSecretsGuard.check(env)).satisfies(e -> {
            assertThat(e.getMessage()).doesNotContain("dev-config-password");
            assertThat(e.getMessage()).doesNotContain("short-voucher-value");
        });
    }
}
