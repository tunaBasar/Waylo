package com.waylo.paymentservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The append-only tables and the V9 constraints, against the real migration on PostgreSQL 16. Raw SQL, no service layer. */
class PayoutLedgerImmutabilityTest extends PayoutDbSupport {

    private UUID insertPayout(UUID org, String status) {
        UUID acc = UUID.randomUUID();
        jdbc.update("INSERT INTO partner_bank_accounts (id, organization_id, account_holder_name, iban_ciphertext, iban_last4, iban_hash, status, created_by, updated_by) "
                + "VALUES (?,?,?,?,?,?,'PENDING_VERIFICATION',?,?)", acc, org, "Acme", "ct", "1326", UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID(), UUID.randomUUID());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO partner_payouts (id, organization_id, currency, amount, status, idempotency_key, request_hash, bank_account_id, bank_account_version, iban_ciphertext, iban_last4, account_holder_name, requested_by) "
                + "VALUES (?,?,'TRY',100.00,?,?,?,?,0,'ct','1326','Acme',?)", id, org, status, "idem-" + id, "a".repeat(64), acc, UUID.randomUUID());
        return id;
    }

    private void insertLedger(UUID org, UUID payout, String type, String delta, String account, String debit, String credit) {
        jdbc.update("INSERT INTO payout_ledger (id, group_id, organization_id, payout_id, entry_type, payable_delta, account, debit_amount, credit_amount, currency, created_by) "
                        + "VALUES (?,?,?,?,?,?::numeric,?,?::numeric,?::numeric,'TRY',?)",
                UUID.randomUUID(), UUID.randomUUID(), org, payout, type, delta, account, debit, credit, UUID.randomUUID());
    }

    @Test
    void payoutLedger_rejectsUpdateDeleteTruncate() {
        UUID org = UUID.randomUUID();
        UUID payout = insertPayout(org, "REQUESTED");
        insertLedger(org, payout, "RESERVE", "-100.00", null, "0", "0");

        assertThatThrownBy(() -> jdbc.update("UPDATE payout_ledger SET payable_delta = -1 WHERE organization_id = ?", org))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payout_ledger WHERE organization_id = ?", org))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE payout_ledger"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payout_ledger WHERE organization_id = ?", Integer.class, org)).isEqualTo(1);
    }

    @Test
    void payoutEvents_andBankAccountChanges_rejectUpdateDeleteTruncate() {
        UUID org = UUID.randomUUID();
        UUID payout = insertPayout(org, "REQUESTED");
        jdbc.update("INSERT INTO payout_events (id, payout_id, from_status, to_status, actor_user_id, actor_type) VALUES (?,?,NULL,'REQUESTED',?,'PARTNER')",
                UUID.randomUUID(), payout, UUID.randomUUID());
        UUID account = jdbc.queryForObject("SELECT bank_account_id FROM partner_payouts WHERE id = ?", UUID.class, payout);
        jdbc.update("INSERT INTO partner_bank_account_changes (id, bank_account_id, organization_id, change_type, actor_user_id, actor_type) VALUES (?,?,?,'CREATED',?,'PARTNER')",
                UUID.randomUUID(), account, org, UUID.randomUUID());

        for (String table : new String[]{"payout_events", "partner_bank_account_changes"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE " + table + " SET reason = 'x'")).as(table).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table)).as(table).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE " + table)).as(table).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        }
    }

    @Test
    void shapeCheck_rejectsReserveWithPositiveDelta_andPaidWithNonzeroDelta() {
        UUID org = UUID.randomUUID();
        UUID payout = insertPayout(org, "REQUESTED");

        assertThatThrownBy(() -> insertLedger(org, payout, "RESERVE", "100.00", null, "0", "0"))
                .hasMessageContaining("chk_payout_ledger_shape");
        assertThatThrownBy(() -> insertLedger(org, payout, "RESERVE_RELEASE", "-100.00", null, "0", "0"))
                .hasMessageContaining("chk_payout_ledger_shape");
        assertThatThrownBy(() -> insertLedger(org, payout, "PAID", "-5.00", "SUPPLIER_PAYABLE", "100.00", "0"))
                .hasMessageContaining("chk_payout_ledger_shape");
        assertThatThrownBy(() -> insertLedger(org, payout, "PAID", "0", "SUPPLIER_PAYABLE", "0", "0"))
                .hasMessageContaining("chk_payout_ledger");
        assertThatThrownBy(() -> insertLedger(org, payout, "RESERVE", "-100.00", "SUPPLIER_PAYABLE", "0", "0"))
                .hasMessageContaining("chk_payout_ledger_shape");
        assertThatThrownBy(() -> insertLedger(org, payout, "WITHDRAWAL", "-100.00", null, "0", "0"))
                .hasMessageContaining("chk_payout_ledger");
    }

    @Test
    void ledgerRowsPerPayout_areUnique_perEntryTypeAndAccount_includingTheNullAccountRows() {
        UUID org = UUID.randomUUID();
        UUID payout = insertPayout(org, "REQUESTED");
        insertLedger(org, payout, "RESERVE", "-100.00", null, "0", "0");
        // NULLS NOT DISTINCT: a second RESERVE (account NULL) for the same payout is rejected
        assertThatThrownBy(() -> insertLedger(org, payout, "RESERVE", "-100.00", null, "0", "0")).hasMessageContaining("uq_payout_ledger_once");
        insertLedger(org, payout, "PAID", "0", "SUPPLIER_PAYABLE", "100.00", "0");
        insertLedger(org, payout, "PAID", "0", "SUPPLIER_PAID", "0", "100.00");
        assertThatThrownBy(() -> insertLedger(org, payout, "PAID", "0", "SUPPLIER_PAID", "0", "100.00")).hasMessageContaining("uq_payout_ledger_once");
    }

    @Test
    void secondOpenPayoutRow_forTheSameOrgAndCurrency_isRejectedByTheDatabase() {
        UUID org = UUID.randomUUID();
        insertPayout(org, "REQUESTED");
        assertThatThrownBy(() -> {
            UUID acc = jdbc.queryForObject("SELECT id FROM partner_bank_accounts WHERE organization_id = ?", UUID.class, org);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO partner_payouts (id, organization_id, currency, amount, status, idempotency_key, request_hash, bank_account_id, bank_account_version, iban_ciphertext, iban_last4, account_holder_name, requested_by) "
                    + "VALUES (?,?,'TRY',100.00,'APPROVED',?,?,?,0,'ct','1326','Acme',?)", id, org, "idem2-" + id, "a".repeat(64), acc, UUID.randomUUID());
        }).hasMessageContaining("uq_payout_open");
    }

    @Test
    void sameBankReference_cannotMarkTwoPayoutsPaid_andAmountMustBePositive() {
        UUID org = UUID.randomUUID();
        UUID a = insertPayout(org, "PROCESSING");
        jdbc.update("UPDATE partner_payouts SET status='PAID', payment_reference='SAME-REF', paid_at=now(), paid_by=? WHERE id = ?", UUID.randomUUID(), a);
        UUID org2 = UUID.randomUUID();
        UUID b = insertPayout(org2, "PROCESSING");
        assertThatThrownBy(() -> jdbc.update("UPDATE partner_payouts SET status='PAID', payment_reference='SAME-REF', paid_at=now(), paid_by=? WHERE id = ?", UUID.randomUUID(), b))
                .hasMessageContaining("uq_payout_reference");
        assertThatThrownBy(() -> jdbc.update("UPDATE partner_payouts SET amount = 0 WHERE id = ?", b)).hasMessageContaining("partner_payouts_amount_check");
        assertThatThrownBy(() -> jdbc.update("UPDATE partner_payouts SET status='PAID' WHERE id = ?", b)).hasMessageContaining("chk_payout_paid");
    }

    @Test
    void idempotencyKey_isUniquePerOrganization() {
        UUID org = UUID.randomUUID();
        UUID first = insertPayout(org, "REQUESTED");
        String key = jdbc.queryForObject("SELECT idempotency_key FROM partner_payouts WHERE id = ?", String.class, first);
        jdbc.update("UPDATE partner_payouts SET status = 'CANCELLED', cancelled_at = now(), cancelled_by_type = 'PARTNER' WHERE id = ?", first);
        UUID acc = jdbc.queryForObject("SELECT bank_account_id FROM partner_payouts WHERE id = ?", UUID.class, first);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO partner_payouts (id, organization_id, currency, amount, status, idempotency_key, request_hash, bank_account_id, bank_account_version, iban_ciphertext, iban_last4, account_holder_name, requested_by) "
                + "VALUES (?,?,'TRY',100.00,'REQUESTED',?,?,?,0,'ct','1326','Acme',?)", UUID.randomUUID(), org, key, "a".repeat(64), acc, UUID.randomUUID()))
                .hasMessageContaining("uq_payout_idem");
    }

    @Test
    void repositories_blockDeletes() {
        var ledger = ctxBean(com.waylo.paymentservice.repository.PayoutLedgerRepository.class);
        var events = ctxBean(com.waylo.paymentservice.repository.PayoutEventRepository.class);
        var changes = ctxBean(com.waylo.paymentservice.repository.BankAccountChangeRepository.class);
        assertThatThrownBy(ledger::deleteAll).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ledger.deleteById(UUID.randomUUID())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(events::deleteAll).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(changes::deleteAllInBatch).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void entities_haveNoSetters() {
        for (Class<?> c : new Class<?>[]{com.waylo.paymentservice.domain.PayoutLedgerEntry.class, com.waylo.paymentservice.domain.PayoutEvent.class,
                com.waylo.paymentservice.domain.BankAccountChange.class}) {
            for (var m : c.getDeclaredMethods()) assertThat(m.getName()).as(c.getSimpleName()).doesNotStartWith("set");
        }
    }

    private static <T> T ctxBean(Class<T> type) { return ctx.getBean(type); }
}
