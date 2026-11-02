package com.waylo.paymentservice.service;

import com.waylo.paymentservice.domain.SupplierAccount;
import com.waylo.paymentservice.domain.SupplierLedgerEntry;
import com.waylo.paymentservice.exception.BusinessRuleException;
import com.waylo.paymentservice.exception.ResourceNotFoundException;
import com.waylo.paymentservice.repository.SupplierAccountRepository;
import com.waylo.paymentservice.repository.SupplierLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SupplierBalanceServiceTest {

    SupplierAccountRepository accounts = mock(SupplierAccountRepository.class);
    SupplierLedgerRepository ledger = mock(SupplierLedgerRepository.class);
    SupplierBalanceService service;
    SupplierAccount acc;
    final UUID order = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SupplierBalanceService(accounts, ledger);
        acc = new SupplierAccount("DUFFEL", "EUR");
        acc.apply(new BigDecimal("100.00"));
        when(accounts.findForUpdate("DUFFEL")).thenReturn(Optional.of(acc));
        when(accounts.findById("DUFFEL")).thenReturn(Optional.of(acc));
        when(accounts.saveAndFlush(any(SupplierAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(ledger.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void topUpAddsAndWritesLedger() {
        service.topUp("duffel", new BigDecimal("50"), "EUR", "wire 1", "k1", UUID.randomUUID());
        assertThat(acc.getBalance()).isEqualByComparingTo("150.00");
        verify(ledger).saveAndFlush(argThat((SupplierLedgerEntry e) ->
                e.getEntryType() == SupplierLedgerEntry.Type.TOPUP && e.getBalanceAfter().compareTo(new BigDecimal("150")) == 0));
    }

    @Test
    void payoutDebitsNetFare() {
        service.payout("DUFFEL", order, UUID.randomUUID(), new BigDecimal("40.00"), "EUR", "p1");
        assertThat(acc.getBalance()).isEqualByComparingTo("60.00");
        verify(ledger).saveAndFlush(argThat((SupplierLedgerEntry e) -> e.getAmount().compareTo(new BigDecimal("-40")) == 0));
    }

    @Test
    void insufficientBalanceWritesNothing() {
        assertThatThrownBy(() -> service.payout("DUFFEL", order, null, new BigDecimal("100.01"), "EUR", "p2"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getRuleCode()).isEqualTo("SUPPLIER_BALANCE_INSUFFICIENT");
        assertThat(acc.getBalance()).isEqualByComparingTo("100.00");
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void exactBalanceIsAllowed() {
        service.payout("DUFFEL", order, null, new BigDecimal("100.00"), "EUR", "p3");
        assertThat(acc.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void payoutReplayDoesNotDebitTwice() {
        when(ledger.findByIdempotencyKey("p4")).thenReturn(Optional.of(mock(SupplierLedgerEntry.class)));
        service.payout("DUFFEL", order, null, new BigDecimal("10.00"), "EUR", "p4");
        assertThat(acc.getBalance()).isEqualByComparingTo("100.00");
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void payoutWithoutAccountIs404() {
        when(accounts.findForUpdate("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.payout("NOPE", order, null, BigDecimal.ONE, "EUR", "p5"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void currencyMismatchRejected() {
        assertThatThrownBy(() -> service.payout("DUFFEL", order, null, BigDecimal.ONE, "TRY", "p6"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void creditAddsBack() {
        service.credit("DUFFEL", order, null, new BigDecimal("25.00"), "EUR", "c1");
        assertThat(acc.getBalance()).isEqualByComparingTo("125.00");
    }

    @Test
    void internalSupplierHasNoAccount() {
        assertThatThrownBy(() -> service.topUp("INTERNAL", BigDecimal.TEN, "TRY", "x", "k", null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
