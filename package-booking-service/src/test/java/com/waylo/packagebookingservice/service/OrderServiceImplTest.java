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

import java.math.BigDecimal;
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

    private OrderServiceImpl service;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, orderItemRepository, eventPublisher);
    }

    private OrderItemCreateRequest validItem(String currency) {
        return new OrderItemCreateRequest(
                OrderItemVertical.HOTEL, UUID.randomUUID(), UUID.randomUUID(),
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
}
