package com.waylo.paymentservice.domain;

import java.util.List;

/** Partner payout lifecycle (PAYOUT-CONTRACT.md sec. 3.1). PAID, FAILED and CANCELLED are terminal. */
public enum PayoutStatus {
    REQUESTED, APPROVED, PROCESSING, PAID, FAILED, CANCELLED;

    /** Statuses whose amount is reserved (open money). At most one open payout per organization and currency. */
    public static final List<PayoutStatus> OPEN = List.of(REQUESTED, APPROVED, PROCESSING);
}
