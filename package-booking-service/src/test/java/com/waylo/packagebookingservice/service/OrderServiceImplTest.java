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

    private OrderServiceImpl service;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, orderItemRepository, eventPublisher, hotelHoldClient);
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
                "EUR", new BigDecimal("321.50"), Instant.parse("2026-12-01T10:00:00Z"));
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

        OrderResponse r = service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, "hi"));

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
                .extracting(c -> c.getName()).containsExactlyInAnyOrder("quoteId", "notes");
    }

    @Test
    void createHotelOrder_noIdentity_throws() {
        assertThatThrownBy(() -> service.createHotelOrder(null, new HotelOrderCreateRequest(quoteId, null)))
                .isInstanceOf(AuthenticationRequiredException.class);
        verifyNoInteractions(hotelHoldClient);
    }

    @Test
    void createHotelOrder_sameQuoteTwice_returnsExistingWithoutNewHold() {
        UUID orderId = UUID.randomUUID();
        Order existing = persistedOrder(orderId, customerId, OrderStatus.PENDING, 0L);
        when(orderRepository.findByCustomerIdAndQuoteId(customerId, quoteId)).thenReturn(List.of(existing));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        OrderResponse r = service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, null));

        assertThat(r.id()).isEqualTo(orderId);
        verifyNoInteractions(hotelHoldClient);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void createHotelOrder_hotelRejects_noOrderSaved() {
        when(orderRepository.findByCustomerIdAndQuoteId(customerId, quoteId)).thenReturn(List.of());
        when(hotelHoldClient.hold(any())).thenThrow(new HotelRejectedException(404, "NOT_FOUND", "no quote"));
        assertThatThrownBy(() -> service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, null)))
                .isInstanceOf(HotelRejectedException.class);

        doThrow(new HotelUnavailableException("down")).when(hotelHoldClient).hold(any());
        assertThatThrownBy(() -> service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, null)))
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

        assertThatThrownBy(() -> service.createHotelOrder(customerId, new HotelOrderCreateRequest(quoteId, null)))
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
}
