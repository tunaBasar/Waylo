package com.waylo.flightseatinventoryservice.supplier;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shape of a paid aggregator adapter (Duffel-like). Intentionally has NO implementation and no
 * bean: enabling it needs a new class plus waylo.flight.paid-supplier.enabled=true and credentials
 * in the environment. Nothing here may return invented data or call a network.
 *
 * Differences from the internal supplier that implementations must honour:
 * - {@code hold} returns a hold whose netAmount is the supplier fare we pay at ticketing.
 * - {@code issueTicket} is where we pay the supplier. It must fail with SUPPLIER_BALANCE_INSUFFICIENT
 *   before any ticket exists, and with FARE_CHANGED (carrying the new total) when the supplier price
 *   moved, in which case the caller asks the customer to confirm or cancels and refunds.
 */
public interface PaidSupplierAdapter extends FlightSupplier {

    /** Balance the supplier currently holds for us, in the supplier currency. */
    BigDecimal supplierBalance();

    /** Supplier-side order reference stored with the order (for support and reconciliation). */
    String supplierOrderRef(UUID holdId);
}
