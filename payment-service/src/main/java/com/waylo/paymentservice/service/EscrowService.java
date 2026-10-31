package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.PaymentIntent;
import com.waylo.paymentservice.domain.PaymentIntentStatus;
import com.waylo.paymentservice.domain.SupplierPayoutAccount;
import com.waylo.paymentservice.dto.EscrowItemResponse;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.provider.PaymentProvider;
import com.waylo.paymentservice.repository.EscrowItemRepository;
import com.waylo.paymentservice.repository.PaymentIntentRepository;
import com.waylo.paymentservice.repository.SupplierPayoutAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (item.getStatus() == EscrowStatus.RELEASED) return EscrowItemResponse.from(item);
        if (item.getStatus() == EscrowStatus.REFUNDED) {
            throw new BusinessRuleException("ESCROW_ALREADY_REFUNDED", "Escrow item was already refunded, it cannot be released");
        }
        PaymentIntent intent = lockIntent(item);
        String subMerchantKey = payoutAccountRepository.findByOrganizationId(item.getSupplierOrganizationId())
                .map(SupplierPayoutAccount::getSubMerchantKey)
                .orElse("TEST-SUBMERCHANT-" + item.getSupplierOrganizationId());

        provider.releaseItem(new PaymentProvider.ReleaseRequest(intent.getProviderPaymentId(), item.getProviderItemRef(),
                subMerchantKey, item.getSupplierAmount(), item.getCommissionAmount(), item.getCurrency()));
        ledgerService.recordRelease(item.getOrderId(), item.getOrderItemId(), item.getGrossAmount(), item.getSupplierAmount(),
                item.getCommissionAmount(), item.getCurrency(), intent.getId());
        item.setStatus(EscrowStatus.RELEASED);
        item.setReleasedAt(Instant.now());
        escrowRepository.saveAndFlush(item);
        refreshIntentStatus(intent);
        return EscrowItemResponse.from(item);
    }

    @Transactional
    public EscrowItemResponse refund(UUID orderItemId) {
        EscrowItem item = lock(orderItemId);
        if (item.getStatus() == EscrowStatus.REFUNDED) return EscrowItemResponse.from(item);
        if (item.getStatus() == EscrowStatus.RELEASED) {
            throw new BusinessRuleException("ESCROW_ALREADY_RELEASED", "Escrow item was already released to the supplier, it cannot be refunded here");
        }
        PaymentIntent intent = lockIntent(item);
        provider.refundItem(new PaymentProvider.RefundRequest(intent.getProviderPaymentId(), item.getProviderItemRef(),
                item.getGrossAmount(), item.getCurrency()));
        ledgerService.recordRefund(item.getOrderId(), item.getOrderItemId(), item.getGrossAmount(), item.getCurrency(), intent.getId());
        item.setStatus(EscrowStatus.REFUNDED);
        item.setRefundedAt(Instant.now());
        escrowRepository.saveAndFlush(item);
        refreshIntentStatus(intent);
        return EscrowItemResponse.from(item);
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
