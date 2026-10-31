package com.waylo.paymentservice.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fake provider for dev and CI. Moves no real money. Never created under the {@code prod} profile
 * (see {@link PaymentProviderConfig}). Every response built from it is labelled provider=TEST so the UI can show "TEST ODEME".
 * Tokens: TEST_OK approves, TEST_DECLINE declines, anything else is rejected as a bad request.
 */
public class TestPaymentProvider implements PaymentProvider {

    public static final String NAME = "TEST";
    public static final String TOKEN_OK = "TEST_OK";
    public static final String TOKEN_DECLINE = "TEST_DECLINE";

    private static final Logger log = LoggerFactory.getLogger(TestPaymentProvider.class);

    @Override
    public String name() { return NAME; }

    @Override
    public boolean requiresPayoutAccount() { return false; }

    @Override
    public AuthorizeResult authorize(AuthorizeRequest request) {
        String token = request.paymentToken();
        if (TOKEN_DECLINE.equals(token)) {
            return AuthorizeResult.declined("CARD_DECLINED");
        }
        if (!TOKEN_OK.equals(token)) {
            throw new IllegalArgumentException("Unknown TEST card token, use " + TOKEN_OK + " or " + TOKEN_DECLINE);
        }
        Map<UUID, String> refs = new HashMap<>();
        request.items().forEach(i -> refs.put(i.orderItemId(), "TEST-ITEM-" + UUID.randomUUID()));
        String paymentId = "TEST-PAY-" + UUID.randomUUID();
        log.info("[TEST PROVIDER] authorized {} {} for order {} as {}", request.amount(), request.currency(), request.orderId(), paymentId);
        return AuthorizeResult.ok(paymentId, refs);
    }

    @Override
    public void releaseItem(ReleaseRequest request) {
        log.info("[TEST PROVIDER] released item {} to sub merchant {}", request.providerItemRef(), request.subMerchantKey());
    }

    @Override
    public void refundItem(RefundRequest request) {
        log.info("[TEST PROVIDER] refunded item {}", request.providerItemRef());
    }

    @Override
    public void cancel(String providerPaymentId) {
        log.info("[TEST PROVIDER] cancelled payment {}", providerPaymentId);
    }
}
