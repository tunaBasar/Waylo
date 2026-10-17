package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.OrderCreateRequest;
import com.waylo.packagebookingservice.dto.OrderItemCreateRequest;
import com.waylo.packagebookingservice.dto.OrderItemResponse;
import com.waylo.packagebookingservice.dto.OrderResponse;
import com.waylo.packagebookingservice.event.OrderCancelledEvent;
import com.waylo.packagebookingservice.event.OrderCreatedEvent;
import com.waylo.packagebookingservice.event.OrderEventPublisher;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.AuthenticationRequiredException;
import com.waylo.packagebookingservice.exception.BusinessRuleException;
import com.waylo.packagebookingservice.exception.ResourceNotFoundException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * v1 self-service kurali: bir musteri yalniz kendi customerId'si altinda order olusturabilir
 * ve goruntuleyebilir (PRODUCT-ARCHITECTURE.md SS7 "GET /api/v1/me/orders/{id}": getOwnedOrder).
 * Platform-genis cross-customer erisim bu is paketinde yoktur.
 */
@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderServiceImpl(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OrderResponse create(UUID actorUserId, OrderCreateRequest request) {
        requireIdentity(actorUserId);
        if (!actorUserId.equals(request.customerId())) {
            throw new AccessDeniedException("Cannot create an order on behalf of another customer");
        }

        List<OrderItemCreateRequest> itemRequests = request.items();
        for (OrderItemCreateRequest item : itemRequests) {
            if (!item.currency().equals(request.currency())) {
                throw new BusinessRuleException("CURRENCY_MISMATCH",
                        "Item currency " + item.currency() + " does not match order currency " + request.currency());
            }
        }

        BigDecimal totalAmount = itemRequests.stream()
                .map(i -> i.unitAmount().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setCurrency(request.currency());
        order.setQuoteId(request.quoteId());
        order.setNotes(request.notes());
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);
        order = orderRepository.save(order);

        List<OrderItem> savedItems = new java.util.ArrayList<>();
        for (OrderItemCreateRequest itemRequest : itemRequests) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setVertical(itemRequest.vertical());
            item.setSupplierOrganizationId(itemRequest.supplierOrganizationId());
            item.setProductRef(itemRequest.productRef());
            item.setDescription(itemRequest.description());
            item.setUnitAmount(itemRequest.unitAmount());
            item.setQuantity(itemRequest.quantity());
            item.setCurrency(itemRequest.currency());
            savedItems.add(orderItemRepository.save(item));
        }

        eventPublisher.publishOrderCreated(new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), order.getCurrency(), order.getTotalAmount()));

        return toResponse(order, savedItems);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getById(UUID actorUserId, UUID orderId) {
        requireIdentity(actorUserId);
        Order order = findOrThrow(orderId);
        requireOwner(actorUserId, order);
        return toResponse(order, orderItemRepository.findByOrderId(order.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> listOwn(UUID actorUserId) {
        requireIdentity(actorUserId);
        return orderRepository.findByCustomerId(actorUserId).stream()
                .map(order -> toResponse(order, orderItemRepository.findByOrderId(order.getId())))
                .toList();
    }

    @Override
    public OrderResponse cancel(UUID actorUserId, UUID orderId, Long expectedVersion) {
        requireIdentity(actorUserId);
        Order order = findOrThrow(orderId);
        requireOwner(actorUserId, order);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessRuleException("ALREADY_CANCELLED", "Order is already cancelled");
        }
        if (order.getStatus() == OrderStatus.FAILED) {
            throw new BusinessRuleException("TERMINAL_STATE", "Order is in a terminal failed state");
        }

        if (expectedVersion != null && !expectedVersion.equals(order.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Order.class, order.getId().toString());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.saveAndFlush(order);

        eventPublisher.publishOrderCancelled(new OrderCancelledEvent(order.getId(), order.getCustomerId()));

        return toResponse(order, orderItemRepository.findByOrderId(order.getId()));
    }

    private void requireIdentity(UUID actorUserId) {
        if (actorUserId == null) {
            throw new AuthenticationRequiredException("Authentication required");
        }
    }

    private void requireOwner(UUID actorUserId, Order order) {
        if (!order.getCustomerId().equals(actorUserId)) {
            throw new AccessDeniedException("Order does not belong to the authenticated customer");
        }
    }

    private Order findOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getCurrency(),
                order.getQuoteId(),
                order.getTotalAmount(),
                order.getNotes(),
                order.getVersion(),
                itemResponses,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getOrderId(),
                item.getVertical(),
                item.getSupplierOrganizationId(),
                item.getProductRef(),
                item.getFulfillmentRef(),
                item.getDescription(),
                item.getUnitAmount(),
                item.getQuantity(),
                item.getSubtotal(),
                item.getCurrency(),
                item.getCreatedAt()
        );
    }
}
