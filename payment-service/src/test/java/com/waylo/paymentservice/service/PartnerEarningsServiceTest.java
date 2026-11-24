package com.waylo.paymentservice.service;

import com.waylo.paymentservice.client.AuthAccessClient;
import com.waylo.paymentservice.domain.EscrowItem;
import com.waylo.paymentservice.domain.EscrowStatus;
import com.waylo.paymentservice.domain.Vertical;
import com.waylo.paymentservice.exception.AccessDeniedException;
import com.waylo.paymentservice.repository.EscrowItemRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PartnerEarningsServiceTest {

    EscrowItemRepository repo = mock(EscrowItemRepository.class);
    AuthAccessClient auth = mock(AuthAccessClient.class);
    PartnerEarningsService service = new PartnerEarningsService(repo, auth);
    UUID user = UUID.randomUUID();
    UUID org = UUID.randomUUID();

    private EscrowItem item(EscrowStatus status, String supplier) {
        EscrowItem e = new EscrowItem();
        e.setOrderItemId(UUID.randomUUID());
        e.setVertical(Vertical.CAR);
        e.setGrossAmount(new BigDecimal("100.00"));
        e.setCommissionAmount(new BigDecimal("10.00"));
        e.setSupplierAmount(new BigDecimal(supplier));
        e.setCurrency("TRY");
        e.setStatus(status);
        return e;
    }

    @Test
    void member_getsRowsAndTotalsPerState() {
        when(auth.check(user, org, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(true, List.of("ORGANIZATION_OWNER")));
        when(repo.findForSupplier(eq(org), any(), any())).thenReturn(List.of(
                item(EscrowStatus.HELD, "90.00"), item(EscrowStatus.HELD, "50.00"),
                item(EscrowStatus.RELEASED, "90.00"), item(EscrowStatus.REFUNDED, "90.00")));

        var r = service.earnings(user, org, null, null);

        assertThat(r.items()).hasSize(4);
        assertThat(r.totals().held()).isEqualByComparingTo("140.00");
        assertThat(r.totals().released()).isEqualByComparingTo("90.00");
        assertThat(r.totals().refunded()).isEqualByComparingTo("90.00");
    }

    @Test
    void nonMember_denied_andNothingQueried() {
        when(auth.check(user, org, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(false, List.of()));
        assertThatThrownBy(() -> service.earnings(user, org, null, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repo);
    }

    @Test
    void twoCurrencies_giveOneTotalsRowEach_andNoSingleCurrencyFields() {
        when(auth.check(user, org, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(true, List.of("ORGANIZATION_OWNER")));
        EscrowItem eur = item(EscrowStatus.RELEASED, "50.00");
        eur.setCurrency("EUR");
        when(repo.findForSupplier(eq(org), any(), any())).thenReturn(List.of(item(EscrowStatus.RELEASED, "90.00"), eur, item(EscrowStatus.HELD, "10.00")));

        var r = service.earnings(user, org, null, null);

        assertThat(r.currency()).isNull();
        assertThat(r.totals()).isNull();
        assertThat(r.totalsByCurrency()).hasSize(2);
        var tryTotals = r.totalsByCurrency().stream().filter(t -> t.currency().equals("TRY")).findFirst().orElseThrow();
        var eurTotals = r.totalsByCurrency().stream().filter(t -> t.currency().equals("EUR")).findFirst().orElseThrow();
        assertThat(tryTotals.released()).isEqualByComparingTo("90.00");
        assertThat(tryTotals.held()).isEqualByComparingTo("10.00");
        assertThat(eurTotals.released()).isEqualByComparingTo("50.00");
    }

    @Test
    void singleCurrency_keepsCurrencyAndTotalsForBackwardCompatibility() {
        when(auth.check(user, org, "RESERVATION_READ")).thenReturn(new AuthAccessClient.AccessDecision(true, List.of()));
        when(repo.findForSupplier(eq(org), any(), any())).thenReturn(List.of(item(EscrowStatus.RELEASED, "90.00")));

        var r = service.earnings(user, org, null, null);

        assertThat(r.currency()).isEqualTo("TRY");
        assertThat(r.totals().released()).isEqualByComparingTo("90.00");
        assertThat(r.totalsByCurrency()).hasSize(1);
    }
}
