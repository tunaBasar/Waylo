package com.waylo.flightseatinventoryservice.supplier;

import java.util.List;
import java.util.UUID;

/**
 * Port between Waylo orders and a flight source. The internal implementation sells admin-created
 * flights; a paid NDC/GDS aggregator is a second implementation (see {@link PaidSupplierAdapter}).
 * Callers never depend on which one produced an offer.
 */
public interface FlightSupplier {

    /** Stable code stored on holds and orders, e.g. INTERNAL, DUFFEL. */
    String code();

    List<SupplierDtos.FlightOffer> searchOffers(SupplierDtos.OfferSearch search);

    /** Re-prices an offer id. Fails with OFFER_EXPIRED / INVALID_OFFER. */
    SupplierDtos.FlightOffer getOffer(String offerId);

    /** Price-check plus hold. Idempotent per idempotencyKey. Fails with FARE_CHANGED when the price moved. */
    SupplierDtos.FlightHold hold(SupplierDtos.HoldCommand command);

    SupplierDtos.FlightHold getHold(UUID holdId);

    /** Issues the ticket (PNR + e-ticket number). Called when payment is confirmed. Idempotent. */
    SupplierDtos.FlightHold issueTicket(UUID holdId);

    /** Drops an unticketed hold or voids a ticket. Idempotent. */
    SupplierDtos.FlightHold cancel(UUID holdId);

    /** What the customer gets back and what the supplier returns to us, if cancelled now. */
    SupplierDtos.RefundQuote refundQuote(UUID holdId);
}
