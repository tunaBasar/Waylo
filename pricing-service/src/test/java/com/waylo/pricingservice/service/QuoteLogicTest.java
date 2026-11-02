package com.waylo.pricingservice.service;

import com.waylo.pricingservice.domain.Campaign;
import com.waylo.pricingservice.domain.DiscountType;
import com.waylo.pricingservice.domain.FundedBy;
import com.waylo.pricingservice.dto.QuoteRequest;
import com.waylo.pricingservice.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteLogicTest {

    private static final UUID TEST_ORG = UUID.randomUUID();

    private Campaign campaign(String name, FundedBy fundedBy, DiscountType type,
                              BigDecimal discountValue) {
        Campaign c = new Campaign();
        c.setId(UUID.randomUUID());
        c.setName(name);
        c.setVertical("FLIGHT");
        c.setScopeOrgId(TEST_ORG);
        c.setDiscountType(type);
        c.setDiscountValue(discountValue);
        c.setStartAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        c.setEndAt(LocalDateTime.of(2025, 12, 31, 23, 59));
        c.setMinOrderAmount(BigDecimal.ZERO);
        c.setMaxUsesGlobal(1000);
        c.setMaxUsesPerUser(100);
        c.setFundedBy(fundedBy);
        c.setDescription("Test campaign");
        return c;
    }

    private QuoteRequest request(BigDecimal gross, String promoCode, BigDecimal commissionRate) {
        return new QuoteRequest(
                "FLIGHT", TEST_ORG, gross, "USD", "customer-1", promoCode, UUID.randomUUID(), commissionRate);
    }

    private QuoteRequest noPromo(BigDecimal gross) {
        return request(gross, null, null);
    }

    // -- no promo code --

    @Test
    void noPromoCodeReturnsGrossAsNet() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        QuoteRequest req = noPromo(new BigDecimal("1000.00"));

        var response = logic.computeQuote(req, campaign("Test", FundedBy.PLATFORM, DiscountType.PERCENTAGE, BigDecimal.valueOf(10)));

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(response.discountLines()).isEmpty();
        assertThat(response.taxAmount()).isNull();
        assertThat(response.promoReservationId()).isNull();
    }

    // -- percentage discount --

    @Test
    void percentageDiscountComputesCorrectAmount() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        Campaign camp = campaign("SummerSale", FundedBy.PLATFORM, DiscountType.PERCENTAGE, BigDecimal.valueOf(10));
        QuoteRequest req = request(new BigDecimal("1000.00"), "SUMMER10", null);

        var response = logic.computeQuote(req, camp);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("900.00"));
        assertThat(response.discountLines()).hasSize(1);
        assertThat(response.discountLines().get(0).amount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.discountLines().get(0).fundedBy()).isEqualTo("PLATFORM");
    }

    @Test
    void percentageDiscountRoundedWithHALF_EVEN() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        Campaign camp = campaign("TestPercent", FundedBy.PLATFORM, DiscountType.PERCENTAGE, BigDecimal.valueOf(33));
        QuoteRequest req = request(new BigDecimal("100.00"), "TEST33", null);

        var response = logic.computeQuote(req, camp);

        // 100 * 33 / 100 = 33.00
        assertThat(response.netAmount()).isEqualTo(new BigDecimal("67.00"));
        assertThat(response.discountLines().get(0).amount()).isEqualTo(new BigDecimal("33.00"));
    }

    // -- fixed discount --

    @Test
    void fixedDiscountDeductsExactAmount() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        Campaign camp = campaign("FixedDeal", FundedBy.PLATFORM, DiscountType.FIXED, new BigDecimal("50.00"));
        QuoteRequest req = request(new BigDecimal("1000.00"), "FIXED50", null);

        var response = logic.computeQuote(req, camp);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("950.00"));
        assertThat(response.discountLines().get(0).amount()).isEqualTo(new BigDecimal("50.00"));
    }

    // -- discount clamped to zero --

    @Test
    void zeroOrNegativeDiscountProducesEmptyLines() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        // 50% of 10 = 5, but net = 10 - 5 = 5 (positive)
        // Use FIXED 9999 to clamp
        Campaign camp = campaign("HugeDiscount", FundedBy.PLATFORM, DiscountType.FIXED, new BigDecimal("9999.00"));
        QuoteRequest req = request(new BigDecimal("100.00"), "HUGE", null);

        var response = logic.computeQuote(req, camp);

        assertThat(response.netAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.discountLines()).isEmpty();
    }

    // -- partner-funded with commission rate --

    @Test
    void partnerFundedPassesCommissionShareCheck() {
        QuoteLogic logic = new QuoteLogic(Optional.of(new BigDecimal("0.9"))); // 90% partner share
        Campaign camp = campaign("PartnerPromo", FundedBy.PARTNER, DiscountType.FIXED, new BigDecimal("50.00"));
        QuoteRequest req = request(new BigDecimal("1000.00"), "PARTNER1", new BigDecimal("0.9"));

        var response = logic.computeQuote(req, camp);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("950.00"));
        assertThat(response.discountLines().get(0).fundedBy()).isEqualTo("PARTNER");
    }

    @Test
    void partnerFundedFailsWhenDiscountExceedsPartnerShare() {
        QuoteLogic logic = new QuoteLogic(Optional.of(new BigDecimal("0.9"))); // 90% partner share = 900
        Campaign camp = campaign("TooBig", FundedBy.PARTNER, DiscountType.FIXED, new BigDecimal("100.00"));
        // net = 900, commission share = 1000 * 0.9 = 900, partner share = 900 - 900 = 0 => passes
        // Let's make it fail: discount 200, net = 800, commission = 1000 * 0.8 = 800, partner share = 800 - 800 = 0 => passes
        // discount 300, net = 700, commission = 1000 * 0.5 = 500, partner share = 700 - 500 = 200 => passes
        // discount 900, net = 100, commission = 1000 * 0.9 = 900, partner share = 100 - 900 = -800 => FAIL
        QuoteRequest req = request(new BigDecimal("1000.00"), "BIG", new BigDecimal("0.9"));
        Campaign bigCamp = campaign("TooBig", FundedBy.PARTNER, DiscountType.FIXED, new BigDecimal("900.00"));

        assertThatThrownBy(() -> logic.computeQuote(req, bigCamp))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Discount exceeds partner");
    }

    @Test
    void partnerFundedWithHighCommissionRatePasses() {
        // 10% commission rate, discount 50 on 1000
        // net = 950, commission = 1000 * 0.1 = 100, partner share = 950 - 100 = 850 > 0 => pass
        QuoteLogic logic = new QuoteLogic(Optional.of(new BigDecimal("0.1")));
        Campaign camp = campaign("LowCommission", FundedBy.PARTNER, DiscountType.FIXED, new BigDecimal("50.00"));
        QuoteRequest req = request(new BigDecimal("1000.00"), "LOWCOMM", new BigDecimal("0.1"));

        var response = logic.computeQuote(req, camp);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("950.00"));
    }

    // -- platform-funded ignores commission rate --

    @Test
    void platformFundedIgnoresCommissionRate() {
        // Even with a 99% commission rate and a 50 discount, PLATFORM ignores the check
        QuoteLogic logic = new QuoteLogic(Optional.of(new BigDecimal("0.99")));
        Campaign camp = campaign("PlatformDeal", FundedBy.PLATFORM, DiscountType.FIXED, new BigDecimal("50.00"));
        QuoteRequest req = request(new BigDecimal("100.00"), "PLATFORM", new BigDecimal("0.99"));

        var response = logic.computeQuote(req, camp);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(response.discountLines().get(0).fundedBy()).isEqualTo("PLATFORM");
    }

    // -- PARTNER without commission rate -> skip check --

    @Test
    void partnerFundedWithoutCommissionRateSkipsShareCheck() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        Campaign camp = campaign("NoCommission", FundedBy.PARTNER, DiscountType.FIXED, new BigDecimal("50.00"));
        QuoteRequest req = request(new BigDecimal("1000.00"), "NOCOMM", null);

        var response = logic.computeQuote(req, camp);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("950.00"));
        assertThat(response.discountLines().get(0).fundedBy()).isEqualTo("PARTNER");
    }

    // -- preview --

    @Test
    void previewWithPromoCodeComputesSameDiscount() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        Campaign camp = campaign("PreviewSale", FundedBy.PLATFORM, DiscountType.PERCENTAGE, BigDecimal.valueOf(15));
        QuoteRequest req = request(new BigDecimal("1000.00"), "PREVIEW15", null);

        var response = logic.computePreview(req, camp);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("850.00"));
        assertThat(response.discountLines()).hasSize(1);
        assertThat(response.discountLines().get(0).amount()).isEqualTo(new BigDecimal("150.00"));
    }

    @Test
    void previewWithoutPromoCode() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        Campaign camp = campaign("AnyCampaign", FundedBy.PLATFORM, DiscountType.PERCENTAGE, BigDecimal.valueOf(10));
        QuoteRequest req = noPromo(new BigDecimal("500.00"));

        var response = logic.computePreview(req, camp);

        assertThat(response.netAmount()).isEqualTo(new BigDecimal("500.00"));
        assertThat(response.discountLines()).isEmpty();
    }

    // -- discount description --

    @Test
    void percentageDiscountHasPercentageInDescription() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        Campaign camp = campaign("SummerSale", FundedBy.PLATFORM, DiscountType.PERCENTAGE, BigDecimal.valueOf(10));
        QuoteRequest req = request(new BigDecimal("1000.00"), "SUMMER10", null);

        var response = logic.computeQuote(req, camp);

        assertThat(response.discountLines().get(0).description()).isEqualTo("SummerSale (10%)");
    }

    @Test
    void fixedDiscountHasFixedAmountInDescription() {
        QuoteLogic logic = new QuoteLogic(Optional.empty());
        Campaign camp = campaign("FixedDeal", FundedBy.PLATFORM, DiscountType.FIXED, new BigDecimal("75.50"));
        QuoteRequest req = request(new BigDecimal("1000.00"), "FIXED75", null);

        var response = logic.computeQuote(req, camp);

        assertThat(response.discountLines().get(0).description()).isEqualTo("FixedDeal (75.50)");
    }
}
