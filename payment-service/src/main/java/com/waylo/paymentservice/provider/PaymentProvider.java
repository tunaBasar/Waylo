package com.waylo.paymentservice.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment provider port. Callers (PaymentIntentService, EscrowService) only know this interface.
 * Shaped after iyzico Pazaryeri so the real adapter drops in without changing callers:
 * <ul>
 *   <li>authorize = payment with per item {@code subMerchantKey} + {@code subMerchantPrice}; funds are captured into the
 *       provider's protected pool (escrow) and returned per item as an item ref (iyzico paymentTransactionId)</li>
 *   <li>releaseItem = item approval (money moves to the sub merchant, provider keeps the commission share)</li>
 *   <li>refundItem = item refund (only valid before approval)</li>
 *   <li>cancel = void a whole payment (iyzico same-day cancel)</li>
 * </ul>
 * Implementations must be idempotent per {@code idempotencyKey} / item ref and throw {@link ProviderException} on failure.
 */
public interface PaymentProvider {

    /** Provider code stored on the intent and returned to clients, e.g. TEST or IYZICO. */
    String name();

    /** True when every supplier needs an ACTIVE row in supplier_payout_accounts (real marketplaces). TEST fabricates keys. */
    boolean requiresPayoutAccount();

    AuthorizeResult authorize(AuthorizeRequest request);

    void releaseItem(ReleaseRequest request);

    void refundItem(RefundRequest request);

    void cancel(String providerPaymentId);

    /**
     * @param paymentToken opaque token produced by the provider's client side (never raw card data). TEST: TEST_OK or TEST_DECLINE.
     */
    record AuthorizeRequest(UUID intentId, UUID orderId, UUID customerId, BigDecimal amount, String currency,
                            String paymentToken, String idempotencyKey, List<ItemAllocation> items) {}

    /** subMerchantPrice = what the supplier receives on release (gross minus commission). */
    record ItemAllocation(UUID orderItemId, String subMerchantKey, BigDecimal grossAmount, BigDecimal subMerchantPrice) {}

    /** On success providerPaymentId and itemRefs (orderItemId -> provider item ref) are set, else failureCode. */
    record AuthorizeResult(boolean success, String providerPaymentId, String failureCode, Map<UUID, String> itemRefs) {
        public static AuthorizeResult ok(String providerPaymentId, Map<UUID, String> itemRefs) {
            return new AuthorizeResult(true, providerPaymentId, null, itemRefs);
        }
        public static AuthorizeResult declined(String failureCode) {
            return new AuthorizeResult(false, null, failureCode, Map.of());
        }
    }

    record ReleaseRequest(String providerPaymentId, String providerItemRef, String subMerchantKey,
                          BigDecimal subMerchantAmount, BigDecimal commissionAmount, String currency) {}

    record RefundRequest(String providerPaymentId, String providerItemRef, BigDecimal amount, String currency) {}
}
