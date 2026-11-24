package com.waylo.paymentservice.domain;

/** Outcome recorded by PaymentReconciliationJob. MANUAL_REVIEW means a person must decide. */
public enum ReconciliationStatus { ESCROW_REPAIRED, PROVIDER_CANCELLED, MANUAL_REVIEW }
