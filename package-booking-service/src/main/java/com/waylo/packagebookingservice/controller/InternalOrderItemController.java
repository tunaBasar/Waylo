package com.waylo.packagebookingservice.controller;

import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.dto.ApiResponse;
import com.waylo.packagebookingservice.dto.InternalOrderItemResponse;
import com.waylo.packagebookingservice.exception.ResourceNotFoundException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Service-to-service only (X-Internal-Token enforced by InternalTokenFilter). Read model for checkin-service. */
@RestController
@RequestMapping("/internal/orders/items")
public class InternalOrderItemController {

    private final OrderItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final VoucherRepository voucherRepository;

    public InternalOrderItemController(OrderItemRepository itemRepository, OrderRepository orderRepository,
                                       VoucherRepository voucherRepository) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.voucherRepository = voucherRepository;
    }

    @GetMapping("/{orderItemId}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<InternalOrderItemResponse>> get(@PathVariable UUID orderItemId) {
        OrderItem item = itemRepository.findById(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", orderItemId));
        Order order = orderRepository.findById(item.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", item.getOrderId()));
        String voucher = voucherRepository.findByOrderItemId(orderItemId).map(v -> v.getStatus().name()).orElse(null);
        return ResponseEntity.ok(ApiResponse.of(new InternalOrderItemResponse(item.getId(), order.getId(), order.getCustomerId(),
                order.getStatus().name(), item.getVertical().name(), item.getCancelOutcome(), item.getSupplier(),
                item.getFlightNumber(), item.getDepartureAt(), item.getSeatNumber(), item.getPnr(),
                item.getPassengerFirstName(), item.getPassengerLastName(), voucher)));
    }
}
