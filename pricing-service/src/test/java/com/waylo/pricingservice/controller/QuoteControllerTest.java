package com.waylo.pricingservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.pricingservice.dto.QuoteRequest;
import com.waylo.pricingservice.dto.QuoteResponse;
import com.waylo.pricingservice.exception.BusinessRuleException;
import com.waylo.pricingservice.service.QuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        properties = {
                "spring.flyway.enabled=false",
                "spring.kafka.enabled=false",
                "spring.redis.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "waylo.internal.token=test-token-123",
                "eureka.client.enabled=false",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false"
        }
)
class QuoteControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QuoteService quoteService;

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    private final UUID reservationId = UUID.randomUUID();

    private QuoteResponse buildQuoteResponse() {
        return new QuoteResponse(
                new BigDecimal("900.00"),
                Collections.singletonList(new QuoteResponse.DiscountLine(
                        "SummerSale (10%)", new BigDecimal("100.00"), "PLATFORM")),
                new BigDecimal("72.00"),
                reservationId
        );
    }

    @Test
    void quote_returns200() throws Exception {
        when(quoteService.computeQuote(any())).thenReturn(buildQuoteResponse());

        QuoteRequest req = new QuoteRequest(
                "FLIGHT", UUID.randomUUID(),
                new BigDecimal("1000.00"), "USD", "customer-1",
                "SUMMER10", UUID.randomUUID(), null);

        mvc.perform(post("/internal/pricing/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netAmount").value(900.00))
                .andExpect(jsonPath("$.discountLines").isArray())
                .andExpect(jsonPath("$.discountLines[0].amount").value(100.00))
                .andExpect(jsonPath("$.promoReservationId").value(reservationId.toString()))
                .andExpect(jsonPath("$.taxAmount").value(72.00));
    }

    @Test
    void quoteWithEmptyPromoCode_returns200_noDiscount() throws Exception {
        when(quoteService.computeQuote(any())).thenReturn(new QuoteResponse(
                new BigDecimal("1000.00"),
                Collections.emptyList(),
                new BigDecimal("80.00"),
                null
        ));

        QuoteRequest req = new QuoteRequest(
                "FLIGHT", UUID.randomUUID(),
                new BigDecimal("1000.00"), "USD", "customer-1",
                null, UUID.randomUUID(), null);

        mvc.perform(post("/internal/pricing/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netAmount").value(1000.00))
                .andExpect(jsonPath("$.discountLines").isEmpty())
                .andExpect(jsonPath("$.promoReservationId").isEmpty());
    }

    @Test
    void quoteWithBusinessRule_error422() throws Exception {
        when(quoteService.computeQuote(any()))
                .thenThrow(new BusinessRuleException("PROMO_CODE_MAX_USES_REACHED", "Promo code has reached max uses"));

        QuoteRequest req = new QuoteRequest(
                "FLIGHT", UUID.randomUUID(),
                new BigDecimal("1000.00"), "USD", "customer-1",
                "SUMMER10", UUID.randomUUID(), null);

        mvc.perform(post("/internal/pricing/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }
}
