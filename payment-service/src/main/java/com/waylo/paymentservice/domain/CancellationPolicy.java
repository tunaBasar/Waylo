package com.waylo.paymentservice.domain;

/** Policy snapshot taken on the order item at order creation (CANCELLATION-CONTRACT.md section 2). */
public enum CancellationPolicy { FREE_CANCELLATION, FREE_UNTIL, NON_REFUNDABLE }
