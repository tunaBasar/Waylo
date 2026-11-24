package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.PayoutActorType;
import com.waylo.paymentservice.domain.PayoutStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.waylo.paymentservice.domain.PayoutActorType.ADMIN;
import static com.waylo.paymentservice.domain.PayoutActorType.PARTNER;
import static com.waylo.paymentservice.domain.PayoutStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/** The transition table of PAYOUT-CONTRACT.md sec. 3.1, exhaustively: every (from, to, actor) not listed is illegal. */
class PayoutStateMachineTest {

    private record T(PayoutStatus from, PayoutStatus to, PayoutActorType actor) {}

    private static final Set<T> LEGAL = Set.of(
            new T(REQUESTED, APPROVED, ADMIN),
            new T(APPROVED, PROCESSING, ADMIN),
            new T(PROCESSING, PAID, ADMIN),
            new T(PROCESSING, FAILED, ADMIN),
            new T(REQUESTED, CANCELLED, PARTNER),
            new T(REQUESTED, CANCELLED, ADMIN),
            new T(APPROVED, CANCELLED, ADMIN));

    @Test
    void everyCombination_matchesTheContractTable() {
        for (PayoutStatus from : values()) {
            for (PayoutStatus to : values()) {
                for (PayoutActorType actor : PayoutActorType.values()) {
                    assertThat(PayoutStateMachine.isAllowed(from, to, actor))
                            .as("%s -> %s by %s", from, to, actor)
                            .isEqualTo(LEGAL.contains(new T(from, to, actor)));
                }
            }
        }
    }

    @Test
    void terminalStates_haveNoOutgoingTransition() {
        for (PayoutStatus s : new PayoutStatus[]{PAID, FAILED, CANCELLED}) {
            assertThat(PayoutStateMachine.isTerminal(s)).isTrue();
            for (PayoutStatus to : values()) {
                for (PayoutActorType a : PayoutActorType.values()) assertThat(PayoutStateMachine.isAllowed(s, to, a)).isFalse();
            }
        }
        assertThat(PayoutStateMachine.isTerminal(REQUESTED)).isFalse();
        assertThat(PayoutStateMachine.isTerminal(APPROVED)).isFalse();
        assertThat(PayoutStateMachine.isTerminal(PROCESSING)).isFalse();
    }

    @Test
    void partnerCanOnlyCancelARequestedPayout() {
        assertThat(PayoutStateMachine.isAllowed(APPROVED, CANCELLED, PARTNER)).isFalse();
        assertThat(PayoutStateMachine.isAllowed(REQUESTED, APPROVED, PARTNER)).isFalse();
    }

    @Test
    void openStatuses_areTheThreeReservingOnes() {
        assertThat(PayoutStatus.OPEN).containsExactly(REQUESTED, APPROVED, PROCESSING);
    }
}
