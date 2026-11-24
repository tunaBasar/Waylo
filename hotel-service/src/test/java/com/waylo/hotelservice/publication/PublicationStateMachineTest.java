package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.domain.PublicationStatus;
import com.waylo.hotelservice.exception.PublicationException;
import com.waylo.hotelservice.publication.PublicationStateMachine.Command;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.waylo.hotelservice.domain.PublicationStatus.*;
import static com.waylo.hotelservice.publication.PublicationStateMachine.Command.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** U1/U2: every (status, command) pair of contract sec. 1.1 is either the listed transition or INVALID_STATE_TRANSITION. */
class PublicationStateMachineTest {

    private static final Map<Command, Set<PublicationStatus>> ALLOWED_FROM = Map.of(
            SUBMIT, EnumSet.of(DRAFT, REJECTED),
            WITHDRAW, EnumSet.of(SUBMITTED),
            APPROVE, EnumSet.of(SUBMITTED),
            REJECT, EnumSet.of(SUBMITTED),
            APPROVE_AND_PUBLISH, EnumSet.of(SUBMITTED),
            PUBLISH, EnumSet.of(APPROVED),
            SUSPEND, EnumSet.of(PUBLISHED),
            REINSTATE, EnumSet.of(SUSPENDED),
            ARCHIVE_ADMIN, EnumSet.of(DRAFT, REJECTED, APPROVED, SUSPENDED),
            ARCHIVE_PARTNER, EnumSet.of(DRAFT, REJECTED));

    @Test
    void fullMatrix_allowedPairsSucceedAndEverythingElseIsRejected() {
        int allowed = 0;
        int rejected = 0;
        for (Command command : Command.values()) {
            for (PublicationStatus from : PublicationStatus.values()) {
                if (ALLOWED_FROM.get(command).contains(from)) {
                    assertThat(PublicationStateMachine.apply(from, command))
                            .as("%s from %s", command, from).isEqualTo(PublicationStateMachine.target(command));
                    allowed++;
                } else {
                    assertThatThrownBy(() -> PublicationStateMachine.apply(from, command))
                            .as("%s from %s", command, from)
                            .isInstanceOfSatisfying(PublicationException.class, e -> {
                                assertThat(e.getStatus()).isEqualTo(409);
                                assertThat(e.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
                                assertThat(e.getDetails()).containsEntry("from", from.name());
                                assertThat(e.getDetails()).containsKey("to");
                            });
                    rejected++;
                }
            }
        }
        // 10 commands x 7 statuses: ALLOWED_FROM lists 15 legal (command, from) pairs (contract 1.1, T2-T9)
        assertThat(allowed).isEqualTo(15);
        assertThat(rejected).isEqualTo(10 * 7 - 15);
    }

    @Test
    void archivedHasNoOutgoingTransition() {
        for (Command command : Command.values()) {
            assertThatThrownBy(() -> PublicationStateMachine.apply(ARCHIVED, command))
                    .isInstanceOf(PublicationException.class);
        }
    }

    @Test
    void targets() {
        assertThat(PublicationStateMachine.target(SUBMIT)).isEqualTo(SUBMITTED);
        assertThat(PublicationStateMachine.target(WITHDRAW)).isEqualTo(DRAFT);
        assertThat(PublicationStateMachine.target(APPROVE)).isEqualTo(APPROVED);
        assertThat(PublicationStateMachine.target(REJECT)).isEqualTo(REJECTED);
        assertThat(PublicationStateMachine.target(PUBLISH)).isEqualTo(PUBLISHED);
        assertThat(PublicationStateMachine.target(SUSPEND)).isEqualTo(SUSPENDED);
        assertThat(PublicationStateMachine.target(REINSTATE)).isEqualTo(PUBLISHED);
        assertThat(PublicationStateMachine.target(ARCHIVE_ADMIN)).isEqualTo(ARCHIVED);
    }

    @Test
    void reasonRules() {
        for (Command c : EnumSet.of(REJECT, SUSPEND, REINSTATE, ARCHIVE_ADMIN, ARCHIVE_PARTNER)) {
            assertThat(PublicationStateMachine.requiresReason(c)).isTrue();
        }
        for (Command c : EnumSet.of(SUBMIT, WITHDRAW, APPROVE, PUBLISH, APPROVE_AND_PUBLISH)) {
            assertThat(PublicationStateMachine.requiresReason(c)).isFalse();
        }
        assertThatThrownBy(() -> PublicationStateMachine.requireReason(null)).isInstanceOf(PublicationException.class)
                .extracting("status").isEqualTo(400);
        assertThatThrownBy(() -> PublicationStateMachine.requireReason(" abcd ")).isInstanceOf(PublicationException.class)
                .extracting("code").isEqualTo("VALIDATION_ERROR");
        assertThatThrownBy(() -> PublicationStateMachine.requireReason("x".repeat(1001))).isInstanceOf(PublicationException.class);
        assertThat(PublicationStateMachine.requireReason("  Adres eksik ")).isEqualTo("Adres eksik");
    }
}
