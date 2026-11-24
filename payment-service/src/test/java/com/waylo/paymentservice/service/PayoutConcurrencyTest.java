package com.waylo.paymentservice.service;

import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A balance is never paid twice (PAYOUT-CONTRACT.md sec. 3.4): wallet row lock, balance computed under the lock, and the
 * database rules (partial unique indexes, ledger uniqueness) all on real PostgreSQL 16 with N threads released together.
 */
class PayoutConcurrencyTest extends PayoutDbSupport {

    static final UUID ADMIN = UUID.randomUUID();

    private Org fundedOrg(String amount) {
        Org o = newOrg();
        releasedItem(o.id, "TRY", amount, "0.00", 0);
        verifiedAccount(o);
        return o;
    }

    /** Runs all callables at the same instant, returns each result or the thrown exception. */
    private static <T> List<Object> race(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return t.call();
                } catch (Throwable e) {
                    return e;
                }
            }));
        }
        ready.await(30, TimeUnit.SECONDS);
        go.countDown();
        List<Object> out = new ArrayList<>();
        for (Future<Object> f : futures) out.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        return out;
    }

    private static boolean isExpectedLoser(Object r) {
        return (r instanceof ConflictException c && (c.getCode().equals("OPEN_PAYOUT_EXISTS")))
                || (r instanceof BusinessRuleException b && b.getRuleCode().equals("INSUFFICIENT_PAYABLE_BALANCE"));
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    @Test
    void eightParallelFullBalanceRequests_exactlyOneSucceeds_ledgerSumsToZero() throws Exception {
        Org o = fundedOrg("900.00");
        List<Callable<PayoutService.RequestResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String key = "race-" + i;
            tasks.add(() -> request(o, key, "900.00"));
        }
        List<Object> results = race(tasks);

        long ok = results.stream().filter(r -> r instanceof PayoutService.RequestResult).count();
        assertThat(ok).isEqualTo(1);
        assertThat(results.stream().filter(r -> !(r instanceof PayoutService.RequestResult)).allMatch(PayoutConcurrencyTest::isExpectedLoser))
                .as("losers: %s", results).isTrue();
        assertThat(count("SELECT count(*) FROM payout_ledger WHERE organization_id = ? AND entry_type = 'RESERVE'", o.id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM partner_payouts WHERE organization_id = ?", o.id)).isEqualTo(1);
        assertThat(balances.rawFor(o.id, "TRY", java.time.Instant.now()).payableRaw()).isEqualByComparingTo("0.00");
    }

    @Test
    void eightParallelRequestsWithTheSameKeyAndBody_oneRow_everyCallerGetsTheSamePayoutId() throws Exception {
        Org o = fundedOrg("900.00");
        List<Callable<PayoutService.RequestResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) tasks.add(() -> request(o, "same-key", "900.00"));
        List<Object> results = race(tasks);

        assertThat(results).allMatch(r -> r instanceof PayoutService.RequestResult);
        assertThat(results.stream().map(r -> ((PayoutService.RequestResult) r).payout().id()).distinct()).hasSize(1);
        assertThat(results.stream().filter(r -> ((PayoutService.RequestResult) r).created()).count()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM partner_payouts WHERE organization_id = ?", o.id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payout_ledger WHERE organization_id = ?", o.id)).isEqualTo(1);
    }

    @Test
    void twoParallelHalfBalanceRequests_stillOneOpenPayout_andAfterCancelTheOtherIsAllowed() throws Exception {
        Org o = fundedOrg("1000.00");
        List<Callable<PayoutService.RequestResult>> tasks = List.of(() -> request(o, "h1", "500.00"), () -> request(o, "h2", "500.00"));
        List<Object> results = race(tasks);

        List<PayoutService.RequestResult> won = results.stream().filter(r -> r instanceof PayoutService.RequestResult)
                .map(r -> (PayoutService.RequestResult) r).toList();
        assertThat(won).hasSize(1);
        assertThat(results.stream().filter(r -> !(r instanceof PayoutService.RequestResult)).allMatch(PayoutConcurrencyTest::isExpectedLoser)).isTrue();
        assertThat(count("SELECT count(*) FROM partner_payouts WHERE organization_id = ? AND status IN ('REQUESTED','APPROVED','PROCESSING')", o.id)).isEqualTo(1);

        payouts.partnerCancel(o.owner, o.id, won.get(0).payout().id());
        assertThat(request(o, "h3", "500.00").created()).isTrue();
    }

    @Test
    void fourParallelMarkPaid_onOnePayout_oneLegPair_othersSameRowOrConflict() throws Exception {
        Org o = fundedOrg("1000.00");
        UUID id = request(o, "paid-race", "400.00").payout().id();
        payouts.approve(ADMIN, "ADMIN", id, null);
        payouts.startProcessing(ADMIN, "ADMIN", id, null);
        String ref = "REF-" + UUID.randomUUID();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) tasks.add(() -> payouts.markPaid(ADMIN, "ADMIN", id, ref, null, null));
        List<Object> results = race(tasks);

        assertThat(results.stream().filter(r -> r instanceof Throwable).map(r -> (Throwable) r).allMatch(t -> t instanceof ConflictException))
                .as("results: %s", results).isTrue();
        assertThat(results.stream().filter(r -> !(r instanceof Throwable))).isNotEmpty();
        assertThat(count("SELECT count(*) FROM payout_ledger WHERE payout_id = ? AND entry_type = 'PAID'", id)).isEqualTo(2);   // one DR + one CR
        assertThat(count("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?", id.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM partner_payouts WHERE id = ?", String.class, id)).isEqualTo("PAID");
    }

    @Test
    void failRacingMarkPaid_exactlyOneWins_ledgerIsReserveReleaseOrReservePaid_neverBoth() throws Exception {
        for (int round = 0; round < 3; round++) {
            Org o = fundedOrg("1000.00");
            UUID id = request(o, "fp-" + round, "400.00").payout().id();
            payouts.approve(ADMIN, "ADMIN", id, null);
            payouts.startProcessing(ADMIN, "ADMIN", id, null);
            List<Callable<Object>> tasks = List.of(
                    () -> payouts.markPaid(ADMIN, "ADMIN", id, "REF-" + UUID.randomUUID(), null, null),
                    () -> payouts.markFailed(ADMIN, "ADMIN", id, "bank rejected the transfer", null));
            List<Object> results = race(tasks);

            assertThat(results.stream().filter(r -> !(r instanceof Throwable)).count()).isEqualTo(1);
            assertThat(results.stream().filter(r -> r instanceof Throwable).allMatch(r -> r instanceof ConflictException)).isTrue();
            String status = jdbc.queryForObject("SELECT status FROM partner_payouts WHERE id = ?", String.class, id);
            int paidLegs = count("SELECT count(*) FROM payout_ledger WHERE payout_id = ? AND entry_type = 'PAID'", id);
            int releases = count("SELECT count(*) FROM payout_ledger WHERE payout_id = ? AND entry_type = 'RESERVE_RELEASE'", id);
            if (status.equals("PAID")) {
                assertThat(paidLegs).isEqualTo(2);
                assertThat(releases).isZero();
            } else {
                assertThat(status).isEqualTo("FAILED");
                assertThat(paidLegs).isZero();
                assertThat(releases).isEqualTo(1);
            }
        }
    }

    @Test
    void requestRacingAConcurrentRelease_noError_theNewReleaseOnlyAddsToTheBalance() throws Exception {
        Org o = fundedOrg("500.00");
        List<Callable<Object>> tasks = List.of(
                () -> request(o, "rel-1", "500.00"),
                () -> releasedItem(o.id, "TRY", "300.00", "0.00", 0));
        List<Object> results = race(tasks);

        assertThat(results.stream().noneMatch(r -> r instanceof Throwable)).as("results: %s", results).isTrue();
        // the reserve took 500, the extra release of 300 is payable afterwards
        assertThat(payable(o.id, "TRY")).isEqualByComparingTo("300.00");
        assertThat(count("SELECT count(*) FROM payout_ledger WHERE organization_id = ? AND entry_type = 'RESERVE'", o.id)).isEqualTo(1);
    }

    @Test
    void parallelAdjustmentDebitVersusRequest_theRequestPathNeverCreatesANegativeBalance() throws Exception {
        for (int round = 0; round < 3; round++) {
            Org o = fundedOrg("1000.00");
            int r = round;
            List<Callable<Object>> tasks = List.of(
                    () -> request(o, "adj-" + r, "1000.00"),
                    () -> payouts.adjust(ADMIN, "ADMIN", o.id, PayoutBalanceServiceTest.adjustment("DEBIT", "600.00", "race-adj")));
            List<Object> results = race(tasks);

            // the debit is an admin decision and may legitimately push the balance negative AFTER a request reserved the money,
            // but a request can never be granted against money that the debit already took
            boolean requestWon = results.get(0) instanceof PayoutService.RequestResult;
            BigDecimal raw = balances.rawFor(o.id, "TRY", java.time.Instant.now()).payableRaw();
            if (requestWon) {
                assertThat(raw).isEqualByComparingTo("-600.00");   // 1000 - 1000 reserve - 600 debit
            } else {
                assertThat(results.get(0)).isInstanceOfAny(BusinessRuleException.class);   // INSUFFICIENT_PAYABLE_BALANCE: debit ran first
                assertThat(raw).isEqualByComparingTo("400.00");
            }
            assertThat(results.get(1)).isNotInstanceOf(Throwable.class);
        }
    }
}
