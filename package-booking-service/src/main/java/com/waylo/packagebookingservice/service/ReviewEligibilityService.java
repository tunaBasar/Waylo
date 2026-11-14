package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.domain.Voucher;
import com.waylo.packagebookingservice.domain.VoucherStatus;
import com.waylo.packagebookingservice.dto.ReviewEligibilityResponse;
import com.waylo.packagebookingservice.exception.ResourceNotFoundException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import com.waylo.packagebookingservice.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Verified-review rule for hotel stays (RM-29): the item is the caller's, a HOTEL item, its order is CONFIRMED,
 * not cancelled/no-show, the voucher was REDEEMED (guest checked in) and checkOut is not after today in the
 * business timezone. Membership in the hotel organization is checked by hotel-service, which owns that data.
 */
@Service
public class ReviewEligibilityService {

    private final OrderItemRepository itemRepository;
    private final OrderRepository orderRepository;
    private final VoucherRepository voucherRepository;
    private final Clock clock;

    public ReviewEligibilityService(OrderItemRepository itemRepository, OrderRepository orderRepository,
                                    VoucherRepository voucherRepository, Clock clock) {
        this.itemRepository = itemRepository;
        this.orderRepository = orderRepository;
        this.voucherRepository = voucherRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse check(UUID orderItemId, UUID customerId) {
        OrderItem item = itemRepository.findById(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", orderItemId));
        Order order = orderRepository.findById(item.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", item.getOrderId()));
        Voucher voucher = voucherRepository.findByOrderItemId(orderItemId).orElse(null);
        return evaluate(item, order, voucher, customerId);
    }

    @Transactional(readOnly = true)
    public List<ReviewEligibilityResponse> listEligible(UUID customerId) {
        List<ReviewEligibilityResponse> result = new ArrayList<>();
        for (Order order : orderRepository.findByCustomerIdAndStatus(customerId, OrderStatus.CONFIRMED)) {
            for (OrderItem item : itemRepository.findByOrderId(order.getId())) {
                if (item.getVertical() != OrderItemVertical.HOTEL) continue;
                ReviewEligibilityResponse r = evaluate(item, order,
                        voucherRepository.findByOrderItemId(item.getId()).orElse(null), customerId);
                if (r.eligible()) result.add(r);
            }
        }
        return result;
    }

    ReviewEligibilityResponse evaluate(OrderItem item, Order order, Voucher voucher, UUID customerId) {
        return respond(item, order, reason(item, order, voucher, customerId));
    }

    private String reason(OrderItem item, Order order, Voucher voucher, UUID customerId) {
        if (customerId == null || !customerId.equals(order.getCustomerId())) return "NOT_OWNER";
        if (item.getVertical() != OrderItemVertical.HOTEL) return "NOT_HOTEL_ITEM";
        if (item.getCancelOutcome() != null) return "CANCELLED_OR_NO_SHOW";
        if (order.getStatus() != OrderStatus.CONFIRMED) return "ORDER_NOT_CONFIRMED";
        if (voucher == null || voucher.getStatus() != VoucherStatus.REDEEMED) return "NOT_CHECKED_IN";
        LocalDate checkOut = item.getCheckOut();
        if (checkOut == null || checkOut.isAfter(LocalDate.now(clock))) return "STAY_NOT_FINISHED";
        return null;
    }

    private static ReviewEligibilityResponse respond(OrderItem item, Order order, String reason) {
        return new ReviewEligibilityResponse(item.getId(), reason == null, reason, order.getCustomerId(),
                item.getFulfillmentRef(), item.getSupplierOrganizationId(), item.getDescription(),
                item.getCheckIn(), item.getCheckOut());
    }
}
