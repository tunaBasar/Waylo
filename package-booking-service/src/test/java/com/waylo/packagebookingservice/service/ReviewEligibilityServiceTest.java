package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.dto.ReviewEligibilityResponse;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewEligibilityServiceTest {

    // 2026-11-14 22:30 UTC is already 2026-11-15 01:30 in Istanbul: checkOut 11-15 must count as finished.
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T22:30:00Z"), ZoneId.of("Europe/Istanbul"));
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final VoucherRepository vouchers = mock(VoucherRepository.class);
    private final ReviewEligibilityService service = new ReviewEligibilityService(items, orders, vouchers, clock);

    private final UUID customer = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private OrderItem item;
    private Order order;
    private Voucher voucher;

    @BeforeEach
    void setUp() {
        item = new OrderItem();
        item.setId(itemId);
        item.setOrderId(orderId);
        item.setVertical(OrderItemVertical.HOTEL);
        item.setCheckIn(LocalDate.of(2026, 11, 13));
        item.setCheckOut(LocalDate.of(2026, 11, 15));
        order = new Order();
        order.setId(orderId);
        order.setCustomerId(customer);
        order.setStatus(OrderStatus.CONFIRMED);
        voucher = new Voucher();
        voucher.setStatus(VoucherStatus.REDEEMED);
        when(items.findById(itemId)).thenReturn(Optional.of(item));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(vouchers.findByOrderItemId(itemId)).thenReturn(Optional.of(voucher));
    }

    private ReviewEligibilityResponse check(UUID who) {
        return service.check(itemId, who);
    }

    @Test
    void redeemedFinishedStayOfOwnerIsEligible() {
        assertThat(check(customer).eligible()).isTrue();
    }

    @Test
    void checkOutUsesIstanbulCalendarNotUtc() {
        item.setCheckOut(LocalDate.of(2026, 11, 15));
        assertThat(check(customer).eligible()).isTrue();
        item.setCheckOut(LocalDate.of(2026, 11, 16));
        assertThat(check(customer).reason()).isEqualTo("STAY_NOT_FINISHED");
    }

    @Test
    void otherUserIsRejected() {
        assertThat(check(UUID.randomUUID()).reason()).isEqualTo("NOT_OWNER");
    }

    @Test
    void nonHotelItemIsRejected() {
        item.setVertical(OrderItemVertical.CAR);
        assertThat(check(customer).reason()).isEqualTo("NOT_HOTEL_ITEM");
    }

    @Test
    void activeVoucherMeansNotCheckedIn() {
        voucher.setStatus(VoucherStatus.ACTIVE);
        assertThat(check(customer).reason()).isEqualTo("NOT_CHECKED_IN");
    }

    @Test
    void noShowVoucherIsRejected() {
        voucher.setStatus(VoucherStatus.NO_SHOW);
        assertThat(check(customer).eligible()).isFalse();
        voucher.setStatus(VoucherStatus.VOID);
        assertThat(check(customer).eligible()).isFalse();
    }

    @Test
    void cancelOutcomeIsRejected() {
        item.setCancelOutcome("NO_SHOW");
        assertThat(check(customer).reason()).isEqualTo("CANCELLED_OR_NO_SHOW");
    }

    @Test
    void unconfirmedOrderIsRejected() {
        order.setStatus(OrderStatus.CANCELLED);
        assertThat(check(customer).reason()).isEqualTo("ORDER_NOT_CONFIRMED");
    }

    @Test
    void missingVoucherIsRejected() {
        when(vouchers.findByOrderItemId(itemId)).thenReturn(Optional.empty());
        assertThat(check(customer).reason()).isEqualTo("NOT_CHECKED_IN");
    }
}
