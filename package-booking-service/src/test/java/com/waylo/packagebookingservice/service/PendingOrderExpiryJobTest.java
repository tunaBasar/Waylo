package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.BusHoldClient;
import com.waylo.packagebookingservice.client.CarHoldClient;
import com.waylo.packagebookingservice.client.FlightHoldClient;
import com.waylo.packagebookingservice.client.HotelHoldClient;
import com.waylo.packagebookingservice.client.PaymentEscrowClient;
import com.waylo.packagebookingservice.client.PricingClient;
import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.event.OrderEventPublisher;
import com.waylo.packagebookingservice.event.OrderExpiredEvent;
import com.waylo.packagebookingservice.exception.GlobalExceptionHandler;
import com.waylo.packagebookingservice.exception.OrderExpiredException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** RM-27 A: PendingOrderExpiryJob (candidate selection) and OrderServiceImpl.expireIfStale / markPaid on EXPIRED. */
@ExtendWith(MockitoExtension.class)
class PendingOrderExpiryJobTest {

    static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    static final Duration GRACE = Duration.ofMinutes(2);
    static final Instant CUTOFF = NOW.minus(GRACE);

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderEventPublisher eventPublisher;
    @Mock HotelHoldClient hotelHoldClient;
    @Mock CarHoldClient carHoldClient;
    @Mock BusHoldClient busHoldClient;
    @Mock FlightHoldClient flightHoldClient;
    @Mock PaymentEscrowClient paymentEscrowClient;
    @Mock VoucherService voucherService;
    @Mock PricingClient pricingClient;

    OrderServiceImpl service;
    PendingOrderExpiryJob job;
    final UUID orderId = UUID.randomUUID();
    final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, orderItemRepository, eventPublisher, hotelHoldClient, carHoldClient,
                busHoldClient, flightHoldClient, UUID.randomUUID().toString(), paymentEscrowClient, voucherService,
                new PricingApplier(pricingClient), new CancellationPolicyResolver("", "", "NON_REFUNDABLE", "", "FREE_UNTIL", "24"));
        job = new PendingOrderExpiryJob(orderRepository, service, Clock.fixed(NOW, ZoneOffset.UTC), 100, 120);
    }

    Order order(OrderStatus status, Instant holdExpiresAt) {
        Order o = new Order();
        o.setId(orderId);
        o.setCustomerId(customerId);
        o.setCurrency("TRY");
        o.setTotalAmount(new BigDecimal("500.00"));
        o.setStatus(status);
        o.setHoldExpiresAt(holdExpiresAt);
        return o;
    }

    OrderItem item(OrderItemVertical v, UUID hold) {
        OrderItem i = new OrderItem();
        i.setId(UUID.randomUUID());
        i.setVertical(v);
        i.setFulfillmentRef(hold);
        return i;
    }

    void given(Order o, OrderItem... items) {
        lenient().when(orderRepository.findByIdForUpdateSkipLocked(orderId)).thenReturn(Optional.of(o));
        lenient().when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(items));
        lenient().when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void job_usesClockMinusGraceAsCutoff_andExpiresEachCandidate() {
        when(orderRepository.findExpiredPendingIds(eq(CUTOFF), any())).thenReturn(List.of(orderId));
        given(order(OrderStatus.PENDING, CUTOFF.minusSeconds(1)), item(OrderItemVertical.HOTEL, UUID.randomUUID()));
        assertThat(job.expireStale()).isEqualTo(1);
        verify(orderRepository).findExpiredPendingIds(eq(CUTOFF), any());
    }

    @Test
    void expire_releasesAllHolds_pricing_setsExpired_andWritesOutboxEvent() {
        UUID hotelHold = UUID.randomUUID(), carHold = UUID.randomUUID(), flightHold = UUID.randomUUID(), busHold = UUID.randomUUID();
        Order o = order(OrderStatus.PENDING, CUTOFF.minusSeconds(60));
        given(o, item(OrderItemVertical.HOTEL, hotelHold), item(OrderItemVertical.CAR, carHold),
                item(OrderItemVertical.FLIGHT, flightHold), item(OrderItemVertical.BUS, busHold));

        assertThat(service.expireIfStale(orderId, CUTOFF)).isTrue();

        assertThat(o.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(hotelHoldClient).release(hotelHold);
        verify(carHoldClient).release(carHold);
        verify(flightHoldClient).release(flightHold);
        verify(busHoldClient).release(busHold);
        verify(pricingClient).release(orderId);
        var ev = org.mockito.ArgumentCaptor.forClass(OrderExpiredEvent.class);
        verify(eventPublisher).publishOrderExpired(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo("ORDER_EXPIRED");
        assertThat(ev.getValue().getOrderId()).isEqualTo(orderId);
    }

    @Test
    void expire_holdReleaseFailureDoesNotBlockExpiry() {
        UUID hold = UUID.randomUUID();
        Order o = order(OrderStatus.PENDING, CUTOFF.minusSeconds(60));
        given(o, item(OrderItemVertical.HOTEL, hold));
        doThrow(new RuntimeException("hotel down")).when(hotelHoldClient).release(hold);
        assertThat(service.expireIfStale(orderId, CUTOFF)).isTrue();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    void expire_isIdempotent_secondRunChangesNothing() {
        Order o = order(OrderStatus.PENDING, CUTOFF.minusSeconds(60));
        given(o, item(OrderItemVertical.HOTEL, UUID.randomUUID()));
        assertThat(service.expireIfStale(orderId, CUTOFF)).isTrue();
        assertThat(service.expireIfStale(orderId, CUTOFF)).isFalse();
        verify(eventPublisher, times(1)).publishOrderExpired(any());
        verify(hotelHoldClient, times(1)).release(any());
        verify(pricingClient, times(1)).release(any());
    }

    @Test
    void expire_withinGraceOrNoHoldExpiry_isNotTouched() {
        Order o = order(OrderStatus.PENDING, CUTOFF.plusSeconds(1));
        given(o, item(OrderItemVertical.HOTEL, UUID.randomUUID()));
        assertThat(service.expireIfStale(orderId, CUTOFF)).isFalse();
        o.setHoldExpiresAt(null);
        assertThat(service.expireIfStale(orderId, CUTOFF)).isFalse();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(hotelHoldClient, pricingClient, eventPublisher);
    }

    @Test
    void expire_neverTouchesConfirmedCancelledFailedOrders() {
        for (OrderStatus s : new OrderStatus[]{OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED}) {
            Order o = order(s, CUTOFF.minusSeconds(3600));
            given(o, item(OrderItemVertical.HOTEL, UUID.randomUUID()));
            assertThat(service.expireIfStale(orderId, CUTOFF)).isFalse();
            assertThat(o.getStatus()).isEqualTo(s);
        }
        verifyNoInteractions(hotelHoldClient, carHoldClient, busHoldClient, flightHoldClient, pricingClient, eventPublisher);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void expire_lockedOrMissingRow_isSkipped() {
        when(orderRepository.findByIdForUpdateSkipLocked(orderId)).thenReturn(Optional.empty());
        assertThat(service.expireIfStale(orderId, CUTOFF)).isFalse();
        verifyNoInteractions(eventPublisher, pricingClient);
    }

    @Test
    void job_oneFailingOrderDoesNotStopTheBatch() {
        UUID other = UUID.randomUUID();
        OrderService mocked = mock(OrderService.class);
        PendingOrderExpiryJob j = new PendingOrderExpiryJob(orderRepository, mocked, Clock.fixed(NOW, ZoneOffset.UTC), 100, 120);
        when(orderRepository.findExpiredPendingIds(eq(CUTOFF), any())).thenReturn(List.of(other, orderId));
        when(mocked.expireIfStale(other, CUTOFF)).thenThrow(new RuntimeException("boom"));
        when(mocked.expireIfStale(orderId, CUTOFF)).thenReturn(true);
        assertThat(j.expireStale()).isEqualTo(1);
    }

    @Test
    void markPaid_onExpiredOrder_throwsOrderExpired_andMapsTo409() {
        Order o = order(OrderStatus.EXPIRED, CUTOFF.minusSeconds(60));
        given(o, item(OrderItemVertical.HOTEL, UUID.randomUUID()));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.markPaid(orderId)).isInstanceOf(OrderExpiredException.class);
        verifyNoInteractions(hotelHoldClient, voucherService, eventPublisher);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        var response = new GlobalExceptionHandler().handleOrderExpired(new OrderExpiredException("x"),
                new MockHttpServletRequest("POST", "/internal/orders/" + orderId + "/paid"));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error()).isEqualTo("ORDER_EXPIRED");
    }
}
