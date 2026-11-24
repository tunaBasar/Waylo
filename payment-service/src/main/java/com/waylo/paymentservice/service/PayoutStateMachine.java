package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.PayoutActorType;
import com.waylo.paymentservice.domain.PayoutStatus;

import java.util.Set;

/** The payout transition table (PAYOUT-CONTRACT.md sec. 3.1). Anything not listed is illegal. Pure and static. */
public final class PayoutStateMachine {

    private record Edge(PayoutStatus from, PayoutStatus to, Set<PayoutActorType> actors) {}

    private static final Set<PayoutActorType> ADMIN = Set.of(PayoutActorType.ADMIN);
    private static final Set<PayoutActorType> PARTNER_OR_ADMIN = Set.of(PayoutActorType.PARTNER, PayoutActorType.ADMIN);

    private static final java.util.List<Edge> EDGES = java.util.List.of(
            new Edge(PayoutStatus.REQUESTED, PayoutStatus.APPROVED, ADMIN),            // P2
            new Edge(PayoutStatus.APPROVED, PayoutStatus.PROCESSING, ADMIN),           // P3
            new Edge(PayoutStatus.PROCESSING, PayoutStatus.PAID, ADMIN),               // P4
            new Edge(PayoutStatus.PROCESSING, PayoutStatus.FAILED, ADMIN),             // P5
            new Edge(PayoutStatus.REQUESTED, PayoutStatus.CANCELLED, PARTNER_OR_ADMIN), // P6
            new Edge(PayoutStatus.APPROVED, PayoutStatus.CANCELLED, ADMIN));           // P7

    private PayoutStateMachine() {}

    public static boolean isAllowed(PayoutStatus from, PayoutStatus to, PayoutActorType actor) {
        return EDGES.stream().anyMatch(e -> e.from == from && e.to == to && e.actors.contains(actor));
    }

    public static boolean isTerminal(PayoutStatus status) {
        return status == PayoutStatus.PAID || status == PayoutStatus.FAILED || status == PayoutStatus.CANCELLED;
    }
}
