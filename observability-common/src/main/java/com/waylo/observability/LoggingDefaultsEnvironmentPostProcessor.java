package com.waylo.observability;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Default log pattern for every service: request id from the MDC and e-mail masking (a***@domain) on the message.
 * Added last, so a service can still override logging.pattern.* in its own configuration.
 */
public class LoggingDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** a.user@x.com -> a***@x.com. Keeps the first char of the local part, the domain stays readable. */
    static final String MASKED_MESSAGE = "%replace(%m){'([A-Za-z0-9_+-])[A-Za-z0-9._+-]*@([A-Za-z0-9-]+[.])', '$1***@$2'}";
    static final String PATTERN = "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p [%15.15t] [%X{" + RequestId.MDC_KEY + ":-}] "
            + "%-40.40logger{39} : " + MASKED_MESSAGE + "%n%wEx";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource("wayloLoggingDefaults", Map.of(
                "logging.pattern.console", PATTERN,
                "logging.pattern.file", PATTERN)));
    }
}
