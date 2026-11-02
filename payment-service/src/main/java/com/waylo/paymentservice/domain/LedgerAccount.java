package com.waylo.paymentservice.domain;

/**
 * CUSTOMER_PAYMENT is the counter account of the money received from the customer (debit side of the capture), it is not in the
 * original account list but a balanced pair needs a counterparty. SUPPLIER_PAID and PROVIDER_FEE are placeholders, nothing writes
 * them yet (the provider pays the sub merchant when an item is approved, provider fees are unknown until a real provider is live).
 */
/** PLATFORM_DISCOUNT_COST: debit side, the platform bears a PLATFORM-funded promo discount (marketing cost). */
public enum LedgerAccount {
    CUSTOMER_PAYMENT, CUSTOMER_FUNDS_HELD, PLATFORM_COMMISSION, SUPPLIER_PAYABLE, SUPPLIER_PAID, CUSTOMER_REFUND, PROVIDER_FEE, PLATFORM_DISCOUNT_COST
}
