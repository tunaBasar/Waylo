package com.waylo.flightseatinventoryservice.config;

import com.waylo.flightseatinventoryservice.supplier.OfferCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupplierConfig {

    @Bean
    public OfferCodec offerCodec(@Value("${waylo.flight.offer-secret}") String secret) {
        return new OfferCodec(secret);
    }
}
