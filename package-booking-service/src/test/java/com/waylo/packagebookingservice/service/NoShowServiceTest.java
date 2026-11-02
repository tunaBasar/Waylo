package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.PaymentEscrowClient;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoShowServiceTest {

    static class NoopTx extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object t, org.springframework.transaction.TransactionDefinition d) {}
        @Override protected void doCommit(DefaultTransactionStatus s) {}
        @Override protected void doRollback(DefaultTransactionStatus s) {}
    }

    VoucherRepository vouchers = mock(VoucherRepository.class);
    OrderItemRepository items = mock(OrderItemRepository.class);
    PaymentEscrowClient payment = mock(PaymentEscrowClient.class);
    NoShowService service = new NoShowService(vouchers, items, payment, new NoopTx());

    Voucher voucher(Instant validUntil) {
        Voucher v = new Voucher();
        v.setId(UUID.randomUUID());
        v.setOrderItemId(UUID.randomUUID());
        v.setValidUntil(validUntil);
        v.setStatus(VoucherStatus.ACTIVE);
        return v;
    }

    @Test
    void expiredActiveVoucher_isSettledOnceAndMarkedNoShow() {
        Instant now = Instant.parse("2026-12-05T00:00:00Z");
        Voucher v = voucher(now.minusSeconds(60));
        OrderItem item = new OrderItem();
        item.setId(v.getOrderItemId());
        when(vouchers.findTop200ByStatusAndValidUntilBeforeOrderByValidUntilAsc(VoucherStatus.ACTIVE, now)).thenReturn(List.of(v));
        when(vouchers.findByIdForUpdate(v.getId())).thenReturn(Optional.of(v));
        when(items.findById(item.getId())).thenReturn(Optional.of(item));
        when(payment.noShow(item.getId())).thenReturn(new PaymentEscrowClient.Envelope<>(true,
                new PaymentEscrowClient.CancellationDto(item.getId(), "NO_REFUND", true, null, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO, "TRY", "RELEASED"), null));

        assertThat(service.settleExpired(now)).isEqualTo(1);
        assertThat(v.getStatus()).isEqualTo(VoucherStatus.NO_SHOW);
        assertThat(item.getCancelOutcome()).isEqualTo("NO_SHOW");

        // second run: voucher is no longer ACTIVE, payment is not called again
        when(vouchers.findTop200ByStatusAndValidUntilBeforeOrderByValidUntilAsc(VoucherStatus.ACTIVE, now)).thenReturn(List.of(v));
        assertThat(service.settleExpired(now)).isZero();
        verify(payment, times(1)).noShow(item.getId());
    }

    @Test
    void paymentFailure_leavesVoucherActiveForRetry() {
        Instant now = Instant.parse("2026-12-05T00:00:00Z");
        Voucher v = voucher(now.minusSeconds(60));
        OrderItem item = new OrderItem();
        item.setId(v.getOrderItemId());
        when(vouchers.findTop200ByStatusAndValidUntilBeforeOrderByValidUntilAsc(VoucherStatus.ACTIVE, now)).thenReturn(List.of(v));
        when(vouchers.findByIdForUpdate(v.getId())).thenReturn(Optional.of(v));
        when(items.findById(item.getId())).thenReturn(Optional.of(item));
        when(payment.noShow(any())).thenThrow(new RuntimeException("down"));

        assertThat(service.settleExpired(now)).isZero();
        assertThat(v.getStatus()).isEqualTo(VoucherStatus.ACTIVE);
    }
}
