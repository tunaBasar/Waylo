package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.OrderClient;
import com.waylo.paymentservice.domain.PaymentIntent;
import com.waylo.paymentservice.domain.PaymentIntentStatus;
import com.waylo.paymentservice.domain.PaymentReconciliation;
import com.waylo.paymentservice.domain.ReconciliationStatus;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.provider.PaymentProvider;
import com.waylo.paymentservice.repository.PaymentIntentRepository;
import com.waylo.paymentservice.repository.PaymentReconciliationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RM-27: settles ambiguous payment outcomes. Finds AUTHORIZED intents (provider holds the money) older than N minutes that have
 * no escrow, asks package-booking for the order status and
 * <ul>
 *   <li>CONFIRMED: books the missing escrow and ledger rows through {@link PaymentIntentService#repairEscrow} (same code as confirm)</li>
 *   <li>PENDING, EXPIRED, FAILED: cancels the provider payment and marks the intent CANCELLED</li>
 *   <li>anything else (or order unknown): MANUAL_REVIEW, left for a person</li>
 * </ul>
 * Every decision is appended to payment_reconciliations. Each intent runs in its own transaction under the intent row lock and
 * is re-checked, so a second run changes nothing. "now" comes from the injected Clock.
 */
@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentIntentRepository intents;
    private final PaymentReconciliationRepository reconciliations;
    private final PaymentIntentService intentService;
    private final OrderClient orderClient;
    private final PaymentProvider provider;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Duration minAge;
    private final int batchSize;

    public PaymentReconciliationJob(PaymentIntentRepository intents, PaymentReconciliationRepository reconciliations,
                                    PaymentIntentService intentService, OrderClient orderClient, PaymentProvider provider,
                                    Clock clock, PlatformTransactionManager txManager,
                                    @Value("${waylo.payments.reconciliation.min-age-minutes:5}") long minAgeMinutes,
                                    @Value("${waylo.payments.reconciliation.batch-size:50}") int batchSize) {
        this.intents = intents;
        this.reconciliations = reconciliations;
        this.intentService = intentService;
        this.orderClient = orderClient;
        this.provider = provider;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
        this.minAge = Duration.ofMinutes(minAgeMinutes);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${waylo.payments.reconciliation.interval-ms:60000}", initialDelay = 45000)
    public void scheduled() {
        try {
            int n = reconcile();
            if (n > 0) log.info("Payment reconciliation job handled {} intent(s)", n);
        } catch (RuntimeException e) {
            log.warn("Payment reconciliation job failed, will retry: {}", e.getMessage());
        }
    }

    /** Returns the number of intents that got a recorded decision in this run. */
    public int reconcile() {
        Instant cutoff = clock.instant().minus(minAge);
        List<UUID> ids = intents.findAuthorizedWithoutEscrow(cutoff, PageRequest.of(0, batchSize));
        int done = 0;
        for (UUID id : ids) {
            try {
                if (Boolean.TRUE.equals(tx.execute(s -> reconcileOne(id, cutoff)))) done++;
            } catch (RuntimeException e) {
                log.warn("Reconciling intent {} failed, will retry: {}", id, e.getMessage());
            }
        }
        return done;
    }

    private boolean reconcileOne(UUID intentId, Instant cutoff) {
        PaymentIntent intent = intents.findByIdForUpdate(intentId).orElse(null);
        if (intent == null || intent.getStatus() != PaymentIntentStatus.AUTHORIZED || intent.getProviderPaymentId() == null
                || !intent.getUpdatedAt().isBefore(cutoff)) {
            return false;
        }
        OrderClient.OrderDto order;
        try {
            order = orderClient.getOrder(intent.getOrderId()).data();
        } catch (ResourceNotFoundException e) {
            order = null;
        }
        String orderStatus = order == null ? null : order.status();
        PaymentIntentStatus before = intent.getStatus();
        Instant now = clock.instant();
        if ("CONFIRMED".equals(orderStatus)) {
            intentService.repairEscrow(intent, order);
            reconciliations.save(new PaymentReconciliation(intent, before, orderStatus, ReconciliationStatus.ESCROW_REPAIRED,
                    "order confirmed but escrow and ledger were missing, booked as confirm would have", now));
        } else if ("PENDING".equals(orderStatus) || "EXPIRED".equals(orderStatus) || "FAILED".equals(orderStatus)) {
            provider.cancel(intent.getProviderPaymentId());
            intent.setStatus(PaymentIntentStatus.CANCELLED);
            intents.saveAndFlush(intent);
            reconciliations.save(new PaymentReconciliation(intent, before, orderStatus, ReconciliationStatus.PROVIDER_CANCELLED,
                    "order is " + orderStatus + ", provider payment cancelled", now));
        } else {
            log.warn("Intent {} (order {}) is AUTHORIZED without escrow and the order is {}: manual review",
                    intent.getId(), intent.getOrderId(), orderStatus == null ? "unknown" : orderStatus);
            reconciliations.save(new PaymentReconciliation(intent, before, orderStatus, ReconciliationStatus.MANUAL_REVIEW,
                    "order is " + (orderStatus == null ? "unknown" : orderStatus) + ", needs a person", now));
        }
        return true;
    }
}
