package com.waylo.paymentservice.domain;

/** What releases the held money: QR redemption at the supplier (hotel, car, bus) or ticket issuance (external flight supplier). */
public enum ReleaseTrigger {
    REDEMPTION, TICKETING
}
