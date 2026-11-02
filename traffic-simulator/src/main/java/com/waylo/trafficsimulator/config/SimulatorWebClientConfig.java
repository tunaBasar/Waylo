package com.waylo.trafficsimulator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class SimulatorWebClientConfig {

    private final String baseUrl;

    public SimulatorWebClientConfig(@Value("${waylo.simulator.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Bean
    public WebClient simulatorWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
