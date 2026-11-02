package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.CarHoldClient;
import com.waylo.packagebookingservice.client.FlightHoldClient;
import com.waylo.packagebookingservice.client.HotelHoldClient;
import com.waylo.packagebookingservice.client.PaymentEscrowClient;
import com.waylo.packagebookingservice.client.PricingClient;
import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.PackageOrderCreateRequest;
import com.waylo.packagebookingservice.event.OrderEventPublisher;
import com.waylo.packagebookingservice.exception.CarRejectedException;
import com.waylo.packagebookingservice.exception.HotelRejectedException;
import com.waylo.packagebookingservice.exception.PackageItemFailedException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** PACKAGE-ORDER-CONTRACT.md: multi-item order create, compensation, confirm failure, promo, cancellation preview. */
@ExtendWith(MockitoExtension.class)
class PackageOrderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID SELLER = UUID.randomUUID();

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderEventPublisher eventPublisher;
    @Mock HotelHoldClient hotelHoldClient;
    @Mock CarHoldClient carHoldClient;
    @Mock com.waylo.packagebookingservice.client.BusHoldClient busHoldClient;
    @Mock FlightHoldClient flightHoldClient;
    @Mock PaymentEscrowClient paymentEscrowClient;
    @Mock VoucherService voucherService;
    @Mock PricingClient pricingClient;

    private OrderServiceImpl service;
    private final UUID customer = UUID.randomUUID();
    private final Instant hotelExpiry = Instant.now().plusSeconds(900);
    private final Instant carExpiry = Instant.now().plusSeconds(600);

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, orderItemRepository, eventPublisher, hotelHoldClient, carHoldClient,
                busHoldClient, flightHoldClient, SELLER.toString(), paymentEscrowClient, voucherService,
                new PricingApplier(pricingClient), new CancellationPolicyResolver("", "", "", "", "", ""));
    }

    private HotelHoldClient.HoldDto hotelHold(UUID id) {
        return new HotelHoldClient.HoldDto(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2, 1, "HELD", "TRY",
                new BigDecimal("1000.00"), hotelExpiry, "FREE_CANCELLATION");
    }

    private CarHoldClient.HoldDto carHold(UUID id) {
        return new CarHoldClient.HoldDto(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ECONOMY", "AUTOMATIC", "Fiat Egea",
                LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 3, 10, 0), 2, "TRY", new BigDecimal("600.00"),
                BigDecimal.ZERO, 300, "HELD", carExpiry);
    }

    private PackageOrderCreateRequest.Item hotelItem() {
        return new PackageOrderCreateRequest.Item(OrderItemVertical.HOTEL, UUID.randomUUID(), null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private PackageOrderCreateRequest.Item carItem() {
        return new PackageOrderCreateRequest.Item(OrderItemVertical.CAR, null, UUID.randomUUID(), UUID.randomUUID(), "ECONOMY",
                "AUTOMATIC", LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 3, 10, 0), 30,
                null, null, null, null, null, null, null, null);
    }

    private void stubSaves() {
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAndFlush(any(OrderItem.class))).thenAnswer(inv -> {
            OrderItem i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
    }

    @Test
    void allSucceed_oneOrderTwoItemsTotalAndMinTtl() {
        UUID h = UUID.randomUUID(), c = UUID.randomUUID();
        when(hotelHoldClient.hold(any())).thenReturn(new HotelHoldClient.Envelope<>(true, hotelHold(h), null));
        when(carHoldClient.hold(any())).thenReturn(new CarHoldClient.Envelope<>(true, carHold(c), null));
        stubSaves();

        var r = service.createPackageOrder(customer, new PackageOrderCreateRequest(List.of(hotelItem(), carItem()), null, null));

        assertThat(r.items()).hasSize(2);
        assertThat(r.totalAmount()).isEqualByComparingTo("1600.00");
        assertThat(r.holdExpiresAt()).isEqualTo(carExpiry); // min of the hold TTLs
        assertThat(r.items()).extracting(i -> i.vertical()).containsExactly(OrderItemVertical.HOTEL, OrderItemVertical.CAR);
        assertThat(r.items()).extracting(i -> i.fulfillmentRef()).containsExactly(h, c);
        verify(hotelHoldClient, never()).release(any());
        verify(carHoldClient, never()).release(any());
        verify(eventPublisher).publishOrderCreated(any());
    }

    @Test
    void secondHoldFails_firstReleased_noOrder_indexReported() {
        UUID h = UUID.randomUUID();
        when(hotelHoldClient.hold(any())).thenReturn(new HotelHoldClient.Envelope<>(true, hotelHold(h), null));
        when(carHoldClient.hold(any())).thenThrow(new CarRejectedException(409, "VEHICLE_UNAVAILABLE", "none"));

        assertThatThrownBy(() -> service.createPackageOrder(customer, new PackageOrderCreateRequest(List.of(hotelItem(), carItem()), null, null)))
                .isInstanceOfSatisfying(PackageItemFailedException.class, e -> {
                    assertThat(e.getItemIndex()).isEqualTo(1);
                    assertThat(e.getCause()).isInstanceOf(CarRejectedException.class);
                });
        verify(hotelHoldClient).release(h);
        verifyNoInteractions(orderRepository);
    }

    private OrderItem confirmable(UUID orderId, OrderItemVertical v, UUID hold, String net) {
        OrderItem i = new OrderItem();
        i.setId(UUID.randomUUID());
        i.setOrderId(orderId);
        i.setVertical(v);
        i.setFulfillmentRef(hold);
        i.setUnitAmount(new BigDecimal(net));
        i.setNetAmount(new BigDecimal(net));
        i.setQuantity(1);
        i.setCurrency("TRY");
        return i;
    }

    private Order order(UUID id, OrderStatus s) {
        Order o = new Order();
        o.setId(id);
        o.setCustomerId(customer);
        o.setCurrency("TRY");
        o.setStatus(s);
        o.setTotalAmount(new BigDecimal("1600.00"));
        return o;
    }

    @Test
    void confirmFailsAfterPayment_thatItemRefunded_restKept() {
        UUID orderId = UUID.randomUUID();
        OrderItem hotel = confirmable(orderId, OrderItemVertical.HOTEL, UUID.randomUUID(), "1000.00");
        OrderItem car = confirmable(orderId, OrderItemVertical.CAR, UUID.randomUUID(), "600.00");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, OrderStatus.PENDING)));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(hotel, car));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(carHoldClient.confirm(car.getFulfillmentRef())).thenThrow(new CarRejectedException(422, "HOLD_EXPIRED", "x"));

        var r = service.markPaid(orderId);

        assertThat(r.status()).isEqualTo("CONFIRMED");
        assertThat(r.refundItemIds()).containsExactly(car.getId());
        assertThat(car.getCancelOutcome()).isEqualTo("REFUND");
        assertThat(car.getRefundAmount()).isEqualByComparingTo("600.00");
        assertThat(hotel.getCancelOutcome()).isNull();
        verify(hotelHoldClient).confirm(hotel.getFulfillmentRef());
        verify(carHoldClient).release(car.getFulfillmentRef());
        verify(voucherService).createForItems(List.of(hotel)); // no voucher for the refunded item
    }

    @Test
    void allConfirmsFail_wholePaymentRolledBack() {
        UUID orderId = UUID.randomUUID();
        OrderItem hotel = confirmable(orderId, OrderItemVertical.HOTEL, UUID.randomUUID(), "1000.00");
        OrderItem car = confirmable(orderId, OrderItemVertical.CAR, UUID.randomUUID(), "600.00");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, OrderStatus.PENDING)));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(hotel, car));
        when(hotelHoldClient.confirm(any())).thenThrow(new HotelRejectedException(422, "HOLD_EXPIRED", "x"));
        when(carHoldClient.confirm(any())).thenThrow(new CarRejectedException(422, "HOLD_EXPIRED", "x"));

        assertThatThrownBy(() -> service.markPaid(orderId)).isInstanceOf(HotelRejectedException.class);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void promo_appliesOncePerOrder_onFirstItemThatAcceptsIt() throws Exception {
        when(hotelHoldClient.hold(any())).thenReturn(new HotelHoldClient.Envelope<>(true, hotelHold(UUID.randomUUID()), null));
        when(carHoldClient.hold(any())).thenReturn(new CarHoldClient.Envelope<>(true, carHold(UUID.randomUUID()), null));
        // hotel: promo belongs to another vertical -> invalid there; car: accepted with 60 off
        when(pricingClient.quote(argThat(q -> q != null && "OTEL".equals(q.vertical()) && q.promoCode() != null)))
                .thenThrow(new com.waylo.packagebookingservice.exception.BusinessRuleException("PROMO_CODE_INVALID", "wrong vertical"));
        when(pricingClient.quote(argThat(q -> q != null && "OTEL".equals(q.vertical()) && q.promoCode() == null)))
                .thenReturn(JSON.readTree("{\"netAmount\":1000.00,\"discountLines\":[]}"));
        when(pricingClient.quote(argThat(q -> q != null && "ARAÇ".equals(q.vertical()))))
                .thenReturn(JSON.readTree("{\"netAmount\":540.00,\"discountLines\":[{\"amount\":60.00,\"fundedBy\":\"PLATFORM\"}]}"));
        stubSaves();

        var r = service.createPackageOrder(customer, new PackageOrderCreateRequest(List.of(hotelItem(), carItem()), "SUMMER", null));

        assertThat(r.totalAmount()).isEqualByComparingTo("1540.00");
        assertThat(r.items().get(0).discountAmount()).isEqualByComparingTo("0");
        assertThat(r.items().get(1).discountAmount()).isEqualByComparingTo("60.00");
        assertThat(r.items().get(1).grossAmount()).isEqualByComparingTo("600.00"); // commission base stays gross
        assertThat(r.items().get(1).promoCode()).isEqualTo("SUMMER");
    }

    @Test
    void promoInvalidForEveryItem_failsAndReleasesHolds() {
        UUID h = UUID.randomUUID();
        when(hotelHoldClient.hold(any())).thenReturn(new HotelHoldClient.Envelope<>(true, hotelHold(h), null));
        when(pricingClient.quote(any())).thenThrow(new com.waylo.packagebookingservice.exception.BusinessRuleException("PROMO_CODE_INVALID", "no"));

        assertThatThrownBy(() -> service.createPackageOrder(customer,
                new PackageOrderCreateRequest(List.of(hotelItem()), "NOPE", null)))
                .isInstanceOfSatisfying(PackageItemFailedException.class,
                        e -> assertThat(e.getCause()).hasMessage("no"));
        verify(hotelHoldClient).release(h);
    }

    @Test
    void cancellationPreview_listsEveryItemAndSumsRefunds() {
        UUID orderId = UUID.randomUUID();
        OrderItem hotel = confirmable(orderId, OrderItemVertical.HOTEL, UUID.randomUUID(), "1000.00");
        OrderItem car = confirmable(orderId, OrderItemVertical.CAR, UUID.randomUUID(), "600.00");
        hotel.setDescription("hotel");
        car.setDescription("car");
        hotel.setCancellationPolicy("FREE_CANCELLATION");
        car.setCancellationPolicy("NON_REFUNDABLE");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order(orderId, OrderStatus.CONFIRMED)));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(hotel, car));
        when(paymentEscrowClient.preview(eq(hotel.getId()), any())).thenReturn(env(cancel(hotel.getId(), "REFUND", "1000.00")));
        when(paymentEscrowClient.preview(eq(car.getId()), any())).thenReturn(env(cancel(car.getId(), "NO_REFUND", "0.00")));

        var p = service.cancellationPreview(customer, orderId);

        assertThat(p.cancellable()).isTrue();
        assertThat(p.items()).hasSize(2);
        assertThat(p.items().get(0).outcome()).isEqualTo("REFUND");
        assertThat(p.items().get(1).outcome()).isEqualTo("NO_REFUND");
        assertThat(p.totalRefund()).isEqualByComparingTo("1000.00");
    }

    @Test
    void adminPaymentState() {
        OrderItem a = confirmable(UUID.randomUUID(), OrderItemVertical.CAR, null, "10.00");
        OrderItem b = confirmable(UUID.randomUUID(), OrderItemVertical.HOTEL, null, "10.00");
        assertThat(AdminOrderService.paymentState(OrderStatus.PENDING, List.of(a, b))).isEqualTo("UNPAID");
        assertThat(AdminOrderService.paymentState(OrderStatus.CONFIRMED, List.of(a, b))).isEqualTo("PAID");
        a.setCancelOutcome("REFUND");
        assertThat(AdminOrderService.paymentState(OrderStatus.CONFIRMED, List.of(a, b))).isEqualTo("PARTIALLY_REFUNDED");
        b.setCancelOutcome("REFUND");
        assertThat(AdminOrderService.paymentState(OrderStatus.CANCELLED, new ArrayList<>(List.of(a, b)))).isEqualTo("REFUNDED");
    }

    private static PaymentEscrowClient.CancellationDto cancel(UUID id, String outcome, String refund) {
        return new PaymentEscrowClient.CancellationDto(id, outcome, true, null, new BigDecimal("1.00"), new BigDecimal(refund),
                BigDecimal.ZERO, BigDecimal.ZERO, "TRY", "HELD");
    }

    private static PaymentEscrowClient.Envelope<PaymentEscrowClient.CancellationDto> env(PaymentEscrowClient.CancellationDto d) {
        return new PaymentEscrowClient.Envelope<>(true, d, null);
    }
}
