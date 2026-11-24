package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.domain.PublicationStatus;
import com.waylo.hotelservice.exception.PublicationException;

import java.util.Map;

import static com.waylo.hotelservice.domain.PublicationStatus.*;

/** Pure transition table of contract sec. 1.1 (hotel level). Content-change transitions (T10/T11) do not move the status. */
public final class PublicationStateMachine {

    public enum Command {
        SUBMIT, WITHDRAW, APPROVE, REJECT, PUBLISH, APPROVE_AND_PUBLISH, SUSPEND, REINSTATE, ARCHIVE_ADMIN, ARCHIVE_PARTNER
    }

    private PublicationStateMachine() {
    }

    /** Target status of {@code command} from {@code from}, or 409 INVALID_STATE_TRANSITION (carries from and to). */
    public static PublicationStatus apply(PublicationStatus from, Command command) {
        boolean ok = switch (command) {
            case SUBMIT -> from == DRAFT || from == REJECTED;
            case WITHDRAW, APPROVE, REJECT, APPROVE_AND_PUBLISH -> from == SUBMITTED;
            case PUBLISH -> from == APPROVED;
            case SUSPEND -> from == PUBLISHED;
            case REINSTATE -> from == SUSPENDED;
            case ARCHIVE_ADMIN -> from == DRAFT || from == REJECTED || from == APPROVED || from == SUSPENDED;
            case ARCHIVE_PARTNER -> from == DRAFT || from == REJECTED;
        };
        PublicationStatus to = target(command);
        if (!ok) {
            throw new PublicationException(409, "INVALID_STATE_TRANSITION",
                    "Cannot " + command + " a hotel in status " + from,
                    Map.of("from", from.name(), "to", to.name()));
        }
        return to;
    }

    public static PublicationStatus target(Command command) {
        return switch (command) {
            case SUBMIT -> SUBMITTED;
            case WITHDRAW -> DRAFT;
            case APPROVE -> APPROVED;
            case REJECT -> REJECTED;
            case PUBLISH, APPROVE_AND_PUBLISH, REINSTATE -> PUBLISHED;
            case SUSPEND -> SUSPENDED;
            case ARCHIVE_ADMIN, ARCHIVE_PARTNER -> ARCHIVED;
        };
    }

    public static boolean requiresReason(Command command) {
        return switch (command) {
            case REJECT, SUSPEND, REINSTATE, ARCHIVE_ADMIN, ARCHIVE_PARTNER -> true;
            default -> false;
        };
    }

    /** 5..1000 chars after trimming, else 400 VALIDATION_ERROR. */
    public static String requireReason(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < 5 || trimmed.length() > 1000) {
            throw PublicationException.validation("reason is required (5 to 1000 characters)");
        }
        return trimmed;
    }
}
