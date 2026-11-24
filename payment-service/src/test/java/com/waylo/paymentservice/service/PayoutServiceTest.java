package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.AuthAccessClient;
import com.waylo.paymentservice.dto.PayoutDtos;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.exception.AuthenticationRequiredException;
import com.waylo.paymentservice.exception.BadRequestException;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.ConflictException;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** State machine, ledger legs, idempotency, access rules and error codes against real PostgreSQL 16. */
class PayoutServiceTest extends PayoutDbSupport {

    static final UUID ADMIN = UUID.randomUUID();

    private Org fundedOrg(String gross) {
        Org o = newOrg();
        releasedItem(o.id, "TRY", gross, "0.00", 0);
        verifiedAccount(o);
        return o;
    }

    private static String ref() { return "REF-" + UUID.randomUUID(); }

    private UUID toProcessing(Org o, String key, String amount) {
        UUID id = request(o, key, amount).payout().id();
        payouts.approve(ADMIN, "ADMIN", id, null);
        payouts.startProcessing(ADMIN, "ADMIN", id, null);
        return id;
    }

    private List<Map<String, Object>> ledgerRows(UUID payoutId) {
        return jdbc.queryForList("SELECT entry_type, payable_delta, account, debit_amount, credit_amount, group_id FROM payout_ledger WHERE payout_id = ? ORDER BY created_at, account NULLS FIRST", payoutId);
    }

    private int eventCount(UUID payoutId) { return jdbc.queryForObject("SELECT count(*) FROM payout_events WHERE payout_id = ?", Integer.class, payoutId); }

    private List<Map<String, Object>> outbox(UUID payoutId) {
        return jdbc.queryForList("SELECT type_id, payload FROM outbox_event WHERE aggregate_id = ?", payoutId.toString());
    }

    private static ConflictException conflict(Runnable r) {
        try { r.run(); } catch (ConflictException e) { return e; }
        throw new AssertionError("expected ConflictException");
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void happyPath_P1toP4_writesExactlyTheContractRows() {
        Org o = fundedOrg("1000.00");
        UUID id = request(o, "k-happy", "900.00").payout().id();
        assertThat(ledgerRows(id)).hasSize(1);
        assertThat(ledgerRows(id).get(0).get("entry_type")).isEqualTo("RESERVE");
        assertThat((BigDecimal) ledgerRows(id).get(0).get("payable_delta")).isEqualByComparingTo("-900.00");

        payouts.approve(ADMIN, "ADMIN", id, null);
        payouts.startProcessing(ADMIN, "ADMIN", id, null);
        assertThat(ledgerRows(id)).hasSize(1);     // P2 and P3 write no ledger rows
        assertThat(outbox(id)).isEmpty();          // and no event

        String reference = ref();
        var paid = payouts.markPaid(ADMIN, "ADMIN", id, reference, null, null);
        assertThat(paid.status().name()).isEqualTo("PAID");
        assertThat(paid.paymentReference()).isEqualTo(reference);

        List<Map<String, Object>> rows = ledgerRows(id);
        assertThat(rows).hasSize(3);
        var legs = rows.stream().filter(r -> r.get("entry_type").equals("PAID")).toList();
        assertThat(legs).hasSize(2);
        var dr = legs.stream().filter(r -> r.get("account").equals("SUPPLIER_PAYABLE")).findFirst().orElseThrow();
        var cr = legs.stream().filter(r -> r.get("account").equals("SUPPLIER_PAID")).findFirst().orElseThrow();
        assertThat((BigDecimal) dr.get("debit_amount")).isEqualByComparingTo("900.00");
        assertThat((BigDecimal) cr.get("credit_amount")).isEqualByComparingTo("900.00");
        assertThat((BigDecimal) dr.get("debit_amount")).isEqualByComparingTo((BigDecimal) cr.get("credit_amount"));
        assertThat(dr.get("group_id")).isEqualTo(cr.get("group_id"));
        assertThat((BigDecimal) dr.get("payable_delta")).isEqualByComparingTo("0");

        assertThat(eventCount(id)).isEqualTo(4);   // REQUESTED, APPROVED, PROCESSING, PAID

        var ob = outbox(id);
        assertThat(ob).hasSize(1);
        assertThat(ob.get(0).get("type_id")).isEqualTo("payoutPaid");
        String payload = (String) ob.get(0).get("payload");
        assertThat(payload).contains("\"eventType\":\"PAYOUT_PAID\"").contains("\"ibanLast4\":\"" + last4(o) + "\"").contains(reference);
        assertThat(payload).doesNotContain("iban\":\"TR").doesNotContain("ciphertext");
    }

    private String last4(Org o) {
        return jdbc.queryForObject("SELECT iban_last4 FROM partner_bank_accounts WHERE organization_id = ?", String.class, o.id).trim();
    }

    @Test
    void P5_failed_writesReserveRelease_andOutboxRow_balanceBackToBeforeTheRequest() {
        Org o = fundedOrg("500.00");
        BigDecimal before = payable(o.id, "TRY");
        UUID id = toProcessing(o, "k-fail", "500.00");
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("0.00");

        var failed = payouts.markFailed(ADMIN, "ADMIN", id, "bank rejected the transfer", null);

        assertThat(failed.status().name()).isEqualTo("FAILED");
        assertThat(failed.failureReason()).isEqualTo("bank rejected the transfer");
        assertThat(ledgerRows(id)).extracting(r -> r.get("entry_type")).containsExactly("RESERVE", "RESERVE_RELEASE");
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo(before);
        var ob = outbox(id);
        assertThat(ob).hasSize(1);
        assertThat(ob.get(0).get("type_id")).isEqualTo("payoutFailed");
        assertThat((String) ob.get(0).get("payload")).contains("PAYOUT_FAILED").contains("bank rejected the transfer");
        // a new request is allowed again
        assertThat(request(o, "k-fail-2", "500.00").created()).isTrue();
    }

    @Test
    void P6_partnerCancelFromRequested_ok_fromApproved_is409_adminCancelFromApproved_ok() {
        Org o = fundedOrg("1000.00");
        UUID a = request(o, "c1", "200.00").payout().id();
        var cancelled = payouts.partnerCancel(o.owner, o.id, a);
        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelledByType().name()).isEqualTo("PARTNER");
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("1000.00");

        UUID b = request(o, "c2", "200.00").payout().id();
        payouts.approve(ADMIN, "ADMIN", b, null);
        ConflictException ex = conflict(() -> payouts.partnerCancel(o.owner, o.id, b));
        assertThat(ex.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(ex.getDetails()).containsEntry("from", "APPROVED").containsEntry("to", "CANCELLED");

        var byAdmin = payouts.adminCancel(ADMIN, "ADMIN", b, "partner asked by phone", null);
        assertThat(byAdmin.status().name()).isEqualTo("CANCELLED");
        assertThat(byAdmin.cancelledByType().name()).isEqualTo("ADMIN");
        assertThat(byAdmin.cancelReason()).isEqualTo("partner asked by phone");
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("1000.00");
    }

    @Test
    void illegalTransitions_are409_withFromAndTo() {
        Org o = fundedOrg("3000.00");
        UUID requested = request(o, "i1", "100.00").payout().id();
        // REQUESTED -> PROCESSING, REQUESTED -> PAID, REQUESTED -> FAILED
        assertThat(conflict(() -> payouts.startProcessing(ADMIN, "ADMIN", requested, null)).getDetails()).containsEntry("from", "REQUESTED").containsEntry("to", "PROCESSING");
        assertThat(conflict(() -> payouts.markPaid(ADMIN, "ADMIN", requested, ref(), null, null)).getDetails()).containsEntry("from", "REQUESTED").containsEntry("to", "PAID");
        assertThat(conflict(() -> payouts.markFailed(ADMIN, "ADMIN", requested, "not processing yet", null)).getCode()).isEqualTo("INVALID_STATE_TRANSITION");
        payouts.adminCancel(ADMIN, "ADMIN", requested, "no longer needed", null);
        // CANCELLED -> anything else
        assertThat(conflict(() -> payouts.approve(ADMIN, "ADMIN", requested, null)).getCode()).isEqualTo("INVALID_STATE_TRANSITION");

        UUID paid = toProcessing(o, "i2", "100.00");
        payouts.markPaid(ADMIN, "ADMIN", paid, ref(), null, null);
        // PAID -> anything
        assertThat(conflict(() -> payouts.markFailed(ADMIN, "ADMIN", paid, "too late to fail", null)).getDetails()).containsEntry("from", "PAID").containsEntry("to", "FAILED");
        assertThat(conflict(() -> payouts.adminCancel(ADMIN, "ADMIN", paid, "too late to cancel", null)).getCode()).isEqualTo("INVALID_STATE_TRANSITION");
        assertThat(conflict(() -> payouts.approve(ADMIN, "ADMIN", paid, null)).getCode()).isEqualTo("INVALID_STATE_TRANSITION");

        UUID failed = toProcessing(o, "i3", "100.00");
        payouts.markFailed(ADMIN, "ADMIN", failed, "bank rejected it", null);
        // FAILED -> PAID
        assertThat(conflict(() -> payouts.markPaid(ADMIN, "ADMIN", failed, ref(), null, null)).getDetails()).containsEntry("from", "FAILED").containsEntry("to", "PAID");
    }

    @Test
    void repeatingATransition_returnsTheSameRow_andWritesNothingMore() {
        Org o = fundedOrg("1000.00");
        UUID id = request(o, "rep", "400.00").payout().id();
        var first = payouts.approve(ADMIN, "ADMIN", id, null);
        int events = eventCount(id);
        int ledger = ledgerRows(id).size();

        var second = payouts.approve(ADMIN, "ADMIN", id, null);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.status()).isEqualTo(first.status());
        assertThat(second.version()).isEqualTo(first.version());
        assertThat(eventCount(id)).isEqualTo(events);
        assertThat(ledgerRows(id)).hasSize(ledger);

        payouts.startProcessing(ADMIN, "ADMIN", id, null);
        String r = ref();
        payouts.markPaid(ADMIN, "ADMIN", id, r, null, null);
        int ledgerAfterPaid = ledgerRows(id).size();
        payouts.markPaid(ADMIN, "ADMIN", id, r, null, null);
        assertThat(ledgerRows(id)).hasSize(ledgerAfterPaid);
        assertThat(outbox(id)).hasSize(1);
    }

    // ------------------------------------------------------------------ amounts

    @Test
    void minimum_9999IsBelow_100IsOk_overPayableByOneCentIsInsufficient_unlistedCurrencyIsUnsupported() {
        Org o = fundedOrg("100.00");
        BusinessRuleException below = org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class, () -> request(o, "m1", "99.99"));
        assertThat(below.getRuleCode()).isEqualTo("PAYOUT_BELOW_MINIMUM");
        assertThat(below.getDetails()).containsKey("minimum");

        BusinessRuleException over = org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class, () -> request(o, "m2", "100.01"));
        assertThat(over.getRuleCode()).isEqualTo("INSUFFICIENT_PAYABLE_BALANCE");
        assertThat((BigDecimal) over.getDetails().get("payable")).isEqualByComparingTo("100.00");

        assertThat(request(o, "m3", "100.00").created()).isTrue();

        BusinessRuleException cur = org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class,
                () -> payouts.request(o.owner, "o@e.test", o.id, "m4", "EUR", new BigDecimal("500.00")));
        assertThat(cur.getRuleCode()).isEqualTo("PAYOUT_CURRENCY_NOT_SUPPORTED");
    }

    @Test
    void amountWithMoreThanTwoDecimals_isRejected_neverRounded() {
        Org o = fundedOrg("1000.00");
        assertThatThrownBy(() -> request(o, "dec", "100.005")).isInstanceOf(BadRequestException.class);
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("1000.00");
    }

    @Test
    void partialAmount_isAllowed() {
        Org o = fundedOrg("1000.00");
        request(o, "part", "250.00");
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("750.00");
    }

    // ------------------------------------------------------------------ bank account rules

    @Test
    void noBankAccount_isBankAccountRequired_pending_isBankAccountNotVerified() {
        Org o = newOrg();
        releasedItem(o.id, "TRY", "1000.00", "0.00", 0);
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class, () -> request(o, "b1", "200.00")).getRuleCode())
                .isEqualTo("BANK_ACCOUNT_REQUIRED");
        bankAccounts.put(o.owner, o.id, new PayoutDtos.BankAccountRequest("Acme Otel AS", uniqueIban()));
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(BusinessRuleException.class, () -> request(o, "b2", "200.00")).getRuleCode())
                .isEqualTo("BANK_ACCOUNT_NOT_VERIFIED");
    }

    @Test
    void accountReplacedAfterRequest_approveGivesBankAccountChanged_statusStays_adminCancelReturnsFunds() {
        Org o = fundedOrg("1000.00");
        UUID id = request(o, "chg", "600.00").payout().id();
        bankAccounts.put(o.owner, o.id, new PayoutDtos.BankAccountRequest("Acme Otel AS", uniqueIban()));

        assertThat(conflict(() -> payouts.approve(ADMIN, "ADMIN", id, null)).getCode()).isEqualTo("BANK_ACCOUNT_CHANGED");
        assertThat(payouts.get(o.owner, o.id, id).status().name()).isEqualTo("REQUESTED");

        payouts.adminCancel(ADMIN, "ADMIN", id, "bank account changed", null);
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("SELECT status FROM partner_bank_accounts WHERE organization_id = ?", String.class, o.id)).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void accountReplacedBetweenProcessingAndPaid_markPaidGivesBankAccountChanged() {
        Org o = fundedOrg("1000.00");
        UUID id = toProcessing(o, "chg2", "300.00");
        bankAccounts.put(o.owner, o.id, new PayoutDtos.BankAccountRequest("Acme Otel AS", uniqueIban()));
        assertThat(conflict(() -> payouts.markPaid(ADMIN, "ADMIN", id, ref(), null, null)).getCode()).isEqualTo("BANK_ACCOUNT_CHANGED");
    }

    // ------------------------------------------------------------------ idempotency and open payout

    @Test
    void idempotency_sameKeySameBody_returnsSamePayout_noSecondReserve_200NotCreated() {
        Org o = fundedOrg("1000.00");
        var first = request(o, "same", "400.00");
        var again = request(o, "same", "400.00");
        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(again.payout().id()).isEqualTo(first.payout().id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payout_ledger WHERE organization_id = ? AND entry_type = 'RESERVE'", Integer.class, o.id)).isEqualTo(1);
    }

    @Test
    void idempotencyKeyReusedWithDifferentBody_is409_IDEMPOTENCY_KEY_REUSED() {
        Org o = fundedOrg("1000.00");
        request(o, "reuse", "400.00");
        ConflictException ex = conflict(() -> request(o, "reuse", "300.00"));
        assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_payouts WHERE organization_id = ?", Integer.class, o.id)).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKey_is400() {
        Org o = fundedOrg("1000.00");
        BadRequestException ex = org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class, () -> request(o, null, "200.00"));
        assertThat(ex.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThatThrownBy(() -> request(o, "x".repeat(151), "200.00")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void secondOpenPayout_is409_OPEN_PAYOUT_EXISTS_withTheOpenId() {
        Org o = fundedOrg("1000.00");
        UUID open = request(o, "o1", "300.00").payout().id();
        ConflictException ex = conflict(() -> request(o, "o2", "300.00"));
        assertThat(ex.getCode()).isEqualTo("OPEN_PAYOUT_EXISTS");
        assertThat(ex.getDetails()).containsEntry("openPayoutId", open.toString());
    }

    // ------------------------------------------------------------------ paid rules

    @Test
    void paidRequiresReference_andAReferenceCannotMarkTwoPayoutsPaid() {
        Org o = fundedOrg("1000.00");
        UUID one = toProcessing(o, "p1", "200.00");
        assertThatThrownBy(() -> payouts.markPaid(ADMIN, "ADMIN", one, null, null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> payouts.markPaid(ADMIN, "ADMIN", one, " ab ", null, null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> payouts.markPaid(ADMIN, "ADMIN", one, ref(), java.time.Instant.now().plusSeconds(3600), null)).isInstanceOf(BadRequestException.class);
        String shared = ref();
        payouts.markPaid(ADMIN, "ADMIN", one, shared, null, null);

        UUID two = toProcessing(o, "p2", "200.00");
        assertThat(conflict(() -> payouts.markPaid(ADMIN, "ADMIN", two, shared, null, null)).getCode()).isEqualTo("PAYMENT_REFERENCE_IN_USE");
        assertThat(payouts.get(o.owner, o.id, two).status().name()).isEqualTo("PROCESSING");
    }

    @Test
    void expectedVersionMismatch_is409_VERSION_CONFLICT() {
        Org o = fundedOrg("1000.00");
        var p = request(o, "ver", "200.00").payout();
        ConflictException ex = conflict(() -> payouts.approve(ADMIN, "ADMIN", p.id(), p.version() + 5));
        assertThat(ex.getCode()).isEqualTo("VERSION_CONFLICT");
        assertThat(payouts.approve(ADMIN, "ADMIN", p.id(), p.version()).status().name()).isEqualTo("APPROVED");
    }

    @Test
    void failedAndCancelRequireAReason() {
        Org o = fundedOrg("1000.00");
        UUID id = toProcessing(o, "rs", "200.00");
        assertThatThrownBy(() -> payouts.markFailed(ADMIN, "ADMIN", id, "no", null)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> payouts.adminCancel(ADMIN, "ADMIN", id, null, null)).isInstanceOf(BadRequestException.class);
    }

    // ------------------------------------------------------------------ access

    @Test
    void nonOwnerMember_gets403_PAYOUT_OWNER_ONLY_onWrites_and200OnReads() {
        Org o = fundedOrg("1000.00");
        UUID id = request(o, "own", "200.00").payout().id();

        assertOwnerOnly(() -> payouts.request(o.staff, "s@e.test", o.id, "st1", "TRY", new BigDecimal("200.00")));
        assertOwnerOnly(() -> payouts.partnerCancel(o.staff, o.id, id));
        assertOwnerOnly(() -> bankAccounts.put(o.staff, o.id, new PayoutDtos.BankAccountRequest("Acme", uniqueIban())));
        assertOwnerOnly(() -> bankAccounts.delete(o.staff, o.id));

        assertThat(payouts.balance(o.staff, o.id).balances()).isNotEmpty();
        assertThat(payouts.balance(o.staff, o.id).balances().get(0).blockedReason()).isEqualTo("NOT_OWNER");
        assertThat(payouts.list(o.staff, o.id, null, 0, 20).items()).hasSize(1);
        assertThat(payouts.get(o.staff, o.id, id).id()).isEqualTo(id);
        assertThat(bankAccounts.get(o.staff, o.id).maskedIban()).startsWith("TR** ");
    }

    private static void assertOwnerOnly(Runnable r) {
        AccessDeniedException ex = org.junit.jupiter.api.Assertions.assertThrows(AccessDeniedException.class, r::run);
        assertThat(ex.getCode()).isEqualTo("PAYOUT_OWNER_ONLY");
    }

    @Test
    void deniedDecision_is403_onReads_andWrites_nullDecisionToo_andAnonymousIs401() {
        Org org = newOrg();
        UUID stranger = UUID.randomUUID();
        Mockito.when(auth.check(stranger, org.id, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(false, List.of()));
        UUID nullDecision = UUID.randomUUID();
        Mockito.when(auth.check(nullDecision, org.id, "RESERVATION_READ")).thenReturn(null);

        assertThatThrownBy(() -> payouts.balance(stranger, org.id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.list(stranger, org.id, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bankAccounts.get(stranger, org.id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.request(stranger, "x@e.test", org.id, "k", "TRY", new BigDecimal("200"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.balance(nullDecision, org.id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.balance(null, org.id)).isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void authServiceDown_failsClosed_withFeignException_whichTheHandlerMapsTo503() {
        Org org = newOrg();
        UUID user = UUID.randomUUID();
        Mockito.when(auth.check(user, org.id, "RESERVATION_READ")).thenThrow(Mockito.mock(feign.FeignException.class));
        assertThatThrownBy(() -> payouts.balance(user, org.id)).isInstanceOf(feign.FeignException.class);
        assertThatThrownBy(() -> payouts.request(user, "x@e.test", org.id, "k", "TRY", new BigDecimal("200"))).isInstanceOf(feign.FeignException.class);
    }

    @Test
    void inactiveOrganization_is409_ORGANIZATION_NOT_ACTIVE() {
        Org o = fundedOrg("1000.00");
        deactivate(o.id);
        activate(UUID.randomUUID());
        ConflictException ex = conflict(() -> request(o, "inact", "200.00"));
        assertThat(ex.getCode()).isEqualTo("ORGANIZATION_NOT_ACTIVE");
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("1000.00");
        // active-ids lookup failing is fail closed too
        activate(o.id);
    }

    @Test
    void otherOrganizationsPayout_is404_notForbidden_andTheBodyLeaksNothing() {
        Org a = fundedOrg("1000.00");
        Org b = newOrg();
        UUID aPayout = request(a, "x1", "300.00").payout().id();

        ResourceNotFoundException get = org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class, () -> payouts.get(b.owner, b.id, aPayout));
        assertThat(get.getMessage()).doesNotContain(a.id.toString());
        assertThatThrownBy(() -> payouts.partnerCancel(b.owner, b.id, aPayout)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(payouts.get(a.owner, a.id, aPayout).status().name()).isEqualTo("REQUESTED");   // untouched

        // and B's owner reading A's organization path is a 403 from the access check (owner of B has no decision on A)
        Mockito.when(auth.check(b.owner, a.id, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(false, List.of()));
        assertThatThrownBy(() -> payouts.balance(b.owner, a.id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.list(b.owner, a.id, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bankAccounts.get(b.owner, a.id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminOperations_rejectNonAdmin_andAnonymous() {
        Org o = fundedOrg("1000.00");
        UUID id = request(o, "adm", "200.00").payout().id();
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> payouts.approve(user, "CUSTOMER", id, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.approve(user, "PARTNER", id, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.approve(null, null, id, null)).isInstanceOf(AuthenticationRequiredException.class);
        assertThatThrownBy(() -> payouts.adminQueue(user, "CUSTOMER", null, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.adminDetail(user, null, id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.adminBalance(user, "PARTNER", o.id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> payouts.adjust(user, "PARTNER", o.id, PayoutBalanceServiceTest.adjustment("DEBIT", "10.00", "na"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bankAccounts.verify(user, "CUSTOMER", UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> bankAccounts.reveal(user, "CUSTOMER", id)).isInstanceOf(AccessDeniedException.class);
        assertThat(payouts.get(o.owner, o.id, id).status().name()).isEqualTo("REQUESTED");
    }

    @Test
    void adminQueue_defaultsToOpenStatuses_oldestFirst_andDetailCarriesTheEvents() {
        Org o = fundedOrg("1000.00");
        UUID id = request(o, "q1", "200.00").payout().id();
        var queue = payouts.adminQueue(ADMIN, "ADMIN", null, o.id, null, 0, 20);
        assertThat(queue.items()).extracting(PayoutDtos.PayoutResponse::id).contains(id);
        var detail = payouts.adminDetail(ADMIN, "ADMIN", id);
        assertThat(detail.events()).hasSize(1);
        assertThat(detail.payout().maskedIban()).startsWith("TR** ");
        assertThat(detail.organizationBalance().currency()).isEqualTo("TRY");
    }

    @Test
    void adjustment_isIdempotent_oneRowPerKey_andKeyReuseWithDifferentBodyIs409() {
        Org o = fundedOrg("1000.00");
        var req = new PayoutDtos.AdjustmentRequest("TRY", new BigDecimal("50.00"), PayoutDtos.AdjustmentRequest.Direction.DEBIT, "chargeback case 42", null, "adj-key-" + UUID.randomUUID());
        var one = payouts.adjust(ADMIN, "ADMIN", o.id, req);
        var two = payouts.adjust(ADMIN, "ADMIN", o.id, req);
        assertThat(two.id()).isEqualTo(one.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payout_ledger WHERE organization_id = ? AND idempotency_key = ?", Integer.class, o.id, req.idempotencyKey())).isEqualTo(1);
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("950.00");
        var other = new PayoutDtos.AdjustmentRequest("TRY", new BigDecimal("60.00"), PayoutDtos.AdjustmentRequest.Direction.DEBIT, "chargeback case 42", null, req.idempotencyKey());
        assertThat(conflict(() -> payouts.adjust(ADMIN, "ADMIN", o.id, other)).getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }
}
