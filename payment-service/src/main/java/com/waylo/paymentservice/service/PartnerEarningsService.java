package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.AuthAccessClient;
import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.dto.PartnerEarningsResponse;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.repository.EscrowItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class PartnerEarningsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

    private final EscrowItemRepository escrowRepository;
    private final AuthAccessClient authAccessClient;

    public PartnerEarningsService(EscrowItemRepository escrowRepository, AuthAccessClient authAccessClient) {
        this.escrowRepository = escrowRepository;
        this.authAccessClient = authAccessClient;
    }

    /** from/to are inclusive UTC dates on the time the amount was first held. Fails closed (503 via FeignException) if auth is down. */
    @Transactional(readOnly = true)
    public PartnerEarningsResponse earnings(UUID userId, UUID organizationId, LocalDate from, LocalDate to) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        AuthAccessClient.AccessDecision decision = authAccessClient.check(userId, organizationId, "RESERVATION_READ");
        if (decision == null || !decision.allowed()) {
            throw new AccessDeniedException("Not allowed to view earnings of this organization");
        }
        Instant fromTs = from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTs = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<EscrowItem> items = escrowRepository.findForSupplier(organizationId, fromTs, toTs);

        List<PartnerEarningsResponse.Row> rows = items.stream().map(e -> new PartnerEarningsResponse.Row(
                e.getOrderItemId(), e.getVertical(), e.getGrossAmount(), e.getCommissionAmount(), e.getSupplierAmount(),
                e.getStatus(), e.getHeldAt(), e.getReleasedAt())).toList();
        Map<String, List<EscrowItem>> byCurrency = new TreeMap<>();
        for (EscrowItem e : items) byCurrency.computeIfAbsent(e.getCurrency().trim(), k -> new ArrayList<>()).add(e);
        List<PartnerEarningsResponse.CurrencyTotals> totals = byCurrency.entrySet().stream()
                .map(en -> new PartnerEarningsResponse.CurrencyTotals(en.getKey(), sum(en.getValue(), EscrowStatus.HELD),
                        sum(en.getValue(), EscrowStatus.RELEASED), sum(en.getValue(), EscrowStatus.REFUNDED))).toList();
        if (byCurrency.size() > 1) return new PartnerEarningsResponse(null, null, totals, rows);
        PartnerEarningsResponse.CurrencyTotals only = totals.isEmpty() ? null : totals.get(0);
        return new PartnerEarningsResponse(only == null ? "TRY" : only.currency(),
                new PartnerEarningsResponse.Totals(only == null ? ZERO : only.held(), only == null ? ZERO : only.released(),
                        only == null ? ZERO : only.refunded()), totals, rows);
    }

    private static BigDecimal sum(List<EscrowItem> items, EscrowStatus status) {
        return items.stream().filter(e -> e.getStatus() == status).map(EscrowItem::getSupplierAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
