package com.waylo.serviceauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-service identity (see STATUS.md / README "Service-to-service auth").
 * <ul>
 *   <li>Sender: every Feign call gets X-Service-Token signed with WAYLO_SERVICE_PRIVATE_KEY, iss = spring.application.name,
 *       aud = the Feign client name.</li>
 *   <li>Receiver: /internal/** (fail closed) and the route groups in waylo.service-auth.routes are verified against
 *       WAYLO_SERVICE_KEYS_&lt;CALLER&gt; and the per-group caller allowlist.</li>
 *   <li>Signed identity only: the legacy shared X-Internal-Token (INTERNAL_API_TOKEN) is retired.</li>
 * </ul>
 */
@AutoConfiguration
public class ServiceAuthAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ServiceAuthAutoConfiguration.class);

    /** Binding target for one waylo.service-auth.routes[n] entry. */
    public static class RouteProperties {
        private String path;
        private List<String> callers = new ArrayList<>();
        private boolean required = true;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public List<String> getCallers() { return callers; }
        public void setCallers(List<String> callers) { this.callers = callers; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    static String self(Environment env) {
        return env.getProperty("spring.application.name", "");
    }

    static Optional<PrivateKey> privateKey(Environment env) {
        String raw = env.getProperty("WAYLO_SERVICE_PRIVATE_KEY", "");
        return raw.isBlank() ? Optional.empty() : Optional.of(ServiceTokens.privateKey(raw));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.boot.web.servlet.FilterRegistrationBean"})
    static class Servlet {
        @Bean
        ServiceAuthenticator serviceAuthenticator(Environment env) {
            List<ServiceAuthenticator.Route> routes = Binder.get(env)
                    .bind("waylo.service-auth.routes", Bindable.listOf(RouteProperties.class))
                    .orElse(List.of()).stream()
                    .map(r -> new ServiceAuthenticator.Route(r.getPath(), Set.copyOf(r.getCallers()), r.isRequired()))
                    .toList();
            Map<String, Optional<PublicKey>> cache = new ConcurrentHashMap<>();
            log.info("Service auth: self={} routes={}", self(env), routes.size());
            return new ServiceAuthenticator(self(env), routes,
                    iss -> cache.computeIfAbsent(iss, i -> {
                        String raw = env.getProperty(ServiceTokens.publicKeyEnvName(i), "");
                        return raw.isBlank() ? Optional.empty() : Optional.of(ServiceTokens.publicKey(raw));
                    }).orElse(null),
                    Clock.systemUTC());
        }

        /** Right after the request id filter, before Spring Security and every service filter. */
        @Bean
        FilterRegistrationBean<ServiceAuthFilter> serviceAuthFilter(ServiceAuthenticator authenticator) {
            FilterRegistrationBean<ServiceAuthFilter> reg = new FilterRegistrationBean<>(new ServiceAuthFilter(authenticator));
            reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
            return reg;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class Feign {
        /** Global bean: every Feign client context inherits it. Without a private key no token is sent and strict receivers reject the call. */
        @Bean
        feign.RequestInterceptor serviceTokenFeignInterceptor(Environment env) {
            Optional<PrivateKey> key = privateKey(env);
            String self = self(env);
            if (key.isEmpty()) {
                log.warn("WAYLO_SERVICE_PRIVATE_KEY not set: outgoing service calls carry no X-Service-Token");
            }
            return template -> {
                if (key.isEmpty() || template.feignTarget() == null) return;
                template.removeHeader(ServiceTokens.HEADER);
                template.header(ServiceTokens.HEADER, ServiceTokens.sign(self, template.feignTarget().name(), key.get(),
                        java.time.Instant.now(), 30));
            };
        }
    }
}
