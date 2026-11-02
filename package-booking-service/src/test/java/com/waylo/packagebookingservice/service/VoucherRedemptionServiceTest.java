package com.waylo.packagebookingservice.service;

import org.mockito.ArgumentCaptor;
import com.waylo.packagebookingservice.client.AuthAccessClient;
import com.waylo.packagebookingservice.client.PaymentEscrowClient;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.PaymentUnavailableException;
import com.waylo.packagebookingservice.exception.VoucherException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit level. The pessimistic row lock is simulated by a ReentrantLock taken in findByIdForUpdate and released when the
 * fake transaction manager commits/rolls back (rollback also restores the voucher snapshot). This proves the service
 * logic is correct IF the DB serializes on the row lock; the real SELECT ... FOR UPDATE is not exercised because the
 * module has no DB test setup (no Testcontainers/H2). Needs live verification.
 */
class VoucherRedemptionServiceTest {

    static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    VoucherRepository voucherRepo = mock(VoucherRepository.class);
    OrderItemRepository itemRepo = mock(OrderItemRepository.class);
    PaymentEscrowClient payment = mock(PaymentEscrowClient.class);
    AuthAccessClient auth = mock(AuthAccessClient.class);
    RedemptionAttemptLimiter limiter = mock(RedemptionAttemptLimiter.class);
    com.waylo.packagebookingservice.repository.OrderRepository orderRepo = mock(com.waylo.packagebookingservice.repository.OrderRepository.class);
    com.waylo.packagebookingservice.event.OrderEventPublisher publisher = mock(com.waylo.packagebookingservice.event.OrderEventPublisher.class);
    VoucherService vouchers;
    VoucherRedemptionService service;

    final ReentrantLock rowLock = new ReentrantLock();
    final ThreadLocal<Object[]> snapshot = new ThreadLocal<>();
    Voucher voucher;
    OrderItem item;
    UUID user = UUID.randomUUID();
    UUID org = UUID.randomUUID();
    String payload;

    PlatformTransactionManager txm = new PlatformTransactionManager() {
        public TransactionStatus getTransaction(TransactionDefinition d) { return new SimpleTransactionStatus(); }
        public void commit(TransactionStatus s) { finish(false); }
        public void rollback(TransactionStatus s) { finish(true); }
        void finish(boolean rollback) {
            Object[] snap = snapshot.get();
            if (rollback && snap != null) {
                voucher.setStatus((VoucherStatus) snap[0]);
                voucher.setRedeemedAt((Instant) snap[1]);
            }
            snapshot.remove();
            if (rowLock.isHeldByCurrentThread()) rowLock.unlock();
        }
    };

    @BeforeEach
    void setUp() {
        vouchers = new VoucherService(voucherRepo, itemRepo, SECRET);
        service = new VoucherRedemptionService(voucherRepo, itemRepo, payment, auth, limiter, txm, orderRepo, publisher);
        item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setVertical(OrderItemVertical.HOTEL);
        item.setSupplierOrganizationId(org);
        item.setDescription("Deluxe");
        item.setUnitAmount(new BigDecimal("100.00"));
        item.setQuantity(1);
        item.setCurrency("USD");
        voucher = new Voucher();
        voucher.setId(UUID.randomUUID());
        voucher.setOrderItemId(item.getId());
        voucher.setCodeHash(VoucherService.hash(vouchers.deriveCode(voucher.getId())));
        voucher.setValidFrom(Instant.now().minus(Duration.ofHours(1)));
        voucher.setValidUntil(Instant.now().plus(Duration.ofHours(1)));
        payload = vouchers.payload(voucher.getId());

        when(voucherRepo.findByIdForUpdate(voucher.getId())).thenAnswer(inv -> {
            rowLock.lock();
            snapshot.set(new Object[] {voucher.getStatus(), voucher.getRedeemedAt()});
            return Optional.of(voucher);
        });
        when(voucherRepo.saveAndFlush(any(Voucher.class))).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepo.findById(item.getId())).thenReturn(Optional.of(item));
        when(auth.check(any(), any(), any())).thenReturn(new AuthAccessClient.AccessDecision(true, List.of("HOTEL_STAFF")));
        when(limiter.isBlocked(any())).thenReturn(false);
        when(payment.release(item.getId())).thenReturn(new PaymentEscrowClient.Envelope<>(true,
                new PaymentEscrowClient.EscrowDto(item.getId(), new BigDecimal("90.00"), "USD", "RELEASED"), null));
    }

    private void assertCode(String code, HttpStatus status) {
        assertThatThrownBy(() -> service.redeem(user, org, payload)).isInstanceOfSatisfying(VoucherException.class, e -> {
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(status);
        });
    }

    @Test
    void happyPath_releasesOnce_andReturnsSupplierAmount() {
        var res = service.redeem(user, org, payload);
        assertThat(res.amountReleased()).isEqualByComparingTo("90.00");
        assertThat(res.description()).isEqualTo("Deluxe");
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.REDEEMED);
        assertThat(voucher.getRedeemedByUserId()).isEqualTo(user);
        verify(payment, times(1)).release(item.getId());
        verify(auth).check(user, org, "RESERVATION_REDEEM");
    }

    @Test
    void redeem_publishesOrderItemRedeemed_afterCommit_andPublishFailureDoesNotFailRedeem() {
        UUID orderId = UUID.randomUUID(), customer = UUID.randomUUID();
        item.setOrderId(orderId);
        var order = new com.waylo.packagebookingservice.domain.Order();
        order.setId(orderId);
        order.setCustomerId(customer);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        service.redeem(user, org, payload);
        var cap = ArgumentCaptor.forClass(com.waylo.packagebookingservice.event.OrderItemRedeemedEvent.class);
        verify(publisher).publishOrderItemRedeemed(cap.capture());
        assertThat(cap.getValue().getCustomerId()).isEqualTo(customer);
        assertThat(cap.getValue().getOrderItemId()).isEqualTo(item.getId());
        assertThat(cap.getValue().getAmount()).isEqualByComparingTo("100.00");
        assertThat(cap.getValue().getEventType()).isEqualTo("ORDER_ITEM_REDEEMED");
        doThrow(new RuntimeException("kafka down")).when(publisher).publishOrderItemRedeemed(any());
        voucher.setStatus(VoucherStatus.ACTIVE);
        assertThat(service.redeem(user, org, payload).amountReleased()).isEqualByComparingTo("90.00");
    }

    @Test
    void secondRedeem_isAlreadyRedeemed409_withRedeemedAt_noSecondRelease() {
        service.redeem(user, org, payload);
        assertThatThrownBy(() -> service.redeem(user, org, payload)).isInstanceOfSatisfying(VoucherException.class, e -> {
            assertThat(e.getCode()).isEqualTo("ALREADY_REDEEMED");
            assertThat(e.getStatus().value()).isEqualTo(409);
            assertThat(e.getRedeemedAt()).isEqualTo(voucher.getRedeemedAt());
        });
        verify(payment, times(1)).release(any());
    }

    @Test
    void concurrentDoubleRedeem_exactlyOneSucceeds() throws Exception {
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    service.redeem(user, org, payload);
                    return true;
                } catch (VoucherException e) {
                    assertThat(e.getCode()).isEqualTo("ALREADY_REDEEMED");
                    return false;
                }
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Boolean> f : futures) if (f.get(10, TimeUnit.SECONDS)) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        verify(payment, times(1)).release(item.getId());
    }

    @Test
    void wrongOrg_wrongSupplier_noRelease_andCountsAsFailure() {
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service.redeem(user, other, payload)).isInstanceOfSatisfying(VoucherException.class,
                e -> assertThat(e.getCode()).isEqualTo("WRONG_SUPPLIER"));
        verify(payment, never()).release(any());
        verify(limiter).recordFailure(other);
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.ACTIVE);
    }

    @Test
    void alreadyRedeemed_byOtherOrg_doesNotRevealRedeemedAt() {
        voucher.setStatus(VoucherStatus.REDEEMED);
        voucher.setRedeemedAt(Instant.now());
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service.redeem(user, other, payload)).isInstanceOfSatisfying(VoucherException.class, e -> {
            assertThat(e.getCode()).isEqualTo("WRONG_SUPPLIER");
            assertThat(e.getRedeemedAt()).isNull();
        });
    }

    @Test
    void expired() {
        voucher.setValidUntil(Instant.now().minusSeconds(5));
        assertCode("EXPIRED", HttpStatus.UNPROCESSABLE_ENTITY);
        verify(payment, never()).release(any());
    }

    @Test
    void notYetValid() {
        voucher.setValidFrom(Instant.now().plusSeconds(3600));
        voucher.setValidUntil(Instant.now().plusSeconds(7200));
        assertCode("NOT_YET_VALID", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void voidVoucher() {
        voucher.setStatus(VoucherStatus.VOID);
        assertCode("VOID", HttpStatus.UNPROCESSABLE_ENTITY);
        verify(payment, never()).release(any());
    }

    @Test
    void releaseFailure_rollsBackToActive_andPropagates503() {
        when(payment.release(item.getId())).thenThrow(new PaymentUnavailableException("down"));
        assertThatThrownBy(() -> service.redeem(user, org, payload)).isInstanceOf(PaymentUnavailableException.class);
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.ACTIVE);
        assertThat(voucher.getRedeemedAt()).isNull();
        verify(limiter, never()).recordFailure(any());
        // retry works after payment recovers
        doReturn(new PaymentEscrowClient.Envelope<>(true,
                new PaymentEscrowClient.EscrowDto(item.getId(), new BigDecimal("90.00"), "USD", "RELEASED"), null))
                .when(payment).release(item.getId());
        assertThat(service.redeem(user, org, payload).voucherId()).isEqualTo(voucher.getId());
    }

    @Test
    void invalidPayloads_andWrongCode() {
        for (String bad : new String[] {"garbage", "WAYLO2:" + voucher.getId() + ":AAAAAAAAAAAAAAAAAAAAAA",
                "WAYLO1:" + voucher.getId() + ":AAAAAAAAAAAAAAAAAAAAAA", // right shape, wrong code
                "WAYLO1:" + UUID.randomUUID() + ":AAAAAAAAAAAAAAAAAAAAAA", ""}) {
            assertThatThrownBy(() -> service.redeem(user, org, bad)).isInstanceOfSatisfying(VoucherException.class,
                    e -> assertThat(e.getCode()).isEqualTo("INVALID_VOUCHER"));
        }
        verify(payment, never()).release(any());
    }

    @Test
    void noRedeemPermission_forbidden_beforeAnyVoucherLookup() {
        when(auth.check(user, org, "RESERVATION_REDEEM")).thenReturn(new AuthAccessClient.AccessDecision(false, List.of()));
        assertThatThrownBy(() -> service.redeem(user, org, payload)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(voucherRepo, payment);
        verify(limiter, never()).recordFailure(any());
    }

    @Test
    void attemptLimit_blocksWith429_beforeLookup() {
        when(limiter.isBlocked(org)).thenReturn(true);
        assertCode("TOO_MANY_ATTEMPTS", HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(voucherRepo, payment);
    }
}
