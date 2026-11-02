package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.PricingClient;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.exception.BusinessRuleException;
import com.waylo.packagebookingservice.exception.PricingUnavailableException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingApplierTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final PricingClient client = mock(PricingClient.class);
    private final PricingApplier applier = new PricingApplier(client);
    private final UUID org = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();

    private PricingApplier.Priced price(String promo) {
        return applier.price("ARAÇ", org, new BigDecimal("1000.00"), "TRY", customer, promo, order);
    }

    @Test
    void noPromoUnreachable_sellsAtGross() {
        when(client.quote(any())).thenThrow(new RuntimeException("connection refused"));
        PricingApplier.Priced p = price(null);
        assertThat(p.net()).isEqualByComparingTo("1000.00");
        assertThat(p.discount()).isEqualByComparingTo("0");
        assertThat(p.fundedBy()).isNull();
        assertThat(p.tax()).isNull();
    }

    @Test
    void promoUnreachable_503() {
        when(client.quote(any())).thenThrow(new RuntimeException("connection refused"));
        assertThatThrownBy(() -> price("SAVE10")).isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    void promoRejected_codePassedThrough() {
        when(client.quote(any())).thenThrow(new BusinessRuleException("PROMO_CODE_INVALID", "nope"));
        assertThatThrownBy(() -> price("BAD")).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getRuleCode()).isEqualTo("PROMO_CODE_INVALID"));
    }

    @Test
    void platformDiscount_storedAndNetIsGrossMinusDiscount() throws Exception {
        when(client.quote(any())).thenReturn(JSON.readTree(
                "{\"netAmount\":900.00,\"discountLines\":[{\"description\":\"x\",\"amount\":100.00,\"fundedBy\":\"PLATFORM\"}],"
                        + "\"taxAmount\":150.00,\"promoReservationId\":\"" + UUID.randomUUID() + "\"}"));
        PricingApplier.Priced p = price("SAVE10");
        OrderItem item = new OrderItem();
        item.setQuantity(1);
        p.applyTo(item);
        assertThat(item.getGrossAmount()).isEqualByComparingTo("1000.00");
        assertThat(item.getDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(item.getDiscountFundedBy()).isEqualTo("PLATFORM");
        assertThat(item.getNetAmount()).isEqualByComparingTo("900.00");
        assertThat(item.getUnitAmount()).isEqualByComparingTo("900.00");
        assertThat(item.getTaxAmount()).isEqualByComparingTo("150.00");
        assertThat(item.getPromoCode()).isEqualTo("SAVE10");
    }

    @Test
    void envelopeWrappedQuoteIsAccepted() throws Exception {
        when(client.quote(any())).thenReturn(JSON.readTree(
                "{\"success\":true,\"data\":{\"netAmount\":950.00,\"discountLines\":[{\"amount\":50.00,\"fundedBy\":\"PARTNER\"}]}}"));
        PricingApplier.Priced p = price("P5");
        assertThat(p.discount()).isEqualByComparingTo("50.00");
        assertThat(p.fundedBy()).isEqualTo("PARTNER");
        assertThat(p.net()).isEqualByComparingTo("950.00");
    }

    @Test
    void zeroDiscountLines_noFundedBy() throws Exception {
        when(client.quote(any())).thenReturn(JSON.readTree("{\"netAmount\":1000.00,\"discountLines\":[]}"));
        PricingApplier.Priced p = price(null);
        assertThat(p.discount()).isEqualByComparingTo("0");
        assertThat(p.fundedBy()).isNull();
    }

    @Test
    void releaseSwallowsFailures() {
        Mockito.doThrow(new RuntimeException("down")).when(client).release(order);
        applier.release(order);
        verify(client).release(order);
    }
}
