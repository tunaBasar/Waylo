package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.OrderCreateRequest;
import com.waylo.packagebookingservice.dto.OrderItemCreateRequest;
import com.waylo.packagebookingservice.dto.OrderResponse;
import com.waylo.packagebookingservice.event.OrderEventPublisher;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.AuthenticationRequiredException;
import com.waylo.packagebookingservice.exception.BusinessRuleException;
import com.waylo.packagebookingservice.exception.ResourceNotFoundException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.waylo.packagebookingservice.client.HotelHoldClient;
import com.waylo.packagebookingservice.dto.HotelOrderCreateRequest;
import com.waylo.packagebookingservice.exception.HotelRejectedException;
import com.waylo.packagebookingservice.exception.HotelUnavailableException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private com.waylo.packagebookingservice.client.HotelHoldClient hotelHoldClient;

    @Mock
    private com.waylo.packagebookingservice.client.CarHoldClient carHoldClient;

    @Mock
    private com.waylo.packagebookingservice.client.BusHoldClient busHoldClient;

    @Mock
    private com.waylo.packagebookingservice.client.FlightHoldClient flightHoldClient;

    private static final UUID SELLER = UUID.randomUUID();

    @Mock
    private com.waylo.packagebookingservice.client.PaymentEscrowClient paymentEscrowClient;

    @Mock
    private VoucherService voucherService;

    @Mock
    private com.waylo.packagebookingservice.client.PricingClient pricingClient;

    private OrderServiceImpl service;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, orderItemRepository, eventPublisher, hotelHoldClient, carHoldClient, busHoldClient, flightHoldClient, SELLER.toString(), paymentEscrowClient, voucherService, new PricingApplier(pricingClient), new CancellationPolicyResolver("", "", "NON_REFUNDABLE", "", "FREE_UNTIL", "24"));
    }

    private OrderItemCreateRequest validItem(String currency) {
        return new OrderItemCreateRequest(
                OrderItemVertical.CAR, UUID.randomUUID(), UUID.randomUUID(),
                "Deluxe room, 2 nights", new BigDecimal("100.00"), 2, currency);
    }

    private Order persistedOrder(UUID id, UUID customer, OrderStatus status, long version) {
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(customer);
        order.setCurrency("USD");
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("200.00"));
        order.setVersion(version);
        return order;
    }

    // --- flight ---

    private com.waylo.packagebookingservice.client.FlightHoldClient.HoldDto flightHold(UUID holdId) {
        return new com.waylo.packagebookingservice.client.FlightHoldClient.HoldDto(holdId, "INTERNAL", "TK100", "THY", "IST", "ESB",
                Instant.parse("2026-11-10T06:30:00Z"), Instant.parse("2026-11-10T07:30:00Z"), "ECONOMY", "12A", "Ali", "Yilmaz",
                "TRY", new BigDecimal("150.00"), BigDecimal.ZERO, "HELD", Instant.now().plusSeconds(900), null, null);
    }

    private com.waylo.packagebookingservice.dto.FlightOrderCreateRequest flightRequest() {
        return new com.waylo.packagebookingservice.dto.FlightOrderCreateRequest("offer", "12A", "Ali", "Yilmaz", null);
    }


    private com.waylo.packagebookingservice.dto.BusOrderCreateRequest busRequest() {
        return new com.waylo.packagebookingservice.dto.BusOrderCreateRequest(UUID.randomUUID(), 0, 2, List.of(
                new com.waylo.packagebookingservice.dto.BusOrderCreateRequest.Passenger(5, "Ali Veli", "MALE", null),
                new com.waylo.packagebookingservice.dto.BusOrderCreateRequest.Passenger(6, "Ayse Veli", "FEMALE", null)), null);
    }

    private com.waylo.packagebookingservice.client.BusHoldClient.HoldDto busHold(UUID holdId) {
        return new com.waylo.packagebookingservice.client.BusHoldClient.HoldDto(holdId, SELLER, UUID.randomUUID(), 0, 2,
                UUID.randomUUID(), UUID.randomUUID(), "Istanbul", "Ankara", "Esenler", "ASTI", "Acme Bus",
                java.time.LocalDateTime.of(2027, 2, 2, 22, 0), java.time.LocalDateTime.of(2027, 2, 3, 5, 0), List.of(5, 6),
                "TRY", new BigDecimal("1500.00"), "HELD", Instant.now().plusSeconds(900));
    }

    @Test
    void createBus_buildsOrderFromHoldOnly() {
        UUID holdId = UUID.randomUUID();
        when(busHoldClient.hold(any())).thenReturn(new com.waylo.packagebookingservice.client.BusHoldClient.Envelope<>(true, busHold(holdId), null));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAndFlush(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.createBusOrder(customerId, busRequest());

        assertThat(response.totalAmount()).isEqualByComparingTo("1500.00");
        var item = response.items().get(0);
        assertThat(item.vertical()).isEqualTo(OrderItemVertical.BUS);
        assertThat(item.supplierOrganizationId()).isEqualTo(SELLER);
        assertThat(item.fulfillmentRef()).isEqualTo(holdId);
        assertThat(item.description()).isEqualTo("Istanbul - Ankara, 02.02.2027 22:00, koltuk 5, 6");
    }

    @Test
    void createBus_releasesHoldWhenOrderSaveFails() {
        UUID holdId = UUID.randomUUID();
        when(busHoldClient.hold(any())).thenReturn(new com.waylo.packagebookingservice.client.BusHoldClient.Envelope<>(true, busHold(holdId), null));
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> service.createBusOrder(customerId, busRequest())).isInstanceOf(IllegalStateException.class);
        verify(busHoldClient).release(holdId);
    }

    @Test
    void createBus_withoutHoldNoOrder() {
        when(busHoldClient.hold(any())).thenThrow(new com.waylo.packagebookingservice.exception.BusRejectedException(409, "SEAT_TAKEN", "x"));
        assertThatThrownBy(() -> service.createBusOrder(customerId, busRequest()))
                .isInstanceOf(com.waylo.packagebookingservice.exception.BusRejectedException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void markPaid_confirmsBusHold() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0);
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setVertical(OrderItemVertical.BUS);
        item.setFulfillmentRef(holdId);
        item.setUnitAmount(new BigDecimal("1500.00"));
        item.setQuantity(1);
        item.setCurrency("TRY");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        service.markPaid(orderId);
        verify(busHoldClient).confirm(holdId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancel_releasesBusHold() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0);
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setVertical(OrderItemVertical.BUS);
        item.setFulfillmentRef(holdId);
        item.setUnitAmount(new BigDecimal("1500.00"));
        item.setQuantity(1);
        item.setCurrency("TRY");
        item.setDescription("x");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        service.cancel(customerId, orderId, null);
        verify(busHoldClient).release(holdId);
    }

    @Test
    void createFlight_buildsOrderFromHoldOnly() {
        UUID holdId = UUID.randomUUID();
        when(flightHoldClient.hold(any())).thenReturn(new com.waylo.packagebookingservice.client.FlightHoldClient.Envelope<>(true, flightHold(holdId), null));
        when(orderItemRepository.findByFulfillmentRef(holdId)).thenReturn(List.of());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAndFlush(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.createFlightOrder(customerId, flightRequest());

        assertThat(response.totalAmount()).isEqualByComparingTo("150.00");
        var item = response.items().get(0);
        assertThat(item.vertical()).isEqualTo(OrderItemVertical.FLIGHT);
        assertThat(item.supplierOrganizationId()).isEqualTo(SELLER);
        assertThat(item.fulfillmentRef()).isEqualTo(holdId);
        assertThat(item.description()).startsWith("TK100 IST-ESB, 10.11.2026 09:30").endsWith("koltuk 12A");
        assertThat(item.flight().passengerLastName()).isEqualTo("Yilmaz");
        assertThat(item.flight().pnr()).isNull();
    }

    @Test
    void createFlight_releasesHoldWhenOrderSaveFails() {
        UUID holdId = UUID.randomUUID();
        when(flightHoldClient.hold(any())).thenReturn(new com.waylo.packagebookingservice.client.FlightHoldClient.Envelope<>(true, flightHold(holdId), null));
        when(orderItemRepository.findByFulfillmentRef(holdId)).thenReturn(List.of());
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> service.createFlightOrder(customerId, flightRequest())).isInstanceOf(IllegalStateException.class);
        verify(flightHoldClient).release(holdId);
    }

    @Test
    void createFlight_withoutHoldNoOrder() {
        when(flightHoldClient.hold(any())).thenThrow(new com.waylo.packagebookingservice.exception.FlightRejectedException(409, "OFFER_EXPIRED", "x"));
        assertThatThrownBy(() -> service.createFlightOrder(customerId, flightRequest()))
                .isInstanceOf(com.waylo.packagebookingservice.exception.FlightRejectedException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void createFlight_noSellerConfigured_422() {
        OrderServiceImpl noSeller = new OrderServiceImpl(orderRepository, orderItemRepository, eventPublisher, hotelHoldClient,
                carHoldClient, busHoldClient, flightHoldClient, "", paymentEscrowClient, voucherService, new PricingApplier(pricingClient), new CancellationPolicyResolver("", "", "NON_REFUNDABLE", "", "FREE_UNTIL", "24"));
        assertThatThrownBy(() -> noSeller.createFlightOrder(customerId, flightRequest()))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(flightHoldClient);
    }

    @Test
    void markPaid_confirmsFlightHoldAndCopiesTicket() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0);
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setVertical(OrderItemVertical.FLIGHT);
        item.setFulfillmentRef(holdId);
        item.setUnitAmount(new BigDecimal("150.00"));
        item.setQuantity(1);
        item.setCurrency("TRY");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        var confirmed = new com.waylo.packagebookingservice.client.FlightHoldClient.HoldDto(holdId, "INTERNAL", "TK100", "THY", "IST", "ESB",
                Instant.parse("2026-11-10T06:30:00Z"), Instant.parse("2026-11-10T07:30:00Z"), "ECONOMY", "12A", "Ali", "Yilmaz",
                "TRY", new BigDecimal("150.00"), BigDecimal.ZERO, "CONFIRMED", Instant.now(), "ABC234", "WL12345678901");
        when(flightHoldClient.confirm(holdId)).thenReturn(new com.waylo.packagebookingservice.client.FlightHoldClient.Envelope<>(true, confirmed, null));

        service.markPaid(orderId);

        assertThat(item.getPnr()).isEqualTo("ABC234");
        assertThat(item.getTicketNumber()).isEqualTo("WL12345678901");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    // --- create ---

    @Test
    void create_noIdentity_throwsAuthenticationRequired() {
        OrderCreateRequest request = new OrderCreateRequest(customerId, "USD", null, null, List.of(validItem("USD")));
        assertThatThrownBy(() -> service.create(null, request))
                .isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void create_customerMismatch_throwsAccessDenied() {
        OrderCreateRequest request = new OrderCreateRequest(UUID.randomUUID(), "USD", null, null, List.of(validItem("USD")));
        assertThatThrownBy(() -> service.create(customerId, request))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void create_itemCurrencyMismatch_throwsBusinessRule422() {
        OrderCreateRequest request = new OrderCreateRequest(customerId, "USD", null, null, List.of(validItem("EUR")));
        assertThatThrownBy(() -> service.create(customerId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                .isEqualTo("CURRENCY_MISMATCH");
        verifyNoInteractions(orderRepository);
    }

    @Test
    void create_valid_savesOrderAndItemComputesTotalAndPublishesEvent() {
        OrderCreateRequest request = new OrderCreateRequest(customerId, "USD", null, "note", List.of(validItem("USD")));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setVersion(0L);
            return o;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = service.create(customerId, request);

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("200.00");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("200.00");

        verify(eventPublisher).publishOrderCreated(any());
    }

    // --- getById ---

    @Test
    void getById_noIdentity_throwsAuthenticationRequired() {
        assertThatThrownBy(() -> service.getById(null, UUID.randomUUID()))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(customerId, orderId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_otherCustomer_throwsAccessDenied() {
        UUID orderId = UUID.randomUUID();
        Order order = persistedOrder(orderId, UUID.randomUUID(), OrderStatus.PENDING, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.getById(customerId, orderId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getById_owner_returnsOrderWithItems() {
        UUID orderId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse response = service.getById(customerId, orderId);
        assertThat(response.id()).isEqualTo(orderId);
    }

    // --- listOwn ---

    @Test
    void listOwn_returnsOnlyOwnOrders() {
        Order order = persistedOrder(UUID.randomUUID(), customerId, OrderStatus.PENDING, 0L);
        when(orderRepository.findByCustomerId(customerId)).thenReturn(List.of(order));
        when(orderItemRepository.findByOrderId(order.getId())).thenReturn(List.of());

        List<OrderResponse> result = service.listOwn(customerId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).customerId()).isEqualTo(customerId);
    }

    // --- cancel ---

    @Test
    void cancel_alreadyCancelled_throwsBusinessRule() {
        UUID orderId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.CANCELLED, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(customerId, orderId, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                .isEqualTo("ALREADY_CANCELLED");
    }

    @Test
    void cancel_failedState_throwsBusinessRuleTerminalState() {
        UUID orderId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.FAILED, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(customerId, orderId, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                .isEqualTo("TERMINAL_STATE");
    }

    @Test
    void cancel_staleExpectedVersion_throwsOptimisticLocking409() {
        UUID orderId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 5L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(customerId, orderId, 4L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancel_otherCustomer_throwsAccessDenied() {
        UUID orderId = UUID.randomUUID();
        Order order = persistedOrder(orderId, UUID.randomUUID(), OrderStatus.PENDING, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(customerId, orderId, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancel_valid_setsStatusCancelledAndPublishesEvent() {
        UUID orderId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 3L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse response = service.cancel(customerId, orderId, 3L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisher).publishOrderCancelled(any());
    }

    // --- hotel orders ---

    private final UUID quoteId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    private HotelHoldClient.HoldDto hold() {
        return new HotelHoldClient.HoldDto(UUID.randomUUID(), quoteId, UUID.randomUUID(), orgId, UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), 2, 1, "HELD",
                "EUR", new BigDecimal("321.50"), Instant.parse("2026-12-01T10:00:00Z"), "FREE_CANCELLATION");
    }

    private void stubSaves() {
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setVersion(0L);
            return o;
        });
        when(orderItemRepository.saveAndFlush(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createHotelOrder_buildsEverythingFromHold() {
        HotelHoldClient.HoldDto hold = hold();
        when(orderRepository.findByCustomerIdAndQuoteId(customerId, quoteId)).thenReturn(List.of());
        when(hotelHoldClient.hold(any())).thenReturn(new HotelHoldClient.Envelope<>(true, hold, null));
        stubSaves();

        OrderResponse r = service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, "hi", null));

        ArgumentCaptor<HotelHoldClient.HoldRequest> req = ArgumentCaptor.forClass(HotelHoldClient.HoldRequest.class);
        verify(hotelHoldClient).hold(req.capture());
        assertThat(req.getValue().quoteId()).isEqualTo(quoteId);
        assertThat(req.getValue().principalUserId()).isEqualTo(customerId);
        assertThat(req.getValue().idempotencyKey()).isNotBlank().hasSizeLessThanOrEqualTo(100);

        ArgumentCaptor<Order> oc = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(oc.capture());
        assertThat(oc.getValue().getId()).isEqualTo(req.getValue().orderCorrelationId());

        assertThat(r.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(r.currency()).isEqualTo("EUR");
        assertThat(r.totalAmount()).isEqualByComparingTo("321.50");
        assertThat(r.holdExpiresAt()).isEqualTo(hold.expiresAt());
        var item = r.items().get(0);
        assertThat(item.vertical()).isEqualTo(OrderItemVertical.HOTEL);
        assertThat(item.productRef()).isEqualTo(hold.roomTypeId());
        assertThat(item.fulfillmentRef()).isEqualTo(hold.holdId());
        assertThat(item.supplierOrganizationId()).isEqualTo(orgId);
        assertThat(item.unitAmount()).isEqualByComparingTo("321.50");
        assertThat(item.quantity()).isEqualTo(1);
        assertThat(item.description()).isEqualTo("2026-12-01-2026-12-03, 2 gece, 1 oda");
        verify(eventPublisher).publishOrderCreated(any());
    }

    @Test
    void createHotelOrder_requestRecordHasNoPriceFields() {
        assertThat(HotelOrderCreateRequest.class.getRecordComponents())
                .extracting(c -> c.getName()).containsExactlyInAnyOrder("quoteId", "notes", "promoCode");
    }

    @Test
    void createHotelOrder_noIdentity_throws() {
        assertThatThrownBy(() -> service.createHotelOrder(null, new HotelOrderCreateRequest(quoteId, null, null)))
                .isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(hotelHoldClient);
    }

    @Test
    void createHotelOrder_sameQuoteTwice_returnsExistingWithoutNewHold() {
        UUID orderId = UUID.randomUUID();
        Order existing = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L);
        when(orderRepository.findByCustomerIdAndQuoteId(customerId, quoteId)).thenReturn(List.of(existing));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse r = service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, null, null));

        assertThat(r.id()).isEqualTo(orderId);
        verifyNoInteractions(hotelHoldClient);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createHotelOrder_hotelRejects_noOrderSaved() {
        when(orderRepository.findByCustomerIdAndQuoteId(customerId, quoteId)).thenReturn(List.of());
        when(hotelHoldClient.hold(any())).thenThrow(new HotelRejectedException(404, "NOT_FOUND", "no quote"));
        assertThatThrownBy(() -> service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, null, null)))
                .isInstanceOf(HotelRejectedException.class);

        doThrow(new HotelUnavailableException("down")).when(hotelHoldClient).hold(any());
        assertThatThrownBy(() -> service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, null, null)))
                .isInstanceOf(HotelUnavailableException.class);

        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createHotelOrder_saveFails_releasesHoldAndRethrows() {
        HotelHoldClient.HoldDto hold = hold();
        when(orderRepository.findByCustomerIdAndQuoteId(customerId, quoteId)).thenReturn(List.of());
        when(hotelHoldClient.hold(any())).thenReturn(new HotelHoldClient.Envelope<>(true, hold, null));
        when(orderRepository.saveAndFlush(any(Order.class))).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
        verify(hotelHoldClient).release(hold.holdId());
    }

    @Test
    void create_genericEndpointRejectsHotelItems() {
        OrderItemCreateRequest hotel = new OrderItemCreateRequest(OrderItemVertical.HOTEL, null, UUID.randomUUID(),
                "x", new BigDecimal("0.01"), 1, "USD");
        OrderCreateRequest request = new OrderCreateRequest(customerId, "USD", null, null, List.of(hotel));
        assertThatThrownBy(() -> service.create(customerId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).getRuleCode())
                .isEqualTo("USE_QUOTE_ENDPOINT");
        verifyNoInteractions(orderRepository);
    }

    private OrderItem hotelItem(UUID orderId, UUID holdId) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setVertical(OrderItemVertical.HOTEL);
        item.setFulfillmentRef(holdId);
        item.setUnitAmount(BigDecimal.TEN);
        item.setQuantity(1);
        return item;
    }

    private static com.waylo.packagebookingservice.client.PaymentEscrowClient.CancellationDto dto(boolean cancellable, String outcome, String refund) {
        return new com.waylo.packagebookingservice.client.PaymentEscrowClient.CancellationDto(UUID.randomUUID(), outcome, cancellable,
                cancellable ? null : "ALREADY_RELEASED", new BigDecimal("100.00"), new BigDecimal(refund), BigDecimal.ZERO, BigDecimal.ZERO, "TRY", "HELD");
    }

    private static <T> com.waylo.packagebookingservice.client.PaymentEscrowClient.Envelope<T> env(T d) {
        return new com.waylo.packagebookingservice.client.PaymentEscrowClient.Envelope<>(true, d, null);
    }

    @Test
    void cancel_paidOrder_fullRefund_cancelsPerPolicySnapshotAndReleasesPromo() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = hotelItem(orderId, UUID.randomUUID());
        item.setId(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.CONFIRMED, 0L)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(hotelHoldClient.release(any())).thenThrow(new HotelRejectedException(422, "HOLD_INVALID_STATE", "confirmed"));
        when(paymentEscrowClient.preview(eq(item.getId()), any())).thenReturn(env(dto(true, "REFUND", "100.00")));
        when(paymentEscrowClient.cancel(eq(item.getId()), any())).thenReturn(env(dto(true, "REFUND", "100.00")));

        assertThat(service.cancel(customerId, orderId, null).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(item.getCancelOutcome()).isEqualTo("REFUND");
        assertThat(item.getRefundAmount()).isEqualByComparingTo("100.00");
        verify(pricingClient).release(orderId);
    }

    @Test
    void cancel_paidOrder_noRefund_keepsPromoConsumed() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = hotelItem(orderId, UUID.randomUUID());
        item.setId(UUID.randomUUID());
        item.setCancellationPolicy("NON_REFUNDABLE");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.CONFIRMED, 0L)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(hotelHoldClient.release(any())).thenThrow(new HotelRejectedException(422, "HOLD_INVALID_STATE", "confirmed"));
        when(paymentEscrowClient.preview(eq(item.getId()), any())).thenReturn(env(dto(true, "NO_REFUND", "0")));
        when(paymentEscrowClient.cancel(argThat(id -> id.equals(item.getId())), argThat(p -> "NON_REFUNDABLE".equals(p.policy()))))
                .thenReturn(env(dto(true, "NO_REFUND", "0")));

        assertThat(service.cancel(customerId, orderId, null).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(item.getCancelOutcome()).isEqualTo("NO_REFUND");
        verify(pricingClient, never()).release(any());
    }

    @Test
    void cancel_paidOrder_alreadyUsed_rejectedBeforeAnyMoneyMovesAndOrderUnchanged() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = hotelItem(orderId, UUID.randomUUID());
        item.setId(UUID.randomUUID());
        Order order = persistedOrder(orderId, customerId, OrderStatus.CONFIRMED, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(paymentEscrowClient.preview(eq(item.getId()), any())).thenReturn(env(dto(false, "NO_REFUND", "0")));

        assertThatThrownBy(() -> service.cancel(customerId, orderId, null)).isInstanceOf(BusinessRuleException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(paymentEscrowClient, never()).cancel(any(), any());
        verify(hotelHoldClient, never()).release(any());
    }

    @Test
    void preview_sumsRefundsOfPolicyOutcomes_andIsReadOnly() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = hotelItem(orderId, UUID.randomUUID());
        item.setId(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.CONFIRMED, 0L)));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        when(paymentEscrowClient.preview(eq(item.getId()), any())).thenReturn(env(dto(true, "REFUND", "100.00")));

        var p = service.cancellationPreview(customerId, orderId);
        assertThat(p.cancellable()).isTrue();
        assertThat(p.totalRefund()).isEqualByComparingTo("100.00");
        assertThat(p.items().get(0).outcome()).isEqualTo("REFUND");
        verify(paymentEscrowClient, never()).cancel(any(), any());
    }

    @Test
    void preview_pendingOrder_nothingToRefund() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = hotelItem(orderId, UUID.randomUUID());
        item.setId(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L)));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        var p = service.cancellationPreview(customerId, orderId);
        assertThat(p.cancellable()).isTrue();
        assertThat(p.totalRefund()).isEqualByComparingTo("0");
        verifyNoInteractions(paymentEscrowClient);
    }

    @Test
    void preview_otherCustomer_denied() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, UUID.randomUUID(), OrderStatus.CONFIRMED, 0L)));
        assertThatThrownBy(() -> service.cancellationPreview(customerId, orderId)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancel_pendingOrder_doesNotCallPayment() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        service.cancel(customerId, orderId, null);
        verifyNoInteractions(paymentEscrowClient);
    }

    @Test
    void markPaid_confirmsHoldAndSetsConfirmed_andIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        OrderItem item = hotelItem(orderId, holdId);
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));

        assertThat(service.markPaid(orderId).status()).isEqualTo("CONFIRMED");
        verify(hotelHoldClient).confirm(holdId);
        service.markPaid(orderId);
        verify(hotelHoldClient, times(1)).confirm(holdId);
    }

    @Test
    void markPaid_holdRejected_orderStaysPending() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(hotelItem(orderId, holdId)));
        when(hotelHoldClient.confirm(holdId)).thenThrow(new HotelRejectedException(422, "HOLD_EXPIRED", "expired"));

        assertThatThrownBy(() -> service.markPaid(orderId)).isInstanceOf(HotelRejectedException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void markPaid_cancelledOrder_422() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.CANCELLED, 0L)));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.markPaid(orderId)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getInternal_exposesAuthoritativeItemAmounts() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = hotelItem(orderId, UUID.randomUUID());
        item.setCurrency("USD");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L)));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item));
        var r = service.getInternal(orderId);
        assertThat(r.items()).hasSize(1);
        assertThat(r.items().get(0).vertical()).isEqualTo("HOTEL");
        assertThat(r.items().get(0).amount()).isEqualByComparingTo("10");
    }

    @Test
    void cancel_hotelOrder_releasesHold() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(hotelItem(orderId, holdId)));

        assertThat(service.cancel(customerId, orderId, null).status()).isEqualTo(OrderStatus.CANCELLED);
        verify(hotelHoldClient).release(holdId);
    }

    @Test
    void cancel_hotelOrder_holdAlreadyGone_stillCancels() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L)));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(hotelItem(orderId, holdId)));
        when(hotelHoldClient.release(holdId)).thenThrow(new HotelRejectedException(422, "HOLD_INVALID_STATE", "confirmed"));

        assertThat(service.cancel(customerId, orderId, null).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_hotelOrder_hotelDown_orderUnchanged() {
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        Order order = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(hotelItem(orderId, holdId)));
        when(hotelHoldClient.release(holdId)).thenThrow(new HotelUnavailableException("down"));

        assertThatThrownBy(() -> service.cancel(customerId, orderId, null)).isInstanceOf(HotelUnavailableException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void markPaid_createsVouchers() {
        UUID orderId = UUID.randomUUID();
        Order o = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0);
        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(o));
        when(orderRepository.saveAndFlush(o)).thenReturn(o);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        service.markPaid(orderId);
        verify(voucherService).createForItems(List.of());
    }

    @Test
    void getVouchers_ownerOnly() {
        UUID orderId = UUID.randomUUID();
        Order o = persistedOrder(orderId, customerId, OrderStatus.CONFIRMED, 0);
        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(o));
        assertThatThrownBy(() -> service.getVouchers(UUID.randomUUID(), orderId))
                .isInstanceOf(com.waylo.packagebookingservice.exception.AccessDeniedException.class);
        verifyNoInteractions(voucherService);
        service.getVouchers(customerId, orderId);
        verify(voucherService).listForOrder(orderId);
    }
}
