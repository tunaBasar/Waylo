package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.AuthAccessClient;
import com.waylo.packagebookingservice.client.PaymentEscrowClient;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.dto.RedeemResponse;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.AuthenticationRequiredException;
import com.waylo.packagebookingservice.exception.AuthorizationUnavailableException;
import com.waylo.packagebookingservice.exception.VoucherException;
import com.waylo.packagebookingservice.event.OrderEventPublisher;
import com.waylo.packagebookingservice.event.OrderItemRedeemedEvent;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Partner QR redemption. The permission check runs first (unauthorized callers cannot probe vouchers and do not feed the
 * failure counter). The rest follows the contract order: payload format, voucher + constant-time hash match, supplier,
 * status, validity window. State change and escrow release happen in ONE local transaction under a row lock.
 */
@Service
public class VoucherRedemptionService {

    static final Pattern PAYLOAD = Pattern.compile("^WAYLO1:([0-9a-fA-F-]{36}):([A-Za-z0-9_-]{22})$");
    private static final Logger log = LoggerFactory.getLogger(VoucherRedemptionService.class);

    private final VoucherRepository voucherRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentEscrowClient paymentEscrowClient;
    private final AuthAccessClient authAccessClient;
    private final RedemptionAttemptLimiter limiter;
    private final TransactionTemplate tx;
    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public VoucherRedemptionService(VoucherRepository voucherRepository, OrderItemRepository orderItemRepository,
                                    PaymentEscrowClient paymentEscrowClient, AuthAccessClient authAccessClient,
                                    RedemptionAttemptLimiter limiter, PlatformTransactionManager txManager,
                                    OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.voucherRepository = voucherRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentEscrowClient = paymentEscrowClient;
        this.authAccessClient = authAccessClient;
        this.limiter = limiter;
        this.tx = new TransactionTemplate(txManager);
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public RedeemResponse redeem(UUID userId, UUID orgId, String payload) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        requireRedeemPermission(userId, orgId);
        if (limiter.isBlocked(orgId)) {
            throw new VoucherException("TOO_MANY_ATTEMPTS", HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            RedeemResponse response = tx.execute(status -> doRedeem(userId, orgId, payload));
            publishRedeemed(response);
            return response;
        } catch (VoucherException e) {
            limiter.recordFailure(orgId);
            throw e;
        }
    }

    private RedeemResponse doRedeem(UUID userId, UUID orgId, String payload) {
        Matcher m = payload == null ? null : PAYLOAD.matcher(payload.trim());
        if (m == null || !m.matches()) throw invalid();
        UUID voucherId;
        try {
            voucherId = UUID.fromString(m.group(1));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        Voucher v = voucherRepository.findByIdForUpdate(voucherId).orElseThrow(VoucherRedemptionService::invalid);
        byte[] given = VoucherService.hash(m.group(2)).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, v.getCodeHash().getBytes(StandardCharsets.UTF_8))) throw invalid();

        OrderItem item = orderItemRepository.findById(v.getOrderItemId()).orElseThrow(VoucherRedemptionService::invalid);
        if (!orgId.equals(item.getSupplierOrganizationId())) {
            throw new VoucherException("WRONG_SUPPLIER", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (v.getStatus() == VoucherStatus.REDEEMED) {
            throw new VoucherException("ALREADY_REDEEMED", HttpStatus.CONFLICT, v.getRedeemedAt());
        }
        if (v.getStatus() == VoucherStatus.VOID) throw new VoucherException("VOID", HttpStatus.UNPROCESSABLE_ENTITY);
        Instant now = Instant.now();
        if (now.isBefore(v.getValidFrom())) throw new VoucherException("NOT_YET_VALID", HttpStatus.UNPROCESSABLE_ENTITY);
        if (now.isAfter(v.getValidUntil())) throw new VoucherException("EXPIRED", HttpStatus.UNPROCESSABLE_ENTITY);

        v.setStatus(VoucherStatus.REDEEMED);
        v.setRedeemedAt(now);
        v.setRedeemedByUserId(userId);
        voucherRepository.saveAndFlush(v);

        // Any failure here (503 payment unavailable, 422 escrow state) propagates and rolls the voucher back to ACTIVE.
        PaymentEscrowClient.EscrowDto escrow = paymentEscrowClient.release(item.getId()).data();
        log.info("Voucher {} redeemed by org {}", v.getId(), orgId);
        return new RedeemResponse(v.getId(), item.getId(), item.getDescription(),
                escrow == null ? null : escrow.supplierAmount(), escrow == null ? item.getCurrency() : escrow.currency(), now);
    }

    /** After commit, fail-soft: a Kafka problem must never undo a committed redemption. */
    private void publishRedeemed(RedeemResponse r) {
        try {
            OrderItem item = orderItemRepository.findById(r.orderItemId()).orElse(null);
            if (item == null) return;
            var order = orderRepository.findById(item.getOrderId()).orElse(null);
            if (order == null) return;
            java.math.BigDecimal amount = item.getGrossAmount() != null ? item.getGrossAmount()
                    : item.getUnitAmount().multiply(java.math.BigDecimal.valueOf(item.getQuantity()));
            eventPublisher.publishOrderItemRedeemed(new OrderItemRedeemedEvent(
                    order.getId(), item.getId(), order.getCustomerId(), item.getCurrency(), amount));
        } catch (RuntimeException e) {
            log.warn("ORDER_ITEM_REDEEMED publish failed for item {}: {}", r.orderItemId(), e.getMessage());
        }
    }

    private static VoucherException invalid() {
        return new VoucherException("INVALID_VOUCHER", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private void requireRedeemPermission(UUID userId, UUID orgId) {
        AuthAccessClient.AccessDecision decision;
        try {
            decision = authAccessClient.check(userId, orgId, "RESERVATION_REDEEM");
        } catch (RuntimeException e) {
            log.warn("Authorization service unavailable for userId={}, permission=RESERVATION_REDEEM", userId);
            throw new AuthorizationUnavailableException("Authorization service unavailable");
        }
        if (decision == null || !decision.allowed()) {
            throw new AccessDeniedException("Not allowed to redeem vouchers for this organization");
        }
    }
}
