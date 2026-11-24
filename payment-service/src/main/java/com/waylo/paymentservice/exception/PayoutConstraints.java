package com.waylo.paymentservice.exception;

import java.util.Map;

/** Translates payout unique-constraint violations (by constraint name) into business conflicts, never leaking SQL text. */
public final class PayoutConstraints {

    private static final Map<String, String[]> BY_CONSTRAINT = Map.of(
            "uq_payout_open", new String[]{"OPEN_PAYOUT_EXISTS", "An open payout already exists for this currency"},
            "uq_payout_reference", new String[]{"PAYMENT_REFERENCE_IN_USE", "This bank reference is already used by another payout"},
            "uq_bank_iban_hash_live", new String[]{"IBAN_ALREADY_IN_USE", "This IBAN is already registered"},
            "uq_payout_idem", new String[]{"IDEMPOTENCY_KEY_REUSED", "Idempotency key was already used"},
            "uq_payout_ledger_adj_idem", new String[]{"IDEMPOTENCY_KEY_REUSED", "Idempotency key was already used"},
            "uq_payout_ledger_once", new String[]{"INVALID_STATE_TRANSITION", "Payout was already settled"});

    private PayoutConstraints() {}

    /** Returns a ConflictException when a known payout constraint is in the cause chain, otherwise the original exception. */
    public static RuntimeException translate(RuntimeException ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String m = t.getMessage();
            if (m == null) continue;
            for (Map.Entry<String, String[]> e : BY_CONSTRAINT.entrySet()) {
                if (m.contains(e.getKey())) return new ConflictException(e.getValue()[0], e.getValue()[1]);
            }
        }
        return ex;
    }
}
