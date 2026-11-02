package com.waylo.pricingservice.integration;

import com.waylo.pricingservice.dto.CampaignRequest;
import com.waylo.pricingservice.dto.PromoCodeRequest;
import com.waylo.pricingservice.dto.QuoteRequest;
import com.waylo.pricingservice.dto.QuoteResponse;
import com.waylo.pricingservice.service.AdminService;
import com.waylo.pricingservice.service.QuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test: 20 threads hit /internal/pricing/quote with the same promo code,
 * global limit 5 → exactly 5 reservations succeed, 15 get PROMO_CODE_MAX_USES_REACHED.
 *
 * The per-code maxUsesTotal is set to 5 so the serialization happens inside the
 * pessimistic lock in reservePromoUsage(), ensuring exactly 5 threads succeed.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///pricing_concurrency_test",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=none"
})
@Testcontainers
class ConcurrencyIntegrationTest {

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void twentyThreadsGlobalLimitFive() throws InterruptedException {
        // Reset the database
        jdbcTemplate.execute("DELETE FROM promo_reservations");
        jdbcTemplate.execute("DELETE FROM promo_codes");
        jdbcTemplate.execute("DELETE FROM campaigns");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pastStart = now.minusMonths(1);
        LocalDateTime futureEnd = now.plusMonths(6);

        // Create campaign with global limit = 5
        adminService.createCampaign(new CampaignRequest(
                "ConcurrencyTest",
                "FLIGHT",
                null,
                "FIXED",
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                pastStart,
                futureEnd,
                5,  // global limit = 5
                null,
                "PLATFORM",
                "Concurrency test campaign"
        ));

        // Create promo code with maxUsesTotal=5 so serialization happens inside the lock
        List<com.waylo.pricingservice.dto.CampaignResponse> campaigns = adminService.listCampaigns();
        UUID campaignId = campaigns.get(0).id();

        adminService.createPromoCodes(new PromoCodeRequest(
                campaignId,
                List.of(new PromoCodeRequest.CodeItem(
                        "CONC10",
                        5,  // per-code limit = 5, matching global limit, so serialization happens inside the lock
                        pastStart,
                        futureEnd
                ))
        ));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<UUID> successReservationIds = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    QuoteRequest request = new QuoteRequest(
                            "FLIGHT",
                            UUID.randomUUID(),
                            new BigDecimal("1000.00"),
                            "USD",
                            "customer-" + threadIndex,
                            "CONC10",
                            UUID.randomUUID(),
                            null
                    );
                    QuoteResponse response = quoteService.computeQuote(request);
                    successCount.incrementAndGet();
                    successReservationIds.add(response.promoReservationId());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    if (e.getMessage() != null && e.getMessage().contains("PROMO_CODE_MAX_USES_REACHED")) {
                        // Expected failure
                    } else {
                        exceptions.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release all threads at once
        startLatch.countDown();

        // Wait for all threads to complete
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(15);
        assertThat(exceptions).isEmpty();

        // Each success should have a unique reservation ID
        assertThat(successReservationIds).hasSize(5);
        assertThat(new HashSet<>(successReservationIds)).hasSize(5);
    }

    @Test
    void twentyThreadsGlobalLimitFiveWithDifferentOrders() throws InterruptedException {
        // Reset the database
        jdbcTemplate.execute("DELETE FROM promo_reservations");
        jdbcTemplate.execute("DELETE FROM promo_codes");
        jdbcTemplate.execute("DELETE FROM campaigns");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pastStart = now.minusMonths(1);
        LocalDateTime futureEnd = now.plusMonths(6);

        // Create campaign with global limit = 5
        adminService.createCampaign(new CampaignRequest(
                "ConcurrencyTest2",
                "FLIGHT",
                null,
                "FIXED",
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                pastStart,
                futureEnd,
                5,
                null,
                "PLATFORM",
                "Concurrency test campaign 2"
        ));

        // Create promo code with maxUsesTotal=5
        List<com.waylo.pricingservice.dto.CampaignResponse> campaigns = adminService.listCampaigns();
        UUID campaignId = campaigns.get(0).id();

        adminService.createPromoCodes(new PromoCodeRequest(
                campaignId,
                List.of(new PromoCodeRequest.CodeItem(
                        "CONC10",
                        5,
                        pastStart,
                        futureEnd
                ))
        ));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    QuoteRequest request = new QuoteRequest(
                            "FLIGHT",
                            UUID.randomUUID(),
                            new BigDecimal("1000.00"),
                            "USD",
                            "customer-" + threadIndex,
                            "CONC10",
                            UUID.randomUUID(),
                            null
                    );
                    quoteService.computeQuote(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(15);
    }
}
