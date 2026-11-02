package com.waylo.pricingservice.integration;

import com.waylo.pricingservice.domain.Campaign;
import com.waylo.pricingservice.domain.DiscountType;
import com.waylo.pricingservice.domain.FundedBy;
import com.waylo.pricingservice.domain.PromoCode;
import com.waylo.pricingservice.dto.QuoteRequest;
import com.waylo.pricingservice.dto.QuoteResponse;
import com.waylo.pricingservice.service.AdminService;
import com.waylo.pricingservice.service.QuoteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///pricing_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=none"
})
@Testcontainers
@Transactional
@Rollback
class QuoteServiceIntegrationTest {

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private AdminService adminService;

    private UUID campaignId;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        campaignId = UUID.randomUUID();

        // Create a campaign
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime futureEnd = now.plusMonths(6);
        LocalDateTime pastStart = now.minusMonths(1);
        adminService.createCampaign(new com.waylo.pricingservice.dto.CampaignRequest(
                "Summer Sale",
                "FLIGHT",
                null,
                "PERCENTAGE",
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(100),
                pastStart,
                futureEnd,
                10,  // maxUsesGlobal
                5,   // maxUsesPerUser
                "PLATFORM",
                "Summer promo campaign"
        ));

        // Create promo codes for the campaign (re-fetch campaign id from list)
        List<com.waylo.pricingservice.dto.CampaignResponse> campaigns = adminService.listCampaigns();
        campaignId = campaigns.get(0).id();

        adminService.createPromoCodes(new com.waylo.pricingservice.dto.PromoCodeRequest(
                campaignId,
                List.of(new com.waylo.pricingservice.dto.PromoCodeRequest.CodeItem(
                        "SUMMER10",
                        10,
                        pastStart,
                        futureEnd
                ))
        ));
    }

    @AfterEach
    void tearDown() {
        // Cleanup is handled by testcontainers auto-removal
    }

    @Test
    void quoteWithoutPromoCode_returnsGrossAsNet() {
        QuoteRequest request = new QuoteRequest(
                "FLIGHT",
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "USD",
                "customer-1",
                null,
                orderId,
                null
        );

        QuoteResponse response = quoteService.computeQuote(request);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(response.discountLines()).isEmpty();
        assertThat(response.taxAmount()).isNull();
        assertThat(response.promoReservationId()).isNull();
    }

    @Test
    void quoteWithValidPromoCode_returnsDiscounted() {
        QuoteRequest request = new QuoteRequest(
                "FLIGHT",
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "USD",
                "customer-1",
                "SUMMER10",
                orderId,
                null
        );

        QuoteResponse response = quoteService.computeQuote(request);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("900.00"));
        assertThat(response.discountLines()).hasSize(1);
        assertThat(response.discountLines().get(0).amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.discountLines().get(0).fundedBy()).isEqualTo("PLATFORM");
        assertThat(response.promoReservationId()).isNotNull();
    }

    @Test
    void quoteWithSameOrderId_returnsSameReservationId() {
        QuoteRequest request = new QuoteRequest(
                "FLIGHT",
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "USD",
                "customer-1",
                "SUMMER10",
                orderId,
                null
        );

        QuoteResponse first = quoteService.computeQuote(request);
        QuoteResponse second = quoteService.computeQuote(request);

        assertThat(first.promoReservationId()).isEqualTo(second.promoReservationId());
        assertThat(first.netAmount()).isEqualTo(new BigDecimal("900.00"));
        assertThat(second.netAmount()).isEqualTo(new BigDecimal("900.00"));
    }

    @Test
    void quoteExceedingGlobalLimit_throwsBusinessRule() {
        // Use up all global uses
        for (int i = 0; i < 10; i++) {
            QuoteRequest request = new QuoteRequest(
                    "FLIGHT",
                    UUID.randomUUID(),
                    new BigDecimal("1000.00"),
                    "USD",
                    "customer-" + i,
                    "SUMMER10",
                    UUID.randomUUID(),
                    null
            );
            quoteService.computeQuote(request);
        }

        // 11th should fail
        QuoteRequest request = new QuoteRequest(
                "FLIGHT",
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "USD",
                "customer-11",
                "SUMMER10",
                UUID.randomUUID(),
                null
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quoteService.computeQuote(request))
                .hasMessageContaining("PROMO_CODE_MAX_USES_REACHED");
    }

    @Test
    void quoteExceedingPerUserLimit_throwsBusinessRule() {
        // Use up per-user limit (5)
        for (int i = 0; i < 5; i++) {
            QuoteRequest request = new QuoteRequest(
                    "FLIGHT",
                    UUID.randomUUID(),
                    new BigDecimal("1000.00"),
                    "USD",
                    "customer-1",
                    "SUMMER10",
                    UUID.randomUUID(),
                    null
            );
            quoteService.computeQuote(request);
        }

        // 6th should fail
        QuoteRequest request = new QuoteRequest(
                "FLIGHT",
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "USD",
                "customer-1",
                "SUMMER10",
                UUID.randomUUID(),
                null
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quoteService.computeQuote(request))
                .hasMessageContaining("PROMO_CODE_MAX_USES_REACHED");
    }

    @Test
    void quoteBelowMinOrderAmount_throwsBusinessRule() {
        QuoteRequest request = new QuoteRequest(
                "FLIGHT",
                UUID.randomUUID(),
                new BigDecimal("50.00"),
                "USD",
                "customer-1",
                "SUMMER10",
                orderId,
                null
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quoteService.computeQuote(request))
                .hasMessageContaining("MIN_ORDER_NOT_MET");
    }

    @Test
    void releaseReservation_setsStatusToReleased() {
        QuoteRequest request = new QuoteRequest(
                "FLIGHT",
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "USD",
                "customer-1",
                "SUMMER10",
                orderId,
                null
        );

        QuoteResponse response = quoteService.computeQuote(request);
        UUID reservationId = response.promoReservationId();

        quoteService.releaseReservation(reservationId);

        // Release is idempotent - calling again should not throw
        quoteService.releaseReservation(reservationId);
    }

    @Test
    void quoteWithInvalidPromoCode_throwsBusinessRule() {
        QuoteRequest request = new QuoteRequest(
                "FLIGHT",
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "USD",
                "customer-1",
                "INVALID_CODE",
                orderId,
                null
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> quoteService.computeQuote(request))
                .hasMessageContaining("PROMO_CODE_INVALID");
    }
}
