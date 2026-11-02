package com.waylo.pricingservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.pricingservice.dto.PreviewDiscountLine;
import com.waylo.pricingservice.dto.PreviewRequest;
import com.waylo.pricingservice.dto.PreviewResponse;
import com.waylo.pricingservice.service.PreviewService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
class PreviewControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PreviewService previewService;

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void previewWithPromoCode_returnsDiscounted() throws Exception {
        when(previewService.preview(any(PreviewRequest.class), any(String.class)))
                .thenReturn(new PreviewResponse(
                        new BigDecimal("900.00"),
                        List.of(new PreviewDiscountLine("SummerSale (10%)", new BigDecimal("100.00"), "PLATFORM")),
                        new BigDecimal("72.00")));

        PreviewRequest req = new PreviewRequest(
                "FLIGHT", UUID.randomUUID(),
                new BigDecimal("1000.00"), "USD",
                "SUMMER10");

        mvc.perform(post("/api/pricing/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netAmount").value(900.00))
                .andExpect(jsonPath("$.discountLines").isArray())
                .andExpect(jsonPath("$.discountLines[0].amount").value(100.00))
                .andExpect(jsonPath("$.discountLines[0].fundedBy").value("PLATFORM"))
                .andExpect(jsonPath("$.taxAmount").value(72.00));
    }

    @Test
    void previewWithoutPromoCode_returnsGrossAsNet() throws Exception {
        when(previewService.preview(any(PreviewRequest.class), isNull()))
                .thenReturn(new PreviewResponse(
                        new BigDecimal("1000.00"),
                        List.of(),
                        new BigDecimal("80.00")));

        PreviewRequest req = new PreviewRequest(
                "FLIGHT", UUID.randomUUID(),
                new BigDecimal("1000.00"), "USD",
                null);

        mvc.perform(post("/api/pricing/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netAmount").value(1000.00))
                .andExpect(jsonPath("$.discountLines").isEmpty())
                .andExpect(jsonPath("$.taxAmount").value(80.00));
    }
}
