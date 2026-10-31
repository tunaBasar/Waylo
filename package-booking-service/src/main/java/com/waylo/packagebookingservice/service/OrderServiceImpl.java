package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.HotelHoldClient;
import com.waylo.packagebookingservice.client.PaymentEscrowClient;
import com.waylo.packagebookingservice.dto.InternalOrderResponse;
import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.HotelOrderCreateRequest;
import com.waylo.packagebookingservice.dto.OrderCreateRequest;
import com.waylo.packagebookingservice.dto.OrderItemCreateRequest;
import com.waylo.packagebookingservice.dto.OrderItemResponse;
import com.waylo.packagebookingservice.dto.OrderResponse;
import com.waylo.packagebookingservice.dto.VoucherResponse;
import com.waylo.packagebookingservice.event.OrderCancelledEvent;
import com.waylo.packagebookingservice.event.OrderCreatedEvent;
import com.waylo.packagebookingservice.event.OrderEventPublisher;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.exception.AuthenticationRequiredException;
import com.waylo.packagebookingservice.exception.BusinessRuleException;
import com.waylo.packagebookingservice.exception.HotelRejectedException;
import com.waylo.packagebookingservice.exception.ResourceNotFoundException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final HotelHoldClient hotelHoldClient;
    private final PaymentEscrowClient paymentEscrowClient;
    private final VoucherService voucherService;

    public OrderServiceImpl(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             OrderEventPublisher eventPublisher,
                             HotelHoldClient hotelHoldClient,
                             PaymentEscrowClient paymentEscrowClient,
                             VoucherService voucherService) {
        this.voucherService = voucherService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
        this.hotelHoldClient = hotelHoldClient;
        this.paymentEscrowClient = paymentEscrowClient;
    }

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    /**
     * Hotel order from a quote. Every monetary/product field is taken from the hotel hold response,
     * the request carries only quoteId + notes. productRef = roomTypeId. supplierOrganizationId = hold.organizationId()
     * (null only for legacy hotels without an organization).
     */
    @Override
    public OrderResponse createHotelOrder(UUID actorUserId, HotelOrderCreateRequest request) {
        requireIdentity(actorUserId);

        List<Order> existing = orderRepository.findByCustomerIdAndQuoteId(actorUserId, request.quoteId());
        if (!existing.isEmpty()) {
            Order prior = existing.get(0);
            if (prior.getStatus() == OrderStatus.CANCELLED || prior.getStatus() == OrderStatus.FAILED) {
                throw new BusinessRuleException("QUOTE_ALREADY_USED",
                        "An earlier order for this quote was cancelled, please request a new quote");
            }
            return toResponse(prior, orderItemRepository.findByOrderId(prior.getId()));
        }

        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "ho-" + UUID.nameUUIDFromBytes(
                (actorUserId + ":" + request.quoteId()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        HotelHoldClient.HoldDto hold = hotelHoldClient.hold(new HotelHoldClient.HoldRequest(
                request.quoteId(), idempotencyKey, actorUserId, orderId)).data();

        try {
            Order order = new Order();
            order.setId(orderId);
            order.setCustomerId(actorUserId);
            order.setCurrency(hold.currency());
            order.setQuoteId(request.quoteId());
            order.setNotes(request.notes());
            order.setTotalAmount(hold.totalAmount());
            order.setStatus(OrderStatus.PENDING);
            order.setHoldExpiresAt(hold.expiresAt());
            order = orderRepository.saveAndFlush(order);

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setVertical(OrderItemVertical.HOTEL);
            item.setSupplierOrganizationId(hold.organizationId());
            item.setProductRef(hold.roomTypeId());
            item.setFulfillmentRef(hold.holdId());
            item.setDescription(hold.checkIn() + "-" + hold.checkOut() + ", " + hold.nights() + " gece, "
                    + hold.rooms() + " oda");
            item.setCheckIn(hold.checkIn());
            item.setCheckOut(hold.checkOut());
            item.setUnitAmount(hold.totalAmount());
            item.setQuantity(1);
            item.setCurrency(hold.currency());
            OrderItem savedItem = orderItemRepository.saveAndFlush(item);

            eventPublisher.publishOrderCreated(new OrderCreatedEvent(
                    order.getId(), order.getCustomerId(), order.getCurrency(), order.getTotalAmount()));
            return toResponse(order, List.of(savedItem));
        } catch (RuntimeException e) {
            try {
                hotelHoldClient.release(hold.holdId());
            } catch (RuntimeException releaseFailure) {
                log.error("Could not release hold {} after order save failure; expiry scheduler will reclaim it",
                        hold.holdId(), releaseFailure);
            }
            throw e;
        }
    }

    @Override
    public OrderResponse create(UUID actorUserId, OrderCreateRequest request) {
        requireIdentity(actorUserId);
        if (!actorUserId.equals(request.customerId())) {
            throw new AccessDeniedException("Cannot create an order on behalf of another customer");
        }

        List<OrderItemCreateRequest> itemRequests = request.items();
        for (OrderItemCreateRequest item : itemRequests) {
            if (item.vertical() == OrderItemVertical.HOTEL) {
                throw new BusinessRuleException("USE_QUOTE_ENDPOINT",
                        "Hotel orders must be created from a quote via POST /api/orders/hotel");
            }
        }
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
    public List<VoucherResponse> getVouchers(UUID actorUserId, UUID orderId) {
        requireIdentity(actorUserId);
        Order order = findOrThrow(orderId);
        requireOwner(actorUserId, order);
        return voucherService.listForOrder(orderId);
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

        // Paid order (CONFIRMED): refund every HELD escrow item first. If payment-service is down this throws 503 and the
        // order stays unchanged. TODO(cancellation policy task): for now this is a FULL refund of all HELD items regardless of
        // the cancel policy snapshot. Items already RELEASED to the supplier make payment-service answer 422
        // ESCROW_ALREADY_RELEASED and the cancel is rejected.
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
                paymentEscrowClient.refund(item.getId());
            }
        }

        // Release hotel holds: if hotel-service is down this throws 503 and the order stays unchanged.
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            if (item.getVertical() == OrderItemVertical.HOTEL && item.getFulfillmentRef() != null) {
                try {
                    hotelHoldClient.release(item.getFulfillmentRef());
                } catch (HotelRejectedException alreadyGone) {
                    log.info("Hold {} not releasable ({}), continuing cancel", item.getFulfillmentRef(),
                            alreadyGone.getCode());
                }
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.saveAndFlush(order);
        voucherService.voidForOrder(order.getId());

        eventPublisher.publishOrderCancelled(new OrderCancelledEvent(order.getId(), order.getCustomerId()));

        return toResponse(order, orderItemRepository.findByOrderId(order.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public InternalOrderResponse getInternal(UUID orderId) {
        Order order = findOrThrow(orderId);
        return toInternal(order, orderItemRepository.findByOrderId(orderId));
    }

    @Override
    public InternalOrderResponse markPaid(UUID orderId) {
        Order order = findOrThrow(orderId);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            voucherService.createForItems(items); // idempotent
            return toInternal(order, items);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("ORDER_NOT_PAYABLE", "Order is " + order.getStatus() + ", it cannot be marked paid");
        }
        // Hold confirm is idempotent in hotel-service. A rejection (expired/released hold) propagates as 422 and
        // payment-service then cancels the provider payment and rolls back its escrow.
        for (OrderItem item : items) {
            if (item.getVertical() == OrderItemVertical.HOTEL && item.getFulfillmentRef() != null) {
                hotelHoldClient.confirm(item.getFulfillmentRef());
            }
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order = orderRepository.saveAndFlush(order);
        voucherService.createForItems(items);
        return toInternal(order, items);
    }

    private InternalOrderResponse toInternal(Order order, List<OrderItem> items) {
        return new InternalOrderResponse(order.getId(), order.getCustomerId(), order.getStatus().name(), order.getCurrency(),
                order.getTotalAmount(),
                items.stream().map(i -> new InternalOrderResponse.Item(i.getId(), i.getVertical().name(),
                        i.getSupplierOrganizationId(), i.getSubtotal(), i.getCurrency())).toList());
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
                order.getUpdatedAt(),
                order.getHoldExpiresAt()
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
