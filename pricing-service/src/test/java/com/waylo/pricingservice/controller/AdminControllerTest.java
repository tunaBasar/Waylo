package com.waylo.pricingservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waylo.pricingservice.dto.CampaignRequest;
import com.waylo.pricingservice.dto.CampaignResponse;
import com.waylo.pricingservice.dto.PromoCodeRequest;
import com.waylo.pricingservice.dto.PromoCodeRequest.CodeItem;
import com.waylo.pricingservice.dto.PromoCodeResponse;
import com.waylo.pricingservice.dto.TaxRateRequest;
import com.waylo.pricingservice.dto.TaxRateResponse;
import com.waylo.pricingservice.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
class AdminControllerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mvc;

    private final UUID campaignId = UUID.randomUUID();
    private final UUID promoCodeId = UUID.randomUUID();
    private final UUID taxRateId = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void setUpMvc() {
        this.mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    private CampaignResponse buildCampaignResponse() {
        return new CampaignResponse(
                campaignId, "Summer Sale", "FLIGHT", null, "PERCENTAGE",
                BigDecimal.valueOf(10), BigDecimal.valueOf(100),
                LocalDateTime.of(2025, 6, 1, 0, 0), LocalDateTime.of(2025, 8, 31, 23, 59),
                1000, 5, "PLATFORM", "Summer promo",
                LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2025, 1, 1, 0, 0));
    }

    private PromoCodeResponse buildPromoCodeResponse() {
        return new PromoCodeResponse(
                promoCodeId, campaignId, "SUMMER10", 1000, 0,
                LocalDateTime.of(2025, 6, 1, 0, 0), LocalDateTime.of(2025, 8, 31, 23, 59));
    }

    private TaxRateResponse buildTaxRateResponse() {
        return new TaxRateResponse(
                taxRateId, "FLIGHT", null, "VAT",
                new BigDecimal("0.08"), true,
                LocalDateTime.of(2025, 1, 1, 0, 0));
    }

    // -- Campaigns --

    @Test
    void createCampaign_returns200() throws Exception {
        when(adminService.createCampaign(any())).thenReturn(buildCampaignResponse());

        CampaignRequest req = new CampaignRequest(
                "Summer Sale", "FLIGHT", null, "PERCENTAGE",
                BigDecimal.valueOf(10), BigDecimal.valueOf(100),
                LocalDateTime.of(2025, 6, 1, 0, 0), LocalDateTime.of(2025, 8, 31, 23, 59),
                1000, 5, "PLATFORM", "Summer promo");

        mvc.perform(post("/api/admin/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId.toString()))
                .andExpect(jsonPath("$.name").value("Summer Sale"));
    }

    @Test
    void getCampaign_returns200() throws Exception {
        when(adminService.getCampaign(campaignId)).thenReturn(buildCampaignResponse());

        mvc.perform(get("/api/admin/campaigns/{id}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId.toString()))
                .andExpect(jsonPath("$.name").value("Summer Sale"));
    }

    @Test
    void listCampaigns_returns200() throws Exception {
        when(adminService.listCampaigns()).thenReturn(List.of(buildCampaignResponse()));

        mvc.perform(get("/api/admin/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(campaignId.toString()))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void updateCampaign_returns200() throws Exception {
        when(adminService.updateCampaign(eq(campaignId), any())).thenReturn(buildCampaignResponse());

        mvc.perform(patch("/api/admin/campaigns/{id}", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteCampaign_returns204() throws Exception {
        mvc.perform(delete("/api/admin/campaigns/{id}", campaignId))
                .andExpect(status().isNoContent());
    }

    // -- Promo Codes --

    @Test
    void createPromoCodes_returns200() throws Exception {
        when(adminService.createPromoCodes(any())).thenReturn(List.of(buildPromoCodeResponse()));

        PromoCodeRequest req = new PromoCodeRequest(
                campaignId,
                List.of(new CodeItem("SUMMER10", 1000,
                        LocalDateTime.of(2025, 6, 1, 0, 0),
                        LocalDateTime.of(2025, 8, 31, 23, 59))));

        mvc.perform(post("/api/admin/promo-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SUMMER10"));
    }

    @Test
    void listPromoCodes_returns200() throws Exception {
        when(adminService.listPromoCodes(campaignId)).thenReturn(List.of(buildPromoCodeResponse()));

        mvc.perform(get("/api/admin/campaigns/{campaignId}/promo-codes", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SUMMER10"));
    }

    @Test
    void deletePromoCode_returns204() throws Exception {
        mvc.perform(delete("/api/admin/promo-codes/{id}", promoCodeId))
                .andExpect(status().isNoContent());
    }

    // -- Tax Rates --

    @Test
    void createTaxRate_returns200() throws Exception {
        when(adminService.createTaxRate(any())).thenReturn(buildTaxRateResponse());

        TaxRateRequest req = new TaxRateRequest(
                "FLIGHT", null, "VAT",
                new BigDecimal("0.08"), true,
                LocalDateTime.of(2025, 1, 1, 0, 0));

        mvc.perform(post("/api/admin/tax-rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vertical").value("FLIGHT"))
                .andExpect(jsonPath("$.rate").value("0.08"));
    }

    @Test
    void listTaxRates_returns200() throws Exception {
        when(adminService.listTaxRates()).thenReturn(List.of(buildTaxRateResponse()));

        mvc.perform(get("/api/admin/tax-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vertical").value("FLIGHT"));
    }

    @Test
    void getTaxRate_returns200() throws Exception {
        when(adminService.getTaxRate(taxRateId)).thenReturn(buildTaxRateResponse());

        mvc.perform(get("/api/admin/tax-rates/{id}", taxRateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taxRateId.toString()));
    }

    @Test
    void deleteTaxRate_returns204() throws Exception {
        mvc.perform(delete("/api/admin/tax-rates/{id}", taxRateId))
                .andExpect(status().isNoContent());
    }
}
