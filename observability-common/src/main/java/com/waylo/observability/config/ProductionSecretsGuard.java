package com.waylo.observability.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fails startup under the "prod" profile when a known secret is a dev default, blank or too short.
 * No-op in every other profile. Messages name the property, never its value.
 * To guard another secret, add one line to {@link #GUARDED}.
 */
public class ProductionSecretsGuard implements EnvironmentPostProcessor, Ordered {

    /** One guarded secret: names it may appear under, minimum length, known dev literals. */
    record Rule(Set<String> names, int minLength, Set<String> devLiterals) {
    }

    /** Single source of truth: guarded names, minimum lengths and dev literals. */
    static final List<Rule> GUARDED = List.of(
            new Rule(Set.of("CONFIG_PASSWORD"), 1, Set.of("dev-config-password")),
            new Rule(Set.of("EUREKA_PASSWORD"), 1, Set.of("dev-eureka-password")),
            new Rule(Set.of("JWT_SECRET"), 32, Set.of()),
            new Rule(Set.of("VOUCHER_SECRET", "waylo.voucher.secret"), 32, Set.of()),
            new Rule(Set.of("FLIGHT_OFFER_SECRET", "waylo.flight.offer-secret"), 32, Set.of()),
            new Rule(Set.of("WAYLO_PAYOUT_IBAN_KEY"), 32, Set.of()),
            new Rule(Set.of("WAYLO_SERVICE_PRIVATE_KEY"), 1, Set.of()),
            new Rule(Set.of("SPRING_DATASOURCE_PASSWORD", "spring.datasource.password"), 1, Set.of()),
            new Rule(Set.of("SPRING_MAIL_PASSWORD", "spring.mail.password"), 1, Set.of()));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        check(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void check(ConfigurableEnvironment environment) {
        if (!isProd(environment)) {
            return;
        }
        List<String> problems = new ArrayList<>();
        for (Rule rule : GUARDED) {
            for (String name : rule.names()) {
                String value = environment.getProperty(name);
                if (value == null) {
                    continue; // not used by this service
                }
                if (value.isBlank()) {
                    problems.add(name + " is blank");
                } else if (rule.devLiterals().contains(value)) {
                    problems.add(name + " still has a known dev default");
                } else if (value.length() < rule.minLength()) {
                    problems.add(name + " is shorter than " + rule.minLength() + " characters");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with the prod profile, insecure secrets: "
                    + String.join("; ", problems) + ". Set strong values (openssl rand -base64 48).");
        }
    }

    private static boolean isProd(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                return true;
            }
        }
        for (String profile : environment.getProperty("spring.profiles.active", "").split(",")) {
            if ("prod".equals(profile.trim())) {
                return true;
            }
        }
        return false;
    }
}
