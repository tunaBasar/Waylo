package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.AdminOrderResponse;
import com.waylo.packagebookingservice.exception.AccessDeniedException;
import com.waylo.packagebookingservice.repository.OrderItemRepository;
import com.waylo.packagebookingservice.repository.OrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Admin order list (PACKAGE-ORDER-CONTRACT.md section 7). ADMIN only, the role comes from the gateway-verified header. */
@Service
public class AdminOrderService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public AdminOrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse.Page list(String role, OrderStatus status, OrderItemVertical vertical, LocalDate from,
                                        LocalDate to, int page, int size) {
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("Administrator role required");
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(ZONE).toInstant()));
            if (to != null) ps.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay(ZONE).toInstant()));
            if (vertical != null) {
                var sub = query.subquery(UUID.class);
                var i = sub.from(OrderItem.class);
                sub.select(i.get("orderId")).where(cb.equal(i.get("orderId"), root.get("id")), cb.equal(i.get("vertical"), vertical));
                ps.add(cb.exists(sub));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        var result = orderRepository.findAll(spec, PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<UUID> ids = result.getContent().stream().map(Order::getId).toList();
        Map<UUID, List<OrderItem>> items = ids.isEmpty() ? Map.of()
                : orderItemRepository.findByOrderIdIn(ids).stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        List<AdminOrderResponse> rows = result.getContent().stream()
                .map(o -> toRow(o, items.getOrDefault(o.getId(), List.of()))).toList();
        return new AdminOrderResponse.Page(rows, p, s, result.getTotalElements());
    }

    static AdminOrderResponse toRow(Order o, List<OrderItem> items) {
        return new AdminOrderResponse(o.getId(), o.getCustomerId(), o.getStatus().name(), paymentState(o.getStatus(), items),
                o.getCurrency(), o.getTotalAmount(), o.getCreatedAt(),
                items.stream().map(i -> new AdminOrderResponse.Item(i.getId(), i.getVertical().name(), i.getDescription(),
                        i.getNetAmount() != null ? i.getNetAmount() : i.getSubtotal(), i.getPromoCode(),
                        i.getCancelOutcome(), i.getRefundAmount())).toList());
    }

    static String paymentState(OrderStatus status, List<OrderItem> items) {
        if (status == OrderStatus.PENDING || status == OrderStatus.FAILED) return "UNPAID";
        long refunded = items.stream().filter(i -> "REFUND".equals(i.getCancelOutcome())).count();
        boolean anyOutcome = items.stream().anyMatch(i -> i.getCancelOutcome() != null);
        if (status == OrderStatus.CANCELLED && !anyOutcome) return "UNPAID";
        if (!items.isEmpty() && refunded == items.size()) return "REFUNDED";
        if (refunded > 0) return "PARTIALLY_REFUNDED";
        return "PAID";
    }
}
