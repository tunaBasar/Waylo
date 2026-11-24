package com.waylo.packagebookingservice.client;

import com.waylo.packagebookingservice.exception.BusinessRuleException;
import com.waylo.packagebookingservice.exception.PricingUnavailableException;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class PricingFeignConfig {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Short timeouts: sales must not hang on the commercial layer. */
    @Bean
    public Request.Options pricingRequestOptions() {
        return new Request.Options(1, TimeUnit.SECONDS, 3, TimeUnit.SECONDS, true);
    }

    @Bean
    public ErrorDecoder pricingErrorDecoder() {
        return (methodKey, response) -> decode(response.status(), readBody(response));
    }

    /** 409/422 are business answers (code passed through). Everything else means pricing is unavailable. */
    static RuntimeException decode(int status, String body) {
        if (status == 422 || status == 409) {
            String code = "PRICING_REJECTED";
            String message = "Pricing service rejected the request";
            try {
                JsonNode node = MAPPER.readTree(body);
                if (node.hasNonNull("error")) code = node.get("error").asString();
                if (node.hasNonNull("message")) message = node.get("message").asString();
            } catch (RuntimeException ignored) {
                // keep defaults
            }
            return new BusinessRuleException(code, message);
        }
        return new PricingUnavailableException("Pricing service returned HTTP " + status);
    }

    private static String readBody(feign.Response response) {
        try {
            if (response.body() == null) return "";
            return new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }
}
