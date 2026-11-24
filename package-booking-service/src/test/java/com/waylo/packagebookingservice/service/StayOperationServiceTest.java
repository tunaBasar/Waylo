package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.AuthAccessClient;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.dto.StayOperationResponse;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.ResourceNotFoundException;
import com.waylo.packagebookingservice.exception.VoucherException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StayOperationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final VoucherRepository vouchers = mock(VoucherRepository.class);
    private final AuthAccessClient auth = mock(AuthAccessClient.class);
    private final StayOperationService service = new StayOperationService(items, vouchers, auth,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID user = UUID.randomUUID();
    private final UUID org = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private Voucher voucher;

    @BeforeEach
    void setUp() {
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setVertical(OrderItemVertical.HOTEL);
        item.setSupplierOrganizationId(org);
        voucher = new Voucher();
        voucher.setId(UUID.randomUUID());
        voucher.setOrderItemId(itemId);
        voucher.setStatus(VoucherStatus.REDEEMED);
        voucher.setRedeemedAt(Instant.parse("2026-11-13T12:00:00Z"));
        ReflectionTestUtils.setField(voucher, "version", 3L);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(vouchers.findByOrderItemId(itemId)).thenReturn(Optional.of(voucher));
        when(vouchers.findByIdForUpdate(voucher.getId())).thenReturn(Optional.of(voucher));
        when(auth.check(user, org, "RESERVATION_REDEEM"))
                .thenReturn(new AuthAccessClient.AccessDecision(true, List.of("HOTEL_STAFF")));
    }

    private void assertConflict(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(VoucherException.class, e -> {
            assertThat(e.getCode()).isEqualTo(code);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        });
    }

    @Test
    void redeemedVoucherCanBeCheckedOutAndDefaultsToNow() {
        StayOperationResponse r = service.checkOut(user, org, itemId, 3L, null);
        assertThat(r.checkedOutAt()).isEqualTo(NOW);
        assertThat(voucher.getCheckedOutAt()).isEqualTo(NOW);
        assertThat(voucher.getCheckedOutByUserId()).isEqualTo(user);
        verify(vouchers).saveAndFlush(voucher);
    }

    @Test
    void checkedOutAtIsStoredAtDatabasePrecision_soReplaysReturnTheSameInstant() {
        // timestamptz keeps microseconds; a nanosecond instant would differ from the value read back on replay
        Instant withNanos = NOW.minusSeconds(60).plusNanos(123_456_789);
        StayOperationResponse r = service.checkOut(user, org, itemId, 3L, withNanos);
        assertThat(r.checkedOutAt()).isEqualTo(NOW.minusSeconds(60).plusNanos(123_456_000));
        assertThat(voucher.getCheckedOutAt().getNano() % 1_000).isZero();
    }

    @Test
    void activeNoShowAndVoidAreInvalidTransitions() {
        for (VoucherStatus s : List.of(VoucherStatus.ACTIVE, VoucherStatus.NO_SHOW, VoucherStatus.VOID)) {
            voucher.setStatus(s);
            assertConflict(() -> service.checkOut(user, org, itemId, 3L, null), "INVALID_STATE_TRANSITION");
        }
        verify(vouchers, never()).saveAndFlush(any());
    }

    @Test
    void secondCheckOutIsIdempotentAndKeepsFirstTimestamp() {
        Instant first = Instant.parse("2026-11-15T08:00:00Z");
        service.checkOut(user, org, itemId, 3L, first);
        StayOperationResponse again = service.checkOut(user, org, itemId, 3L, Instant.parse("2026-11-15T09:00:00Z"));
        assertThat(again.checkedOutAt()).isEqualTo(first);
        assertThat(voucher.getCheckedOutAt()).isEqualTo(first);
        verify(vouchers).saveAndFlush(voucher); // only once
    }

    @Test
    void staleExpectedVersionIsConflict() {
        assertConflict(() -> service.checkOut(user, org, itemId, 2L, null), "VERSION_CONFLICT");
        assertThat(voucher.getCheckedOutAt()).isNull();
    }

    @Test
    void futureCheckedOutAtIsRejected() {
        assertThatThrownBy(() -> service.checkOut(user, org, itemId, 3L, NOW.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkedOutAtBeforeRedemptionIsRejected() {
        assertThatThrownBy(() -> service.checkOut(user, org, itemId, 3L, Instant.parse("2026-11-13T11:59:59Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherOrganizationItemIsNotFoundEvenWithPermissionThere() {
        UUID other = UUID.randomUUID();
        when(auth.check(user, other, "RESERVATION_REDEEM"))
                .thenReturn(new AuthAccessClient.AccessDecision(true, List.of("HOTEL_STAFF")));
        assertThatThrownBy(() -> service.checkOut(user, other, itemId, 3L, null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(voucher.getCheckedOutAt()).isNull();
    }

    @Test
    void callerWithoutPermissionIsForbidden() {
        when(auth.check(user, org, "RESERVATION_REDEEM"))
                .thenReturn(new AuthAccessClient.AccessDecision(false, List.of()));
        assertThatThrownBy(() -> service.checkOut(user, org, itemId, 3L, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonHotelItemIsInvalidTransition() {
        OrderItem car = new OrderItem();
        car.setId(itemId);
        car.setVertical(OrderItemVertical.CAR);
        car.setSupplierOrganizationId(org);
        when(items.findById(itemId)).thenReturn(Optional.of(car));
        assertConflict(() -> service.checkOut(user, org, itemId, 3L, null), "INVALID_STATE_TRANSITION");
    }
}
