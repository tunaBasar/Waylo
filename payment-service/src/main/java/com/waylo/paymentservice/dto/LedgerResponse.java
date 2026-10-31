package com.waylo.paymentservice.dto;

import com.waylo.paymentservice.domain.LedgerAccount;
import com.waylo.paymentservice.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerResponse(UUID orderId, BigDecimal balance, List<Entry> entries) {

    public record Entry(UUID entryId, UUID groupId, Instant occurredAt, UUID orderItemId, LedgerAccount account,
                        BigDecimal debitAmount, BigDecimal creditAmount, String currency, UUID correlationId, String description) {
        public static Entry from(LedgerEntry e) {
            return new Entry(e.getEntryId(), e.getGroupId(), e.getOccurredAt(), e.getOrderItemId(), e.getAccount(),
                    e.getDebitAmount(), e.getCreditAmount(), e.getCurrency(), e.getCorrelationId(), e.getDescription());
        }
    }
}
