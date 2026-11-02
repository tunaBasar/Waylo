package com.waylo.checkinservice.service;

import com.waylo.checkinservice.client.OrderItemClient;
import com.waylo.checkinservice.domain.Checkin;
import com.waylo.checkinservice.domain.CheckinStatus;
import com.waylo.checkinservice.dto.CheckinPage;
import com.waylo.checkinservice.dto.OrderCheckinResponse;
import com.waylo.checkinservice.exception.CheckinException;
import com.waylo.checkinservice.repository.CheckinRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Online check-in of FLIGHT order items (CHECKIN-CONTRACT.md). */
@Service
public class OrderCheckinService {

    static final Duration OPENS_BEFORE = Duration.ofHours(24);
    static final Duration CLOSES_BEFORE = Duration.ofHours(1);

    private final CheckinRepository repository;
    private final OrderItemClient orderItems;
    private final Clock clock;

    public OrderCheckinService(CheckinRepository repository, OrderItemClient orderItems, Clock clock) {
        this.repository = repository;
        this.orderItems = orderItems;
        this.clock = clock;
    }

    public OrderCheckinResponse checkIn(UUID orderItemId, UUID userId) {
        OrderItemClient.OrderItemDto item = orderItems.get(orderItemId).data();
        if (item == null || !userId.equals(item.customerId())) {
            throw new CheckinException(403, "FORBIDDEN", "Only the order owner can check in");
        }
        if (!"FLIGHT".equals(item.vertical())) {
            throw new CheckinException(422, "NOT_A_FLIGHT_ITEM", "Only flight items can be checked in");
        }
        if (!"INTERNAL".equals(item.supplier())) {
            throw new CheckinException(409, "CHECKIN_AT_AIRLINE", "Check-in for this flight is done at the airline");
        }
        if ("CANCELLED".equals(item.orderStatus()) || item.cancelOutcome() != null || "VOID".equals(item.voucherStatus())) {
            throw new CheckinException(409, "ORDER_ITEM_CANCELLED", "This flight item is cancelled or refunded");
        }
        Checkin existing = repository.findByOrderItemId(orderItemId).orElse(null);
        if (existing != null) return toResponse(existing);
        if ("REDEEMED".equals(item.voucherStatus())) {
            throw new CheckinException(409, "ALREADY_BOARDED", "The boarding QR was already scanned");
        }
        if (!"CONFIRMED".equals(item.orderStatus()) || !"ACTIVE".equals(item.voucherStatus()) || item.departureAt() == null) {
            throw new CheckinException(409, "ORDER_NOT_CONFIRMED", "The order is not paid and ticketed yet");
        }
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (now.isBefore(item.departureAt().minus(OPENS_BEFORE))) {
            throw new CheckinException(422, "CHECKIN_NOT_OPEN", "Online check-in opens 24 hours before departure");
        }
        if (now.isAfter(item.departureAt().minus(CLOSES_BEFORE))) {
            throw new CheckinException(422, "CHECKIN_CLOSED", "Online check-in closed 1 hour before departure");
        }
        Checkin c = new Checkin();
        c.setOrderItemId(orderItemId);
        c.setOrderId(item.orderId());
        c.setCustomerId(item.customerId());
        c.setPnr(item.pnr());
        c.setPassengerName((item.passengerFirstName() + " " + item.passengerLastName()).trim());
        c.setFlightNumber(item.flightNumber());
        c.setDepartureAt(item.departureAt());
        c.setSeatNumber(item.seatNumber());
        c.setStatus(CheckinStatus.CHECKED_IN);
        c.setCheckedInAt(now);
        try {
            return toResponse(repository.saveAndFlush(c));
        } catch (DataIntegrityViolationException race) {
            return repository.findByOrderItemId(orderItemId).map(this::toResponse).orElseThrow(() -> race);
        }
    }

    public List<OrderCheckinResponse> mine(UUID userId) {
        return repository.findByCustomerIdAndOrderItemIdIsNotNullOrderByDepartureAtDesc(userId).stream().map(this::toResponse).toList();
    }

    public CheckinPage adminList(String role, int page, int size) {
        if (!"ADMIN".equals(role)) throw new CheckinException(403, "FORBIDDEN", "Administrator role required");
        Page<Checkin> p = repository.findByOrderItemIdIsNotNull(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "checkedInAt")));
        return new CheckinPage(p.getContent().stream().map(this::toResponse).toList(), p.getNumber(), p.getSize(), p.getTotalElements());
    }

    private OrderCheckinResponse toResponse(Checkin c) {
        return new OrderCheckinResponse(c.getId(), c.getOrderItemId(), c.getOrderId(), c.getCustomerId(), c.getPnr(),
                c.getPassengerName(), c.getFlightNumber(), c.getDepartureAt(), c.getSeatNumber(), c.getStatus(), c.getCheckedInAt());
    }
}
