package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.AuthAccessClient;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.PartnerOrderItemPage;
import com.waylo.packagebookingservice.dto.PartnerOrderItemResponse;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.AuthenticationRequiredException;
import com.waylo.packagebookingservice.exception.AuthorizationUnavailableException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class PartnerOrderService {

    static final int MAX_PAGE_SIZE = 100;
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");
    private static final Logger log = LoggerFactory.getLogger(PartnerOrderService.class);

    private final OrderItemRepository orderItemRepository;
    private final AuthAccessClient authAccessClient;

    public PartnerOrderService(OrderItemRepository orderItemRepository, AuthAccessClient authAccessClient) {
        this.orderItemRepository = orderItemRepository;
        this.authAccessClient = authAccessClient;
    }

    /** from/to are inclusive UTC dates on the order creation time. Fails closed (503) if auth-service is unreachable. */
    @Transactional(readOnly = true)
    public PartnerOrderItemPage list(UUID userId, UUID organizationId, OrderStatus status,
                                     LocalDate from, LocalDate to, int page, int size) {
        if (userId == null) {
            throw new AuthenticationRequiredException("Authentication required");
        }
        requireReservationRead(userId, organizationId);

        Instant fromTs = from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTs = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        Page<PartnerOrderItemResponse> result =
                orderItemRepository.findPartnerItems(organizationId,
                        status == null ? java.util.List.of(OrderStatus.values()) : java.util.List.of(status), fromTs, toTs, pageable);
        return new PartnerOrderItemPage(result.getContent(), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private void requireReservationRead(UUID userId, UUID organizationId) {
        AuthAccessClient.AccessDecision decision;
        try {
            decision = authAccessClient.check(userId, organizationId, "RESERVATION_READ");
        } catch (RuntimeException e) {
            log.warn("Authorization service unavailable for userId={}, permission=RESERVATION_READ", userId);
            throw new AuthorizationUnavailableException("Authorization service unavailable");
        }
        if (decision == null || !decision.allowed()) {
            throw new AccessDeniedException("Not allowed to view reservations of this organization");
        }
    }
}
