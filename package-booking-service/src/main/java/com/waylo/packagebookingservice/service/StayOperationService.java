package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.AuthAccessClient;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.dto.StayOperationResponse;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.AuthenticationRequiredException;
import com.waylo.packagebookingservice.exception.AuthorizationUnavailableException;
import com.waylo.packagebookingservice.exception.ResourceNotFoundException;
import com.waylo.packagebookingservice.exception.VoucherException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Recorded hotel check-out (RM-29). Only a REDEEMED voucher (guest checked in) can be checked out. A second call is an
 * idempotent no-op. Same partner permission as QR redemption (RESERVATION_REDEEM).
 */
@Service
public class StayOperationService {

    private static final Logger log = LoggerFactory.getLogger(StayOperationService.class);

    private final OrderItemRepository itemRepository;
    private final VoucherRepository voucherRepository;
    private final AuthAccessClient authAccessClient;
    private final Clock clock;

    public StayOperationService(OrderItemRepository itemRepository, VoucherRepository voucherRepository,
                                AuthAccessClient authAccessClient, Clock clock) {
        this.itemRepository = itemRepository;
        this.voucherRepository = voucherRepository;
        this.authAccessClient = authAccessClient;
        this.clock = clock;
    }

    @Transactional
    public StayOperationResponse checkOut(UUID userId, UUID orgId, UUID orderItemId, Long expectedVersion, Instant checkedOutAt) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        requirePermission(userId, orgId);
        OrderItem item = itemRepository.findById(orderItemId)
                .filter(i -> orgId.equals(i.getSupplierOrganizationId()))
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", orderItemId));
        if (item.getVertical() != OrderItemVertical.HOTEL) {
            throw new VoucherException("INVALID_STATE_TRANSITION", HttpStatus.CONFLICT);
        }
        UUID voucherId = voucherRepository.findByOrderItemId(orderItemId)
                .orElseThrow(() -> new VoucherException("INVALID_STATE_TRANSITION", HttpStatus.CONFLICT)).getId();
        Voucher v = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Voucher", "id", voucherId));

        if (v.getStatus() == VoucherStatus.REDEEMED && v.getCheckedOutAt() != null) return respond(v);
        if (v.getStatus() != VoucherStatus.REDEEMED) {
            throw new VoucherException("INVALID_STATE_TRANSITION", HttpStatus.CONFLICT);
        }
        if (expectedVersion == null || expectedVersion != v.getVersion()) {
            throw new VoucherException("VERSION_CONFLICT", HttpStatus.CONFLICT);
        }
        // PostgreSQL timestamptz keeps microseconds: truncate here so the first response and every idempotent replay
        // (which reads the stored value back) return the same instant.
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant at = checkedOutAt == null ? now : checkedOutAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (at.isAfter(now)) throw new IllegalArgumentException("checkedOutAt cannot be in the future");
        if (v.getRedeemedAt() != null && at.isBefore(v.getRedeemedAt())) {
            throw new IllegalArgumentException("checkedOutAt cannot be before the check-in time");
        }
        v.setCheckedOutAt(at);
        v.setCheckedOutByUserId(userId);
        voucherRepository.saveAndFlush(v);
        log.info("Order item {} checked out by user {} for org {}", orderItemId, userId, orgId);
        return respond(v);
    }

    private static StayOperationResponse respond(Voucher v) {
        return new StayOperationResponse(v.getOrderItemId(), v.getStatus().name(), v.getCheckedOutAt(),
                v.getVersion() == null ? 0 : v.getVersion());
    }

    private void requirePermission(UUID userId, UUID orgId) {
        AuthAccessClient.AccessDecision decision;
        try {
            decision = authAccessClient.check(userId, orgId, "RESERVATION_REDEEM");
        } catch (RuntimeException e) {
            log.warn("Authorization service unavailable for userId={}, permission=RESERVATION_REDEEM", userId);
            throw new AuthorizationUnavailableException("Authorization service unavailable");
        }
        if (decision == null || !decision.allowed()) {
            throw new AccessDeniedException("Not allowed to operate stays of this organization");
        }
    }
}
