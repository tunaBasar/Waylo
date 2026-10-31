package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.OrderClient;
import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.PaymentIntent;
import com.waylo.paymentservice.domain.PaymentIntentStatus;
import com.waylo.paymentservice.domain.PayoutAccountStatus;
import com.waylo.paymentservice.domain.ReleaseTrigger;
import com.waylo.paymentservice.domain.SupplierPayoutAccount;
import com.waylo.paymentservice.domain.Vertical;
import com.waylo.paymentservice.dto.CustomerEscrowItemView;
import com.waylo.paymentservice.dto.IntentResponse;
import com.waylo.paymentservice.dto.OrderPaymentResponse;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.provider.PaymentProvider;
import com.waylo.paymentservice.repository.EscrowItemRepository;
import com.waylo.paymentservice.repository.PaymentIntentRepository;
import com.waylo.paymentservice.repository.SupplierPayoutAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Customer payment flow: intent (amount taken from the order, never from the client) and confirm (capture into escrow).
 */
@Service
public class PaymentIntentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIntentService.class);

    private final PaymentIntentRepository intentRepository;
    private final EscrowItemRepository escrowRepository;
    private final SupplierPayoutAccountRepository payoutAccountRepository;
    private final CommissionService commissionService;
    private final LedgerService ledgerService;
    private final OrderClient orderClient;
    private final PaymentProvider provider;

    public PaymentIntentService(PaymentIntentRepository intentRepository,
                                EscrowItemRepository escrowRepository,
                                SupplierPayoutAccountRepository payoutAccountRepository,
                                CommissionService commissionService,
                                LedgerService ledgerService,
                                OrderClient orderClient,
                                PaymentProvider provider) {
        this.intentRepository = intentRepository;
        this.escrowRepository = escrowRepository;
        this.payoutAccountRepository = payoutAccountRepository;
        this.commissionService = commissionService;
        this.ledgerService = ledgerService;
        this.orderClient = orderClient;
        this.provider = provider;
    }

    /** One planned escrow item: validated order item plus its commission snapshot. */
    record Plan(OrderClient.OrderItemDto item, Vertical vertical, BigDecimal rate, CommissionService.Split split) {}

    @Transactional
    public IntentResponse createIntent(UUID userId, String role, UUID orderId) {
        requireCustomer(userId, role);

        PaymentIntent existing = intentRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            requireOwner(userId, existing);
            return IntentResponse.from(existing);
        }

        OrderClient.OrderDto order = loadOrder(orderId);
        if (!userId.equals(order.customerId())) {
            throw new AccessDeniedException("Order does not belong to the authenticated customer");
        }
        List<Plan> plans = plan(order);

        PaymentIntent intent = new PaymentIntent();
        intent.setId(UUID.randomUUID());
        intent.setOrderId(orderId);
        intent.setCustomerId(userId);
        intent.setAmount(order.totalAmount());
        intent.setCurrency(order.currency());
        intent.setStatus(PaymentIntentStatus.REQUIRES_PAYMENT);
        intent.setProvider(provider.name());
        intent.setIdempotencyKey("intent-" + orderId);
        return IntentResponse.from(intentRepository.saveAndFlush(intent));
    }

    /**
     * Capture into escrow. Declined: FAILED, nothing held, order untouched (the customer may confirm again with another token).
     * Approved: escrow items + ledger + order marked paid. If package-booking rejects the paid call (e.g. hold expired) the
     * provider payment is cancelled and the whole local transaction rolls back, so money and order never disagree.
     */
    @Transactional
    public IntentResponse confirm(UUID userId, String role, UUID intentId, String paymentToken) {
        requireCustomer(userId, role);
        PaymentIntent intent = intentRepository.findByIdForUpdate(intentId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", "id", intentId));
        requireOwner(userId, intent);

        switch (intent.getStatus()) {
            case REQUIRES_PAYMENT, FAILED -> { /* payable */ }
            case CANCELLED -> throw new BusinessRuleException("INTENT_CANCELLED", "Payment intent was cancelled");
            default -> { return IntentResponse.from(intent); } // already captured: idempotent
        }

        OrderClient.OrderDto order = loadOrder(intent.getOrderId());
        if (!"PENDING".equals(order.status())) {
            throw new BusinessRuleException("ORDER_NOT_PAYABLE", "Order is " + order.status() + ", it cannot be paid");
        }
        if (!userId.equals(order.customerId())) {
            throw new AccessDeniedException("Order does not belong to the authenticated customer");
        }
        List<Plan> plans = plan(order);
        intent.setAmount(order.totalAmount());
        intent.setCurrency(order.currency());

        List<PaymentProvider.ItemAllocation> allocations = new ArrayList<>();
        for (Plan p : plans) {
            allocations.add(new PaymentProvider.ItemAllocation(p.item().orderItemId(), subMerchantKey(p.item().supplierOrganizationId()),
                    p.item().amount(), p.split().supplier()));
        }
        PaymentProvider.AuthorizeResult result = provider.authorize(new PaymentProvider.AuthorizeRequest(
                intent.getId(), intent.getOrderId(), userId, intent.getAmount(), intent.getCurrency(), paymentToken,
                intent.getIdempotencyKey(), allocations));

        if (!result.success()) {
            intent.setStatus(PaymentIntentStatus.FAILED);
            intent.setFailureCode(result.failureCode());
            return IntentResponse.from(intentRepository.saveAndFlush(intent));
        }

        Instant now = Instant.now();
        intent.setProviderPaymentId(result.providerPaymentId());
        intent.setStatus(PaymentIntentStatus.AUTHORIZED);
        intent.setFailureCode(null);
        for (Plan p : plans) {
            EscrowItem e = new EscrowItem();
            e.setId(UUID.randomUUID());
            e.setPaymentIntentId(intent.getId());
            e.setOrderId(intent.getOrderId());
            e.setOrderItemId(p.item().orderItemId());
            e.setVertical(p.vertical());
            e.setSupplierOrganizationId(p.item().supplierOrganizationId());
            e.setGrossAmount(p.item().amount());
            e.setCommissionRate(p.rate());
            e.setCommissionAmount(p.split().commission());
            e.setSupplierAmount(p.split().supplier());
            e.setCurrency(p.item().currency());
            e.setStatus(EscrowStatus.HELD);
            e.setReleaseTrigger(triggerFor(p.vertical()));
            e.setProviderItemRef(result.itemRefs().get(p.item().orderItemId()));
            e.setHeldAt(now);
            escrowRepository.save(e);
            ledgerService.recordCapture(intent.getOrderId(), p.item().orderItemId(), p.item().amount(),
                    p.item().currency(), intent.getId());
        }
        intentRepository.saveAndFlush(intent);

        try {
            orderClient.markPaid(intent.getOrderId());
        } catch (RuntimeException e) {
            try {
                provider.cancel(result.providerPaymentId());
            } catch (RuntimeException cancelFailure) {
                log.error("Could not cancel provider payment {} after order paid-call failure, reconcile manually",
                        result.providerPaymentId(), cancelFailure);
            }
            throw e; // rolls the local transaction back: no escrow, no ledger, intent unchanged
        }
        return IntentResponse.from(intent);
    }

    @Transactional(readOnly = true)
    public OrderPaymentResponse getForOrder(UUID userId, UUID orderId) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        PaymentIntent intent = intentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", "orderId", orderId));
        requireOwner(userId, intent);
        List<CustomerEscrowItemView> items = escrowRepository.findByPaymentIntentId(intent.getId()).stream()
                .map(CustomerEscrowItemView::from).toList();
        return new OrderPaymentResponse(IntentResponse.from(intent), items);
    }

    /**
     * Today every vertical releases on REDEMPTION (QR scan): hotel, car, bus, and flights from the internal supplier.
     * TICKETING is for flights from an external supplier; no such supplier exists yet, so nothing yields it.
     */
    static ReleaseTrigger triggerFor(Vertical vertical) {
        return ReleaseTrigger.REDEMPTION;
    }

    private OrderClient.OrderDto loadOrder(UUID orderId) {
        OrderClient.OrderDto order = orderClient.getOrder(orderId).data();
        if (order == null) throw new ResourceNotFoundException("Order", "id", orderId);
        return order;
    }

    /** Validates the order items and resolves one commission snapshot per item. Any missing rate stops the payment (422). */
    private List<Plan> plan(OrderClient.OrderDto order) {
        if (order.items() == null || order.items().isEmpty()) {
            throw new BusinessRuleException("ORDER_EMPTY", "Order has no items");
        }
        Instant now = Instant.now();
        List<Plan> plans = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderClient.OrderItemDto item : order.items()) {
            Vertical vertical = parseVertical(item.vertical());
            if (item.supplierOrganizationId() == null) {
                throw new BusinessRuleException("SUPPLIER_MISSING", "Order item " + item.orderItemId() + " has no supplier organization");
            }
            if (!order.currency().equals(item.currency())) {
                throw new BusinessRuleException("CURRENCY_MISMATCH", "Item currency differs from order currency");
            }
            if (item.amount() == null || item.amount().signum() <= 0) {
                throw new BusinessRuleException("INVALID_ITEM_AMOUNT", "Order item amount must be positive");
            }
            if (provider.requiresPayoutAccount()) {
                requireActivePayoutAccount(item.supplierOrganizationId());
            }
            BigDecimal rate = commissionService.resolveRate(vertical, item.supplierOrganizationId(), now);
            plans.add(new Plan(item, vertical, rate, CommissionService.split(item.amount(), rate)));
            sum = sum.add(item.amount());
        }
        if (sum.compareTo(order.totalAmount()) != 0) {
            throw new BusinessRuleException("ORDER_TOTAL_MISMATCH", "Order total does not equal the sum of its items");
        }
        return plans;
    }

    private static Vertical parseVertical(String name) {
        try {
            return Vertical.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException("UNSUPPORTED_VERTICAL", "Unsupported order item vertical: " + name);
        }
    }

    private void requireActivePayoutAccount(UUID organizationId) {
        SupplierPayoutAccount acc = payoutAccountRepository.findByOrganizationId(organizationId).orElse(null);
        if (acc == null || acc.getStatus() != PayoutAccountStatus.ACTIVE) {
            throw new BusinessRuleException("PAYOUT_ACCOUNT_MISSING",
                    "Supplier " + organizationId + " has no active payout account");
        }
    }

    private String subMerchantKey(UUID organizationId) {
        return payoutAccountRepository.findByOrganizationId(organizationId)
                .map(SupplierPayoutAccount::getSubMerchantKey)
                .orElse("TEST-SUBMERCHANT-" + organizationId);
    }

    private static void requireCustomer(UUID userId, String role) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        if ("ADMIN".equals(role)) {
            throw new AccessDeniedException(AccessDeniedException.ADMIN_CANNOT_PURCHASE,
                    "Platform administrators cannot purchase, use a customer account");
        }
    }

    private static void requireOwner(UUID userId, PaymentIntent intent) {
        if (!intent.getCustomerId().equals(userId)) {
            throw new AccessDeniedException("Payment does not belong to the authenticated customer");
        }
    }
}
