package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.client.HotelHoldClient;
import com.waylo.packagebookingservice.client.CarHoldClient;
import com.waylo.packagebookingservice.client.BusHoldClient;
import com.waylo.packagebookingservice.dto.BusOrderCreateRequest;
import com.waylo.packagebookingservice.client.FlightHoldClient;
import com.waylo.packagebookingservice.dto.FlightItemDetails;
import com.waylo.packagebookingservice.dto.FlightOrderCreateRequest;
import com.waylo.packagebookingservice.client.PaymentEscrowClient;
import com.waylo.packagebookingservice.dto.InternalOrderResponse;
import com.waylo.packagebookingservice.domain.Order;
import com.waylo.packagebookingservice.domain.OrderItemVertical;
import com.waylo.packagebookingservice.domain.OrderItem;
import com.waylo.packagebookingservice.domain.OrderStatus;
import com.waylo.packagebookingservice.dto.HotelOrderCreateRequest;
import com.waylo.packagebookingservice.dto.CarOrderCreateRequest;
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
import org.springframework.beans.factory.annotation.Value;
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
    private final CarHoldClient carHoldClient;
    private final BusHoldClient busHoldClient;
    private final FlightHoldClient flightHoldClient;
    private final UUID flightSellerOrganizationId;
    private final PaymentEscrowClient paymentEscrowClient;
    private final VoucherService voucherService;
    private final PricingApplier pricingApplier;
    private final CancellationPolicyResolver policyResolver;

    public OrderServiceImpl(OrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             OrderEventPublisher eventPublisher,
                             HotelHoldClient hotelHoldClient,
                             CarHoldClient carHoldClient,
                             BusHoldClient busHoldClient,
                             FlightHoldClient flightHoldClient,
                             @Value("${waylo.flight.seller-organization-id:}") String flightSellerOrganizationId,
                             PaymentEscrowClient paymentEscrowClient,
                             VoucherService voucherService,
                             PricingApplier pricingApplier,
                             CancellationPolicyResolver policyResolver) {
        this.policyResolver = policyResolver;
        this.pricingApplier = pricingApplier;
        this.voucherService = voucherService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
        this.hotelHoldClient = hotelHoldClient;
        this.carHoldClient = carHoldClient;
        this.busHoldClient = busHoldClient;
        this.flightHoldClient = flightHoldClient;
        this.flightSellerOrganizationId = flightSellerOrganizationId == null || flightSellerOrganizationId.isBlank()
                ? null : UUID.fromString(flightSellerOrganizationId.trim());
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
            PricingApplier.Priced priced = pricingApplier.price("OTEL", hold.organizationId(), hold.totalAmount(),
                    hold.currency(), actorUserId, request.promoCode(), orderId);
            order.setTotalAmount(priced.net());
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
            policyResolver.applyHotel(item, hold.cancellationPolicy());
            item.setQuantity(1);
            item.setCurrency(hold.currency());
            priced.applyTo(item);
            OrderItem savedItem = orderItemRepository.saveAndFlush(item);

            eventPublisher.publishOrderCreated(new OrderCreatedEvent(
                    order.getId(), order.getCustomerId(), order.getCurrency(), order.getTotalAmount()));
            return toResponse(order, List.of(savedItem));
        } catch (RuntimeException e) {
            pricingApplier.release(orderId);
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
    public OrderResponse createCarOrder(UUID actorUserId, CarOrderCreateRequest request) {
        requireIdentity(actorUserId);
        UUID orderId = UUID.randomUUID();
        String key = "co-" + UUID.nameUUIDFromBytes((actorUserId + ":" + request.pickupBranchId() + ":" + request.returnBranchId() + ":" + request.pickupAt() + ":" + request.returnAt()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        CarHoldClient.HoldDto hold = carHoldClient.hold(new CarHoldClient.HoldRequest(request.pickupBranchId(), request.returnBranchId(), request.category(), request.transmission(), request.pickupAt(), request.returnAt(), request.driverAge(), actorUserId, key, orderId)).data();
        try {
            Order order = new Order(); order.setId(orderId); order.setCustomerId(actorUserId); order.setCurrency(hold.currency()); PricingApplier.Priced priced = pricingApplier.price("ARAÇ", hold.organizationId(), hold.totalAmount(), hold.currency(), actorUserId, request.promoCode(), orderId); order.setTotalAmount(priced.net()); order.setStatus(OrderStatus.PENDING); order.setHoldExpiresAt(hold.expiresAt());
            order = orderRepository.saveAndFlush(order);
            OrderItem item = new OrderItem(); item.setOrderId(order.getId()); item.setVertical(OrderItemVertical.CAR); item.setSupplierOrganizationId(hold.organizationId()); item.setProductRef(hold.pickupBranchId()); item.setFulfillmentRef(hold.holdId());
            String transmission = "AUTOMATIC".equalsIgnoreCase(hold.transmission()) ? "Otomatik" : "Manuel";
            item.setDescription(hold.exampleModel() + " (" + carCategoryLabel(hold.category()) + ", " + transmission + "), " + hold.chargeableDays() + " gun"); priced.applyTo(item); item.setQuantity(1); item.setCurrency(hold.currency()); item.setPickupAt(hold.pickupAt()); item.setReturnAt(hold.returnAt()); policyResolver.applyConfigured(item);
            OrderItem saved = orderItemRepository.saveAndFlush(item);
            eventPublisher.publishOrderCreated(new OrderCreatedEvent(order.getId(), order.getCustomerId(), order.getCurrency(), order.getTotalAmount()));
            return toResponse(order, List.of(saved));
        } catch (RuntimeException e) { pricingApplier.release(orderId); try { carHoldClient.release(hold.holdId()); } catch (RuntimeException releaseFailure) { log.error("Could not release car hold {}", hold.holdId(), releaseFailure); } throw e; }
    }

    /** Bus order: price, route and times come from the bus hold, never from the client. productRef = tripId. */
    @Override
    public OrderResponse createBusOrder(UUID actorUserId, BusOrderCreateRequest request) {
        requireIdentity(actorUserId);
        UUID orderId = UUID.randomUUID();
        String seats = request.passengers().stream().map(p -> String.valueOf(p.seatNo())).sorted().collect(java.util.stream.Collectors.joining(","));
        String key = "bo-" + UUID.nameUUIDFromBytes((actorUserId + ":" + request.tripId() + ":" + request.fromSeq() + ":" + request.toSeq() + ":" + seats)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<BusHoldClient.Passenger> passengers = request.passengers().stream()
                .map(p -> new BusHoldClient.Passenger(p.seatNo(), p.name().trim(), p.gender(), p.identityNumber())).toList();
        BusHoldClient.HoldDto hold = busHoldClient.hold(new BusHoldClient.HoldRequest(request.tripId(), request.fromSeq(), request.toSeq(),
                passengers, actorUserId, key, orderId.toString())).data();
        try {
            Order order = new Order(); order.setId(orderId); order.setCustomerId(actorUserId); order.setCurrency(hold.currency()); PricingApplier.Priced priced = pricingApplier.price("OTOBÜS", hold.organizationId(), hold.totalAmount(), hold.currency(), actorUserId, request.promoCode(), orderId); order.setTotalAmount(priced.net()); order.setStatus(OrderStatus.PENDING); order.setHoldExpiresAt(hold.expiresAt());
            order = orderRepository.saveAndFlush(order);
            OrderItem item = new OrderItem(); item.setOrderId(order.getId()); item.setVertical(OrderItemVertical.BUS); item.setSupplierOrganizationId(hold.organizationId()); item.setProductRef(hold.tripId()); item.setFulfillmentRef(hold.holdId());
            item.setDescription(busDescription(hold)); priced.applyTo(item); item.setQuantity(1); item.setCurrency(hold.currency()); item.setPickupAt(hold.boardingAt()); item.setReturnAt(hold.arrivalAt()); policyResolver.applyConfigured(item);
            OrderItem saved = orderItemRepository.saveAndFlush(item);
            eventPublisher.publishOrderCreated(new OrderCreatedEvent(order.getId(), order.getCustomerId(), order.getCurrency(), order.getTotalAmount()));
            return toResponse(order, List.of(saved));
        } catch (RuntimeException e) { pricingApplier.release(orderId); try { busHoldClient.release(hold.holdId()); } catch (RuntimeException releaseFailure) { log.error("Could not release bus hold {}", hold.holdId(), releaseFailure); } throw e; }
    }

    static String busDescription(BusHoldClient.HoldDto hold) {
        java.time.LocalDateTime t = hold.boardingAt();
        String when = t == null ? "" : String.format(", %02d.%02d.%d %02d:%02d", t.getDayOfMonth(), t.getMonthValue(), t.getYear(), t.getHour(), t.getMinute());
        String seats = hold.seatNos() == null ? "" : ", koltuk " + hold.seatNos().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        String text = hold.fromCity() + " - " + hold.toCity() + when + seats;
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    @Override
    public OrderResponse createFlightOrder(UUID actorUserId, FlightOrderCreateRequest request) {
        requireIdentity(actorUserId);
        if (flightSellerOrganizationId == null) {
            throw new BusinessRuleException("FLIGHT_SELLER_NOT_CONFIGURED",
                    "Flight sales are not open: no seller organization is configured");
        }
        UUID orderId = UUID.randomUUID();
        String seat = request.seatNumber() == null || request.seatNumber().isBlank() ? "" : request.seatNumber().trim().toUpperCase();
        // one hold per (user, offer, seat, passenger): a double submit returns the same hold, and the order below is new
        String key = "fo-" + UUID.nameUUIDFromBytes((actorUserId + ":" + request.offerId() + ":" + seat + ":"
                + request.passengerFirstName().trim() + ":" + request.passengerLastName().trim())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FlightHoldClient.HoldDto hold = flightHoldClient.hold(new FlightHoldClient.HoldRequest(request.offerId(),
                seat.isEmpty() ? null : seat, request.passengerFirstName().trim(), request.passengerLastName().trim(),
                actorUserId, key, orderId.toString())).data();
        if (!orderItemRepository.findByFulfillmentRef(hold.holdId()).isEmpty()) {
            // idempotent replay of an earlier request: return the order that already owns this hold
            OrderItem prior = orderItemRepository.findByFulfillmentRef(hold.holdId()).get(0);
            Order priorOrder = findOrThrow(prior.getOrderId());
            return toResponse(priorOrder, orderItemRepository.findByOrderId(priorOrder.getId()));
        }
        try {
            Order order = new Order();
            order.setId(orderId);
            order.setCustomerId(actorUserId);
            order.setCurrency(hold.currency());
            PricingApplier.Priced priced = pricingApplier.price("UÇUŞ", flightSellerOrganizationId, hold.totalAmount(),
                    hold.currency(), actorUserId, request.promoCode(), orderId);
            order.setTotalAmount(priced.net());
            order.setStatus(OrderStatus.PENDING);
            order.setHoldExpiresAt(hold.expiresAt());
            order = orderRepository.saveAndFlush(order);

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setVertical(OrderItemVertical.FLIGHT);
            item.setSupplierOrganizationId(flightSellerOrganizationId);
            item.setProductRef(UUID.nameUUIDFromBytes(("flight:" + hold.flightNumber()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            item.setFulfillmentRef(hold.holdId());
            item.setDescription(flightDescription(hold));
            item.setQuantity(1);
            item.setCurrency(hold.currency());
            priced.applyTo(item);
            applyFlight(item, hold);
            policyResolver.applyConfigured(item);
            OrderItem saved = orderItemRepository.saveAndFlush(item);
            eventPublisher.publishOrderCreated(new OrderCreatedEvent(order.getId(), order.getCustomerId(), order.getCurrency(), order.getTotalAmount()));
            return toResponse(order, List.of(saved));
        } catch (RuntimeException e) {
            pricingApplier.release(orderId);
            try {
                flightHoldClient.release(hold.holdId());
            } catch (RuntimeException releaseFailure) {
                log.error("Could not release flight hold {}; expiry scheduler will reclaim it", hold.holdId(), releaseFailure);
            }
            throw e;
        }
    }

    private static void applyFlight(OrderItem item, FlightHoldClient.HoldDto hold) {
        item.setFlightNumber(hold.flightNumber());
        item.setDepartureAt(hold.departureTime());
        item.setSeatNumber(hold.seatNumber());
        item.setPassengerFirstName(hold.passengerFirstName());
        item.setPassengerLastName(hold.passengerLastName());
        item.setPnr(hold.pnr());
        item.setTicketNumber(hold.ticketNumber());
        item.setSupplier(hold.supplier());
    }

    static String flightDescription(FlightHoldClient.HoldDto hold) {
        String when = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                .format(hold.departureTime().atZone(VoucherService.DEFAULT_ZONE));
        return hold.flightNumber() + " " + hold.departureAirport() + "-" + hold.arrivalAirport() + ", " + when
                + ", koltuk " + hold.seatNumber();
    }

    // ---- package (cart) order: PACKAGE-ORDER-CONTRACT.md ----

    /** One taken hold plus what is needed to price it and to write its order item. */
    private record Held(OrderItemVertical vertical, String pricingName, UUID supplierOrganizationId, BigDecimal gross,
                        String currency, java.time.Instant expiresAt, UUID holdId, java.util.function.Consumer<OrderItem> fill) {}

    @Override
    public OrderResponse createPackageOrder(UUID actorUserId, com.waylo.packagebookingservice.dto.PackageOrderCreateRequest request) {
        requireIdentity(actorUserId);
        var items = request.items();
        for (int i = 0; i < items.size(); i++) {
            String missing = missingField(items.get(i));
            if (missing != null) {
                throw new com.waylo.packagebookingservice.exception.PackageItemFailedException(i,
                        new IllegalArgumentException("Missing field for " + items.get(i).vertical() + ": " + missing));
            }
        }
        UUID orderId = UUID.randomUUID();
        List<Held> holds = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < items.size(); i++) {
                try {
                    holds.add(takeHold(actorUserId, orderId, i, items.get(i)));
                } catch (RuntimeException e) {
                    throw new com.waylo.packagebookingservice.exception.PackageItemFailedException(i, e);
                }
            }
            String currency = holds.get(0).currency();
            for (int i = 1; i < holds.size(); i++) {
                if (!currency.equals(holds.get(i).currency())) {
                    throw new com.waylo.packagebookingservice.exception.PackageItemFailedException(i, new BusinessRuleException(
                            "CURRENCY_MISMATCH", "Item currency " + holds.get(i).currency() + " does not match " + currency));
                }
            }
            List<PricingApplier.Priced> priced = priceAll(actorUserId, orderId, request.promoCode(), holds);

            Order order = new Order();
            order.setId(orderId);
            order.setCustomerId(actorUserId);
            order.setCurrency(currency);
            order.setNotes(request.notes());
            order.setTotalAmount(priced.stream().map(PricingApplier.Priced::net).reduce(BigDecimal.ZERO, BigDecimal::add));
            order.setStatus(OrderStatus.PENDING);
            order.setHoldExpiresAt(holds.stream().map(Held::expiresAt).min(java.util.Comparator.naturalOrder()).orElse(null));
            order = orderRepository.saveAndFlush(order);
            List<OrderItem> saved = new java.util.ArrayList<>();
            for (int i = 0; i < holds.size(); i++) {
                Held h = holds.get(i);
                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setVertical(h.vertical());
                item.setSupplierOrganizationId(h.supplierOrganizationId());
                item.setFulfillmentRef(h.holdId());
                item.setQuantity(1);
                item.setCurrency(h.currency());
                h.fill().accept(item);
                priced.get(i).applyTo(item);
                saved.add(orderItemRepository.saveAndFlush(item));
            }
            eventPublisher.publishOrderCreated(new OrderCreatedEvent(
                    order.getId(), order.getCustomerId(), order.getCurrency(), order.getTotalAmount()));
            return toResponse(order, saved);
        } catch (RuntimeException e) {
            pricingApplier.release(orderId);
            for (Held h : holds) releaseHoldQuietly(h.vertical(), h.holdId());
            throw e;
        }
    }

    /** Promo applies once, on the first item that accepts it (PACKAGE-ORDER-CONTRACT.md section 3). */
    private List<PricingApplier.Priced> priceAll(UUID customerId, UUID orderId, String promoCode, List<Held> holds) {
        String promo = promoCode == null || promoCode.isBlank() ? null : promoCode.trim();
        RuntimeException firstInvalid = null;
        List<PricingApplier.Priced> out = new java.util.ArrayList<>();
        for (int i = 0; i < holds.size(); i++) {
            Held h = holds.get(i);
            try {
                PricingApplier.Priced p = pricingApplier.price(h.pricingName(), h.supplierOrganizationId(), h.gross(),
                        h.currency(), customerId, promo, orderId);
                if (promo != null) promo = null; // consumed: pricing-service allows one reservation per order
                out.add(p);
            } catch (BusinessRuleException e) {
                if (promo == null || !"PROMO_CODE_INVALID".equals(e.getRuleCode())) {
                    throw new com.waylo.packagebookingservice.exception.PackageItemFailedException(i, e);
                }
                if (firstInvalid == null) firstInvalid = e;
                out.add(pricingApplier.price(h.pricingName(), h.supplierOrganizationId(), h.gross(),
                        h.currency(), customerId, null, orderId));
            } catch (com.waylo.packagebookingservice.exception.PricingUnavailableException e) {
                throw new com.waylo.packagebookingservice.exception.PackageItemFailedException(i, e);
            }
        }
        if (promo != null) {
            throw new com.waylo.packagebookingservice.exception.PackageItemFailedException(0, firstInvalid);
        }
        return out;
    }

    private static String missingField(com.waylo.packagebookingservice.dto.PackageOrderCreateRequest.Item i) {
        return switch (i.vertical()) {
            case HOTEL -> i.quoteId() == null ? "quoteId" : null;
            case CAR -> i.pickupBranchId() == null ? "pickupBranchId" : i.returnBranchId() == null ? "returnBranchId"
                    : blank(i.category()) ? "category" : blank(i.transmission()) ? "transmission"
                    : i.pickupAt() == null ? "pickupAt" : i.returnAt() == null ? "returnAt"
                    : i.driverAge() == null ? "driverAge" : null;
            case FLIGHT -> blank(i.offerId()) ? "offerId" : blank(i.passengerFirstName()) ? "passengerFirstName"
                    : blank(i.passengerLastName()) ? "passengerLastName" : null;
            case BUS -> i.tripId() == null ? "tripId" : i.fromSeq() == null ? "fromSeq" : i.toSeq() == null ? "toSeq"
                    : i.passengers() == null || i.passengers().isEmpty() ? "passengers" : null;
        };
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private Held takeHold(UUID user, UUID orderId, int index, com.waylo.packagebookingservice.dto.PackageOrderCreateRequest.Item r) {
        String key = "pk-" + UUID.nameUUIDFromBytes((orderId + ":" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        switch (r.vertical()) {
            case HOTEL -> {
                var hold = hotelHoldClient.hold(new HotelHoldClient.HoldRequest(r.quoteId(), key, user, orderId)).data();
                return new Held(OrderItemVertical.HOTEL, "OTEL", hold.organizationId(), hold.totalAmount(), hold.currency(),
                        hold.expiresAt(), hold.holdId(), item -> {
                            item.setProductRef(hold.roomTypeId());
                            item.setDescription(hold.checkIn() + "-" + hold.checkOut() + ", " + hold.nights() + " gece, "
                                    + hold.rooms() + " oda");
                            item.setCheckIn(hold.checkIn());
                            item.setCheckOut(hold.checkOut());
                            policyResolver.applyHotel(item, hold.cancellationPolicy());
                        });
            }
            case CAR -> {
                var hold = carHoldClient.hold(new CarHoldClient.HoldRequest(r.pickupBranchId(), r.returnBranchId(), r.category(),
                        r.transmission(), r.pickupAt(), r.returnAt(), r.driverAge(), user, key, orderId)).data();
                return new Held(OrderItemVertical.CAR, "ARAÇ", hold.organizationId(), hold.totalAmount(), hold.currency(),
                        hold.expiresAt(), hold.holdId(), item -> {
                            item.setProductRef(hold.pickupBranchId());
                            String transmission = "AUTOMATIC".equalsIgnoreCase(hold.transmission()) ? "Otomatik" : "Manuel";
                            item.setDescription(hold.exampleModel() + " (" + carCategoryLabel(hold.category()) + ", " + transmission
                                    + "), " + hold.chargeableDays() + " gun");
                            item.setPickupAt(hold.pickupAt());
                            item.setReturnAt(hold.returnAt());
                            policyResolver.applyConfigured(item);
                        });
            }
            case BUS -> {
                List<BusHoldClient.Passenger> passengers = r.passengers().stream()
                        .map(p -> new BusHoldClient.Passenger(p.seatNo(), p.name().trim(), p.gender(), p.identityNumber())).toList();
                var hold = busHoldClient.hold(new BusHoldClient.HoldRequest(r.tripId(), r.fromSeq(), r.toSeq(), passengers, user,
                        key, orderId.toString())).data();
                return new Held(OrderItemVertical.BUS, "OTOBÜS", hold.organizationId(), hold.totalAmount(), hold.currency(),
                        hold.expiresAt(), hold.holdId(), item -> {
                            item.setProductRef(hold.tripId());
                            item.setDescription(busDescription(hold));
                            item.setPickupAt(hold.boardingAt());
                            item.setReturnAt(hold.arrivalAt());
                            policyResolver.applyConfigured(item);
                        });
            }
            case FLIGHT -> {
                if (flightSellerOrganizationId == null) {
                    throw new BusinessRuleException("FLIGHT_SELLER_NOT_CONFIGURED",
                            "Flight sales are not open: no seller organization is configured");
                }
                String seat = blank(r.seatNumber()) ? null : r.seatNumber().trim().toUpperCase();
                var hold = flightHoldClient.hold(new FlightHoldClient.HoldRequest(r.offerId(), seat, r.passengerFirstName().trim(),
                        r.passengerLastName().trim(), user, key, orderId.toString())).data();
                return new Held(OrderItemVertical.FLIGHT, "UÇUŞ", flightSellerOrganizationId, hold.totalAmount(), hold.currency(),
                        hold.expiresAt(), hold.holdId(), item -> {
                            item.setProductRef(UUID.nameUUIDFromBytes(("flight:" + hold.flightNumber()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                            item.setDescription(flightDescription(hold));
                            applyFlight(item, hold);
                            policyResolver.applyConfigured(item);
                        });
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private void releaseHoldQuietly(OrderItemVertical vertical, UUID holdId) {
        try {
            switch (vertical) {
                case HOTEL -> hotelHoldClient.release(holdId);
                case CAR -> carHoldClient.release(holdId);
                case BUS -> busHoldClient.release(holdId);
                case FLIGHT -> flightHoldClient.release(holdId);
            }
        } catch (RuntimeException e) {
            log.error("Could not release {} hold {}; expiry scheduler will reclaim it", vertical, holdId, e);
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

    private static PaymentEscrowClient.PolicyDto policyOf(OrderItem item) {
        return new PaymentEscrowClient.PolicyDto(item.getCancellationPolicy(), item.getCancelFreeHoursBefore(),
                CancellationPolicyResolver.serviceStart(item));
    }

    @Override
    @Transactional(readOnly = true)
    public com.waylo.packagebookingservice.dto.CancellationPreviewResponse cancellationPreview(UUID actorUserId, UUID orderId) {
        requireIdentity(actorUserId);
        Order order = findOrThrow(orderId);
        requireOwner(actorUserId, order);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        String blocked = null;
        List<com.waylo.packagebookingservice.dto.CancellationPreviewResponse.Item> out = new java.util.ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED) {
            blocked = order.getStatus() == OrderStatus.CANCELLED ? "ALREADY_CANCELLED" : "TERMINAL_STATE";
        }
        for (OrderItem item : items) {
            BigDecimal charged = item.getNetAmount() != null ? item.getNetAmount() : item.getSubtotal();
            String outcome = "UNPAID";
            BigDecimal refund = BigDecimal.ZERO.setScale(2);
            if (blocked != null) {
                outcome = item.getCancelOutcome() == null ? "NONE" : item.getCancelOutcome();
                refund = item.getRefundAmount() == null ? refund : item.getRefundAmount();
            } else if (order.getStatus() == OrderStatus.CONFIRMED) {
                var p = paymentEscrowClient.preview(item.getId(), policyOf(item)).data();
                if (!p.cancellable()) {
                    blocked = "ALREADY_USED";
                    outcome = "NONE";
                } else {
                    outcome = p.outcome();
                    refund = p.refundAmount();
                }
            }
            total = total.add(refund);
            out.add(new com.waylo.packagebookingservice.dto.CancellationPreviewResponse.Item(item.getId(), item.getDescription(),
                    item.getCancellationPolicy(), item.getCancelFreeHoursBefore(), outcome, charged, refund));
        }
        if (blocked != null && order.getStatus() == OrderStatus.CONFIRMED) {
            total = BigDecimal.ZERO.setScale(2);
        }
        return new com.waylo.packagebookingservice.dto.CancellationPreviewResponse(orderId, blocked == null, blocked,
                order.getCurrency(), total, out);
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

        // Paid order (CONFIRMED): decide every item from its stored policy snapshot. Check first that no item has left escrow
        // (redeemed or no-show) so a rejected cancel never leaves some items already settled. If payment-service is down
        // this throws 503 and the order stays unchanged.
        boolean allRefunded = true;
        java.util.Set<UUID> alreadySettled = new java.util.HashSet<>(); // refunded at confirm, hold already released
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            orderItemRepository.findByOrderId(order.getId()).stream().filter(i -> i.getCancelOutcome() != null)
                    .forEach(i -> alreadySettled.add(i.getId()));
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem item : items) {
                var preview = paymentEscrowClient.preview(item.getId(), policyOf(item)).data();
                if (preview != null && !preview.cancellable()) {
                    throw new BusinessRuleException("ESCROW_ALREADY_RELEASED",
                            "An item of this order was already used or settled, the order cannot be cancelled");
                }
            }
            for (OrderItem item : items) {
                var r = paymentEscrowClient.cancel(item.getId(), policyOf(item)).data();
                boolean refund = "REFUND".equals(r.outcome());
                allRefunded &= refund;
                item.setCancelOutcome(r.outcome());
                item.setRefundAmount(r.refundAmount());
                orderItemRepository.save(item);
            }
        }

        // Release hotel holds: if hotel-service is down this throws 503 and the order stays unchanged.
        for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            if (alreadySettled.contains(item.getId())) continue;
            if (item.getVertical() == OrderItemVertical.HOTEL && item.getFulfillmentRef() != null) {
                try {
                    hotelHoldClient.release(item.getFulfillmentRef());
                } catch (HotelRejectedException alreadyGone) {
                    log.info("Hold {} not releasable ({}), continuing cancel", item.getFulfillmentRef(),
                            alreadyGone.getCode());
                }
            } else if (item.getVertical() == OrderItemVertical.CAR && item.getFulfillmentRef() != null) {
                carHoldClient.release(item.getFulfillmentRef());
            } else if (item.getVertical() == OrderItemVertical.BUS && item.getFulfillmentRef() != null) {
                busHoldClient.release(item.getFulfillmentRef());
            } else if (item.getVertical() == OrderItemVertical.FLIGHT && item.getFulfillmentRef() != null) {
                flightHoldClient.release(item.getFulfillmentRef());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.saveAndFlush(order);
        if (allRefunded) pricingApplier.release(order.getId()); // promo is consumed when nothing was refunded
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
            voucherService.createForItems(items.stream().filter(i -> !"REFUND".equals(i.getCancelOutcome())).toList()); // idempotent
            return toInternal(order, items);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("ORDER_NOT_PAYABLE", "Order is " + order.getStatus() + ", it cannot be marked paid");
        }
        // Hold confirm is idempotent. Single item: a rejection propagates (422) and payment-service cancels the provider
        // payment and rolls back. Package (N > 1): a failed item is refunded alone and the rest is kept
        // (PACKAGE-ORDER-CONTRACT.md section 4); if every item fails the first error propagates and all is rolled back.
        List<OrderItem> confirmed = new java.util.ArrayList<>();
        RuntimeException firstFailure = null;
        for (OrderItem item : items) {
            try {
                confirmHold(item);
                confirmed.add(item);
            } catch (RuntimeException e) {
                if (items.size() == 1) throw e;
                log.error("Confirm of {} hold {} failed after payment, item will be refunded", item.getVertical(), item.getFulfillmentRef(), e);
                if (firstFailure == null) firstFailure = e;
                item.setCancelOutcome("REFUND");
                item.setRefundAmount(item.getNetAmount() != null ? item.getNetAmount() : item.getSubtotal());
                orderItemRepository.save(item);
                releaseHoldQuietly(item.getVertical(), item.getFulfillmentRef());
            }
        }
        if (confirmed.isEmpty() && firstFailure != null) throw firstFailure;
        order.setStatus(OrderStatus.CONFIRMED);
        order = orderRepository.saveAndFlush(order);
        voucherService.createForItems(confirmed);
        return toInternal(order, items);
    }

    private void confirmHold(OrderItem item) {
        if (item.getFulfillmentRef() == null) return;
        switch (item.getVertical()) {
            case HOTEL -> hotelHoldClient.confirm(item.getFulfillmentRef());
            case CAR -> carHoldClient.confirm(item.getFulfillmentRef());
            case BUS -> busHoldClient.confirm(item.getFulfillmentRef());
            case FLIGHT -> {
                // confirm issues the ticket (PNR + e-ticket number), copy them onto the item
                applyFlight(item, flightHoldClient.confirm(item.getFulfillmentRef()).data());
                orderItemRepository.save(item);
            }
        }
    }

    private InternalOrderResponse toInternal(Order order, List<OrderItem> items) {
        return new InternalOrderResponse(order.getId(), order.getCustomerId(), order.getStatus().name(), order.getCurrency(),
                order.getTotalAmount(),
                items.stream().map(i -> new InternalOrderResponse.Item(i.getId(), i.getVertical().name(),
                        i.getSupplierOrganizationId(), i.getSubtotal(), i.getCurrency(),
                        i.getGrossAmount() != null ? i.getGrossAmount() : i.getSubtotal(),
                        i.getDiscountAmount() != null ? i.getDiscountAmount() : BigDecimal.ZERO,
                        i.getDiscountFundedBy())).toList(),
                order.getStatus() == OrderStatus.CONFIRMED
                        ? items.stream().filter(i -> "REFUND".equals(i.getCancelOutcome())).map(OrderItem::getId).toList()
                        : List.of());
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

    /** Turkish label for a car-rental-service CarCategory name (shown to customers on orders and vouchers). */
    static String carCategoryLabel(String category) {
        if (category == null) return "";
        return switch (category) {
            case "ECONOMY" -> "Ekonomi";
            case "COMPACT" -> "Kompakt";
            case "MIDSIZE" -> "Orta";
            case "FULLSIZE" -> "Buyuk";
            case "SUV" -> "SUV";
            case "MINIVAN" -> "Minivan";
            case "VAN" -> "Van";
            case "LUXURY" -> "Luks";
            default -> category;
        };
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
                item.getCreatedAt(),
                item.getPickupAt(),
                item.getReturnAt(),
                item.getVertical() == OrderItemVertical.FLIGHT
                        ? new FlightItemDetails(item.getFlightNumber(), item.getDepartureAt(), item.getSeatNumber(),
                                item.getPassengerFirstName(), item.getPassengerLastName(), item.getPnr(),
                                item.getTicketNumber(), item.getSupplier())
                        : null,
                item.getGrossAmount() != null ? item.getGrossAmount() : item.getSubtotal(),
                item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO,
                item.getDiscountFundedBy(),
                item.getNetAmount() != null ? item.getNetAmount() : item.getSubtotal(),
                item.getTaxAmount(),
                item.getPromoCode(),
                item.getCancellationPolicy(),
                item.getCancelFreeHoursBefore(),
                item.getCancelOutcome(),
                item.getRefundAmount()
        );
    }
}
