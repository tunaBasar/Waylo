package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.PaymentIntent;
import com.waylo.paymentservice.domain.PaymentIntentStatus;
import com.waylo.paymentservice.domain.SupplierPayoutAccount;
import com.waylo.paymentservice.dto.CancellationDtos.CancellationResult;
import com.waylo.paymentservice.dto.CancellationDtos.PolicyRequest;
import com.waylo.paymentservice.dto.EscrowItemResponse;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.provider.PaymentProvider;
import com.waylo.paymentservice.repository.EscrowItemRepository;
import com.waylo.paymentservice.repository.PaymentIntentRepository;
import com.waylo.paymentservice.repository.SupplierPayoutAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Escrow item state machine: HELD -> RELEASED | REFUNDED (both terminal). Called only through /internal endpoints
 * (QR redemption, ticketing, cancellation flow). Repeating the same transition is idempotent, the opposite one is 422.
 */
@Service
public class EscrowService {

    private final EscrowItemRepository escrowRepository;
    private final PaymentIntentRepository intentRepository;
    private final SupplierPayoutAccountRepository payoutAccountRepository;
    private final LedgerService ledgerService;
    private final PaymentProvider provider;

    public EscrowService(EscrowItemRepository escrowRepository,
                         PaymentIntentRepository intentRepository,
                         SupplierPayoutAccountRepository payoutAccountRepository,
                         LedgerService ledgerService,
                         PaymentProvider provider) {
        this.escrowRepository = escrowRepository;
        this.intentRepository = intentRepository;
        this.payoutAccountRepository = payoutAccountRepository;
        this.ledgerService = ledgerService;
        this.provider = provider;
    }

    @Transactional
    public EscrowItemResponse release(UUID orderItemId) {
        EscrowItem item = lock(orderItemId);
        return EscrowItemResponse.from(doRelease(item, item.getReleaseTrigger().name(), "release to supplier"));
    }

    private EscrowItem doRelease(EscrowItem item, String reason, String description) {
        if (item.getStatus() == EscrowStatus.RELEASED) return item;
        if (item.getStatus() == EscrowStatus.REFUNDED) {
            throw new BusinessRuleException("ESCROW_ALREADY_REFUNDED", "Escrow item was already refunded, it cannot be released");
        }
        PaymentIntent intent = lockIntent(item);
        String subMerchantKey = payoutAccountRepository.findByOrganizationId(item.getSupplierOrganizationId())
                .map(SupplierPayoutAccount::getSubMerchantKey)
                .orElse("TEST-SUBMERCHANT-" + item.getSupplierOrganizationId());

        provider.releaseItem(new PaymentProvider.ReleaseRequest(intent.getProviderPaymentId(), item.getProviderItemRef(),
                subMerchantKey, item.getSupplierAmount(), item.getCommissionAmount(), item.getCurrency()));
        BigDecimal platformCost = "PLATFORM".equals(item.getDiscountFundedBy()) ? item.getDiscountAmount() : BigDecimal.ZERO;
        ledgerService.recordRelease(item.getOrderId(), item.getOrderItemId(), item.getChargedAmount(), item.getSupplierAmount(),
                item.getCommissionAmount(), platformCost, item.getCurrency(), intent.getId(), description);
        item.setStatus(EscrowStatus.RELEASED);
        item.setSettlementReason(reason);
        item.setReleasedAt(Instant.now());
        escrowRepository.saveAndFlush(item);
        refreshIntentStatus(intent);
        return item;
    }

    @Transactional
    public EscrowItemResponse refund(UUID orderItemId) {
        return EscrowItemResponse.from(doRefund(lock(orderItemId), "refund to customer"));
    }

    private EscrowItem doRefund(EscrowItem item, String description) {
        if (item.getStatus() == EscrowStatus.REFUNDED) return item;
        if (item.getStatus() == EscrowStatus.RELEASED) {
            throw new BusinessRuleException("ESCROW_ALREADY_RELEASED", "Escrow item was already released to the supplier, it cannot be refunded here");
        }
        PaymentIntent intent = lockIntent(item);
        provider.refundItem(new PaymentProvider.RefundRequest(intent.getProviderPaymentId(), item.getProviderItemRef(),
                item.getChargedAmount(), item.getCurrency()));
        ledgerService.recordRefund(item.getOrderId(), item.getOrderItemId(), item.getChargedAmount(), item.getCurrency(), intent.getId(), description);
        item.setStatus(EscrowStatus.REFUNDED);
        item.setSettlementReason("CANCEL_REFUND");
        item.setRefundedAt(Instant.now());
        escrowRepository.saveAndFlush(item);
        refreshIntentStatus(intent);
        return item;
    }

    /** What a cancel would do right now. Read only, same evaluator as {@link #cancel}. */
    @Transactional(readOnly = true)
    public CancellationResult previewCancel(UUID orderItemId, PolicyRequest policy) {
        EscrowItem item = escrowRepository.findByOrderItemId(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("EscrowItem", "orderItemId", orderItemId));
        if (item.getStatus() == EscrowStatus.HELD) {
            return result(item, CancellationEvaluator.decide(policy.policy(), policy.freeHoursBefore(), policy.serviceStartAt(), Instant.now()), true, null);
        }
        return settledResult(item);
    }

    /**
     * Customer cancel before QR scan. FULL_REFUND: HELD -> REFUNDED (charged net amount back to the customer).
     * NO_REFUND: HELD -> RELEASED (supplier share to the partner, commission kept). Repeating is idempotent and returns the
     * recorded outcome. An item released by redemption or ticketing is 422 ESCROW_ALREADY_RELEASED.
     */
    @Transactional
    public CancellationResult cancel(UUID orderItemId, PolicyRequest policy) {
        EscrowItem item = lock(orderItemId);
        if (item.getStatus() != EscrowStatus.HELD) {
            CancellationResult r = settledResult(item);
            if (!r.cancellable()) {
                throw new BusinessRuleException("ESCROW_ALREADY_RELEASED", "Escrow item was already released to the supplier, it cannot be cancelled");
            }
            return r;
        }
        CancellationEvaluator.Decision d = CancellationEvaluator.decide(policy.policy(), policy.freeHoursBefore(), policy.serviceStartAt(), Instant.now());
        if (d == CancellationEvaluator.Decision.FULL_REFUND) {
            doRefund(item, "refund to customer (cancellation)");
        } else {
            doRelease(item, "CANCEL_NO_REFUND", "release to supplier (non-refundable cancellation)");
        }
        return result(item, d, true, null);
    }

    /** Voucher window ended without a scan: HELD -> RELEASED to the partner. Already settled items are returned unchanged. */
    @Transactional
    public CancellationResult noShow(UUID orderItemId) {
        EscrowItem item = lock(orderItemId);
        if (item.getStatus() == EscrowStatus.HELD) {
            doRelease(item, "NO_SHOW", "release to supplier (no-show)");
        }
        return settledResult(item);
    }

    private CancellationResult settledResult(EscrowItem item) {
        if (item.getStatus() == EscrowStatus.REFUNDED) {
            return result(item, CancellationEvaluator.Decision.FULL_REFUND, true, null);
        }
        boolean viaCancel = "CANCEL_NO_REFUND".equals(item.getSettlementReason());
        return result(item, CancellationEvaluator.Decision.NO_REFUND, viaCancel, viaCancel ? null : "ALREADY_RELEASED");
    }

    private static CancellationResult result(EscrowItem item, CancellationEvaluator.Decision d, boolean cancellable, String blocked) {
        boolean refund = d == CancellationEvaluator.Decision.FULL_REFUND;
        return new CancellationResult(item.getOrderItemId(), refund ? "REFUND" : "NO_REFUND", cancellable, blocked,
                item.getChargedAmount(), refund ? item.getChargedAmount() : BigDecimal.ZERO.setScale(2),
                refund ? BigDecimal.ZERO.setScale(2) : item.getSupplierAmount(),
                refund ? BigDecimal.ZERO.setScale(2) : item.getCommissionAmount(), item.getCurrency(), item.getStatus());
    }

    /** Derives the intent status from its items. Mixed terminal outcomes count as PARTIALLY_REFUNDED. */
    static PaymentIntentStatus deriveStatus(List<EscrowStatus> items) {
        long held = items.stream().filter(s -> s == EscrowStatus.HELD).count();
        long released = items.stream().filter(s -> s == EscrowStatus.RELEASED).count();
        long refunded = items.stream().filter(s -> s == EscrowStatus.REFUNDED).count();
        if (held == 0) {
            if (refunded == 0) return PaymentIntentStatus.RELEASED;
            if (released == 0) return PaymentIntentStatus.REFUNDED;
            return PaymentIntentStatus.PARTIALLY_REFUNDED;
        }
        if (released > 0) return PaymentIntentStatus.PARTIALLY_RELEASED;
        if (refunded > 0) return PaymentIntentStatus.PARTIALLY_REFUNDED;
        return PaymentIntentStatus.AUTHORIZED;
    }

    private void refreshIntentStatus(PaymentIntent intent) {
        List<EscrowStatus> statuses = escrowRepository.findByPaymentIntentId(intent.getId()).stream()
                .map(EscrowItem::getStatus).toList();
        intent.setStatus(deriveStatus(statuses));
        intentRepository.saveAndFlush(intent);
    }

    private EscrowItem lock(UUID orderItemId) {
        return escrowRepository.findByOrderItemIdForUpdate(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("EscrowItem", "orderItemId", orderItemId));
    }

    private PaymentIntent lockIntent(EscrowItem item) {
        return intentRepository.findByIdForUpdate(item.getPaymentIntentId())
                .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", "id", item.getPaymentIntentId()));
    }
}
