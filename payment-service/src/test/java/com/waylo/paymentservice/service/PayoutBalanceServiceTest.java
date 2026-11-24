package com.waylo.paymentservice.service;

import com.waylo.paymentservice.dto.PayoutDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Payable balance against the real queries on PostgreSQL 16 (PAYOUT-CONTRACT.md sec. 2). */
class PayoutBalanceServiceTest extends PayoutDbSupport {

    private PayoutBalanceService.Raw raw(UUID org, String cur) { return balances.rawFor(org, cur, Instant.now()); }

    @Test
    void onlyReleasedItemsCount_heldAndRefundedAreExcluded() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "100.00", 0);
        heldItem(o.id, "TRY", "500.00", "50.00");
        refundedItem(o.id, "TRY", "300.00", "30.00");

        var r = raw(o.id, "TRY");
        assertThat(r.payable()).isEqualByComparingTo("900.00");
        assertThat(r.held()).isEqualByComparingTo("450.00");   // estimated, never part of payable
        assertThat(r.released()).isEqualByComparingTo("900.00");
        assertThat(r.reconciles()).isTrue();
    }

    @Test
    void supplierAmountIsAlreadyNetOfCommission_tenPercentOn100Gives90() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "100.00", "10.00", 0);
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("90.00");
    }

    @Test
    void partnerFundedDiscount_payableIsSupplierAmountNotGross() {
        Org o = newOrg();
        // gross 1000, partner funds a 200 discount, commission 80 on the net: supplier 720
        partnerDiscountItem(o.id, "1000.00", "200.00", "80.00");
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("720.00");
        assertThat(raw(o.id, "TRY").reconciles()).isTrue();
    }

    @Test
    void holdback_releasedYesterdayWithTwoDayHoldbackIsNotYetPayable_thenBecomesPayable() {
        Org o = newOrg();
        int original = settings.getHoldbackDays();
        try {
            settings.setHoldbackDays(2);
            releasedItem(o.id, "TRY", "100.00", "10.00", 1);
            var r = raw(o.id, "TRY");
            assertThat(r.payable()).isEqualByComparingTo("0.00");
            assertThat(r.inHoldback()).isEqualByComparingTo("90.00");
            assertThat(r.reconciles()).isTrue();

            releasedItem(o.id, "TRY", "200.00", "20.00", 3);   // released 3 days ago: past the cutoff
            r = raw(o.id, "TRY");
            assertThat(r.payable()).isEqualByComparingTo("180.00");
            assertThat(r.inHoldback()).isEqualByComparingTo("90.00");

            settings.setHoldbackDays(0);                        // cutoff passes
            assertThat(payable(o.id, "TRY")).isEqualByComparingTo("270.00");
        } finally {
            settings.setHoldbackDays(original);
        }
    }

    @Test
    void currencies_areIsolated_aTryPayoutNeverTouchesEur() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "100.00", 0);
        releasedItem(o.id, "EUR", "50.00", "0.00", 0);
        verifiedAccount(o);

        var before = balances.balance(o.id, true, true, Instant.now());
        assertThat(before.balances()).extracting(PayoutDtos.BalanceItem::currency).containsExactly("EUR", "TRY");
        assertThat(item(before, "TRY").payable()).isEqualByComparingTo("900.00");
        assertThat(item(before, "EUR").payable()).isEqualByComparingTo("50.00");

        request(o, "k1", "900.00");

        var after = balances.balance(o.id, true, true, Instant.now());
        assertThat(item(after, "TRY").payable()).isEqualByComparingTo("0.00");
        assertThat(item(after, "TRY").reserved()).isEqualByComparingTo("900.00");
        assertThat(item(after, "EUR").payable()).isEqualByComparingTo("50.00");
        assertThat(item(after, "EUR").reserved()).isEqualByComparingTo("0.00");
        assertThat(item(after, "EUR").canRequest()).isFalse();
        assertThat(item(after, "EUR").blockedReason()).isEqualTo("CURRENCY_NOT_SUPPORTED");
    }

    @Test
    void reserveReducesPayable_failAndCancelGiveItBack_paidKeepsItReducedAndRaisesPaidTotal() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "0.00", 0);
        verifiedAccount(o);
        UUID admin = UUID.randomUUID();

        UUID cancelled = request(o, "a", "300.00").payout().id();
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("700.00");
        payouts.partnerCancel(o.owner, o.id, cancelled);
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("1000.00");

        UUID failed = request(o, "b", "300.00").payout().id();
        payouts.approve(admin, "ADMIN", failed, null);
        payouts.startProcessing(admin, "ADMIN", failed, null);
        payouts.markFailed(admin, "ADMIN", failed, "bank rejected the transfer", null);
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("1000.00");

        UUID paid = request(o, "c", "400.00").payout().id();
        payouts.approve(admin, "ADMIN", paid, null);
        payouts.startProcessing(admin, "ADMIN", paid, null);
        payouts.markPaid(admin, "ADMIN", paid, "REF-" + UUID.randomUUID(), null, null);

        var r = raw(o.id, "TRY");
        assertThat(r.payable()).isEqualByComparingTo("600.00");
        assertThat(r.paidTotal()).isEqualByComparingTo("400.00");
        assertThat(r.reserved()).isEqualByComparingTo("0.00");
        assertThat(r.reconciles()).isTrue();
    }

    @Test
    void refundAfterRelease_debitReducesPayable_biggerDebitGivesOwed_creditRestores() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "100.00", 0);   // payable 900
        verifiedAccount(o);
        UUID admin = UUID.randomUUID();

        payouts.adjust(admin, "ADMIN", o.id, adjustment("DEBIT", "200.00", "d1"));
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("700.00");

        payouts.adjust(admin, "ADMIN", o.id, adjustment("DEBIT", "5000.00", "d2"));
        var r = raw(o.id, "TRY");
        assertThat(r.payable()).isEqualByComparingTo("0.00");
        assertThat(r.owed()).isEqualByComparingTo("4300.00");
        assertThat(r.reconciles()).isTrue();
        var item = item(balances.balance(o.id, true, true, Instant.now()), "TRY");
        assertThat(item.canRequest()).isFalse();
        assertThat(item.blockedReason()).isEqualTo("NEGATIVE_BALANCE");
        assertThat(item.owed()).isEqualByComparingTo("4300.00");

        payouts.adjust(admin, "ADMIN", o.id, adjustment("CREDIT", "5200.00", "c1"));
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("900.00");
        // net of all three adjustments: -200 - 5000 + 5200 = 0
        assertThat(raw(o.id, "TRY").adjustmentsNet()).isEqualByComparingTo("0.00");
    }

    @Test
    void reconciliationIdentity_holdsAcrossAMixedScenario_andMatchesTheLedgerCredits() {
        Org o = newOrg();
        int original = settings.getHoldbackDays();
        try {
            settings.setHoldbackDays(2);
            releasedItem(o.id, "TRY", "2000.00", "200.00", 5);   // payable 1800
            releasedItem(o.id, "TRY", "500.00", "50.00", 0);     // in holdback 450
            heldItem(o.id, "TRY", "700.00", "70.00");
            refundedItem(o.id, "TRY", "900.00", "90.00");
            verifiedAccount(o);
            UUID admin = UUID.randomUUID();
            UUID p1 = request(o, "m1", "600.00").payout().id();
            payouts.approve(admin, "ADMIN", p1, null);
            payouts.startProcessing(admin, "ADMIN", p1, null);
            payouts.markPaid(admin, "ADMIN", p1, "REF-" + UUID.randomUUID(), null, null);
            UUID p2 = request(o, "m2", "300.00").payout().id();     // open
            payouts.adjust(admin, "ADMIN", o.id, adjustment("DEBIT", "100.00", "m-adj"));

            var r = raw(o.id, "TRY");
            assertThat(r.eligible()).isEqualByComparingTo("1800.00");
            assertThat(r.inHoldback()).isEqualByComparingTo("450.00");
            assertThat(r.payable()).isEqualByComparingTo("800.00");   // 1800 - 600 - 300 - 100
            assertThat(r.reserved()).isEqualByComparingTo("300.00");
            assertThat(r.paidTotal()).isEqualByComparingTo("600.00");
            assertThat(r.adjustmentsNet()).isEqualByComparingTo("-100.00");
            assertThat(r.ledgerPayable()).isEqualByComparingTo("2250.00");   // SUM(credit SUPPLIER_PAYABLE) of this org's items
            assertThat(r.released()).isEqualByComparingTo(r.ledgerPayable());
            // released = payable + releasedNotYetPayable + reserved + paidTotal - adjustmentsNet
            assertThat(r.payable().add(r.inHoldback()).add(r.reserved()).add(r.paidTotal()).subtract(r.adjustmentsNet())).isEqualByComparingTo(r.released());
            assertThat(r.reconciles()).isTrue();
            assertThat(p2).isNotNull();
        } finally {
            settings.setHoldbackDays(original);
        }
    }

    @Test
    void blockedReason_isOrderedAndExplicit() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "0.00", 0);
        assertThat(item(balances.balance(o.id, false, true, Instant.now()), "TRY").blockedReason()).isEqualTo("NOT_OWNER");
        assertThat(item(balances.balance(o.id, true, false, Instant.now()), "TRY").blockedReason()).isEqualTo("ORGANIZATION_NOT_ACTIVE");
        assertThat(item(balances.balance(o.id, true, true, Instant.now()), "TRY").blockedReason()).isEqualTo("NO_BANK_ACCOUNT");
        var acc = bankAccounts.put(o.owner, o.id, new PayoutDtos.BankAccountRequest("Acme", uniqueIban()));
        assertThat(item(balances.balance(o.id, true, true, Instant.now()), "TRY").blockedReason()).isEqualTo("BANK_NOT_VERIFIED");
        bankAccounts.verify(UUID.randomUUID(), "ADMIN", acc.id());
        assertThat(item(balances.balance(o.id, true, true, Instant.now()), "TRY").canRequest()).isTrue();
        assertThat(item(balances.balance(o.id, true, true, Instant.now()), "TRY").minimumPayout()).isEqualByComparingTo("100.00");

        Org small = newOrg();
        releasedItem(small.id, "TRY", "50.00", "0.00", 0);
        verifiedAccount(small);
        assertThat(item(balances.balance(small.id, true, true, Instant.now()), "TRY").blockedReason()).isEqualTo("BELOW_MINIMUM");
    }

    static PayoutDtos.BalanceItem item(PayoutDtos.BalanceResponse r, String currency) {
        return r.balances().stream().filter(b -> b.currency().equals(currency)).findFirst().orElseThrow();
    }

    static PayoutDtos.AdjustmentRequest adjustment(String direction, String amount, String key) {
        return new PayoutDtos.AdjustmentRequest("TRY", new BigDecimal(amount), PayoutDtos.AdjustmentRequest.Direction.valueOf(direction),
                "chargeback after release", null, key + "-" + UUID.randomUUID());
    }
}
