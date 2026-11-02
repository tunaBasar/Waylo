package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.PaymentEscrowClient;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;

/**
 * No-show job (CANCELLATION-CONTRACT.md section 4). After the voucher window ended without a scan the escrow is released to the
 * partner whatever the policy (the service time passed). Idempotent: payment-service answers repeats with the recorded state,
 * the voucher moves ACTIVE -> NO_SHOW once, a failure leaves the voucher ACTIVE so the next run retries.
 */
@Service
public class NoShowService {

    private static final Logger log = LoggerFactory.getLogger(NoShowService.class);

    private final VoucherRepository voucherRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentEscrowClient paymentEscrowClient;
    private final TransactionTemplate tx;

    public NoShowService(VoucherRepository voucherRepository, OrderItemRepository orderItemRepository,
                         PaymentEscrowClient paymentEscrowClient, PlatformTransactionManager txManager) {
        this.voucherRepository = voucherRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentEscrowClient = paymentEscrowClient;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${waylo.cancellation.no-show-interval-ms:60000}", initialDelay = 30000)
    public void scheduled() {
        try {
            int n = settleExpired(Instant.now());
            if (n > 0) log.info("No-show job settled {} voucher(s)", n);
        } catch (RuntimeException e) {
            log.warn("No-show job failed, will retry: {}", e.getMessage());
        }
    }

    /** Returns the number of vouchers settled. */
    public int settleExpired(Instant now) {
        List<Voucher> due = voucherRepository.findTop200ByStatusAndValidUntilBeforeOrderByValidUntilAsc(VoucherStatus.ACTIVE, now);
        int done = 0;
        for (Voucher v : due) {
            try {
                Boolean ok = tx.execute(status -> settle(v.getId(), now));
                if (Boolean.TRUE.equals(ok)) done++;
            } catch (RuntimeException e) {
                log.warn("No-show settle of voucher {} failed, will retry: {}", v.getId(), e.getMessage());
            }
        }
        return done;
    }

    private boolean settle(java.util.UUID voucherId, Instant now) {
        Voucher v = voucherRepository.findByIdForUpdate(voucherId).orElse(null);
        if (v == null || v.getStatus() != VoucherStatus.ACTIVE || !v.getValidUntil().isBefore(now)) return false;
        OrderItem item = orderItemRepository.findById(v.getOrderItemId()).orElse(null);
        if (item == null) return false;
        PaymentEscrowClient.CancellationDto r = paymentEscrowClient.noShow(item.getId()).data();
        v.setStatus(VoucherStatus.NO_SHOW);
        voucherRepository.save(v);
        item.setCancelOutcome("NO_SHOW");
        item.setRefundAmount(java.math.BigDecimal.ZERO.setScale(2));
        orderItemRepository.save(item);
        log.info("No-show: item {} escrow {}", item.getId(), r == null ? "?" : r.status());
        return true;
    }
}
