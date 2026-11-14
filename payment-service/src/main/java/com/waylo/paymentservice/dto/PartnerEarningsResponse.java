package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.Vertical;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Partner-facing earnings. partnerNet is the supplier share. Totals are partner net per escrow state. */
public record PartnerEarningsResponse(String currency, Totals totals, List<Row> items) {

    public record Row(UUID orderItemId, Vertical vertical, BigDecimal gross, BigDecimal commission,
                      BigDecimal partnerNet, EscrowStatus escrowState, Instant heldAt, Instant releasedAt) {}

    public record Totals(BigDecimal held, BigDecimal released, BigDecimal refunded) {}
}
