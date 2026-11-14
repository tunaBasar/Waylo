package com.waylo.hotelservice.service;

import com.waylo.hotelservice.client.AccessDecision;
import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.client.OrderItemClient;
import com.waylo.hotelservice.client.ReviewEligibility;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelHold;
import com.waylo.hotelservice.domain.HotelReview;
import com.waylo.hotelservice.domain.ReviewModerationEvent;
import com.waylo.hotelservice.domain.ReviewStatus;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.dto.ReviewRequest;
import com.waylo.hotelservice.dto.ReviewResponse;
import com.waylo.hotelservice.dto.ReviewStats;
import com.waylo.hotelservice.dto.ReviewableItemResponse;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.AuthenticationRequiredException;
import com.waylo.hotelservice.exception.AuthorizationUnavailableException;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.DuplicateResourceException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelHoldRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.ReviewModerationEventRepository;
import com.waylo.hotelservice.repository.ReviewRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Verified hotel reviews (RM-29). Stay eligibility is decided by package-booking-service (it owns orders and
 * vouchers). This service adds the hotel-side rules: staff of the hotel's organization cannot review it,
 * one review per order item, and the PUBLISHED-only aggregate.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviews;
    private final ReviewModerationEventRepository moderationEvents;
    private final OrderItemClient orderItems;
    private final HotelHoldRepository holds;
    private final HotelRepository hotels;
    private final AuthAccessClient authAccess;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, ReviewModerationEventRepository moderationEvents,
                         OrderItemClient orderItems, HotelHoldRepository holds, HotelRepository hotels,
                         AuthAccessClient authAccess, Clock clock) {
        this.reviews = reviews;
        this.moderationEvents = moderationEvents;
        this.orderItems = orderItems;
        this.holds = holds;
        this.hotels = hotels;
        this.authAccess = authAccess;
        this.clock = clock;
    }

    @Transactional
    public ReviewResponse submit(UUID userId, ReviewRequest request) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        ReviewEligibility eligibility = fetchEligibility(request.orderItemId(), userId);
        if (!eligibility.eligible()) {
            String reason = eligibility.reason() == null ? "NOT_ELIGIBLE" : eligibility.reason();
            if ("NOT_OWNER".equals(reason)) throw new AccessDeniedException("Order item does not belong to you");
            throw new BusinessRuleException("REVIEW_" + reason, "This stay cannot be reviewed yet: " + reason);
        }
        Hotel hotel = hotelOf(eligibility);
        if (hotel.getOrganizationId() != null && isMember(userId, hotel.getOrganizationId())) {
            throw new AccessDeniedException("Members of the hotel's organization cannot review it", "ORG_MEMBER_CANNOT_REVIEW");
        }
        if (reviews.existsByOrderItemId(request.orderItemId())) {
            throw new DuplicateResourceException("Review", "orderItemId", request.orderItemId());
        }
        HotelReview review = new HotelReview(hotel.getId(), request.orderItemId(), userId, request.score(),
                normalize(request.text()), Instant.now(clock));
        return ReviewResponse.fullView(reviews.saveAndFlush(review));
    }

    @Transactional(readOnly = true)
    public ReviewResponse.Page listPublic(UUID hotelId, int page, int size) {
        if (!hotels.existsById(hotelId)) throw new ResourceNotFoundException("Hotel", "id", hotelId);
        Page<HotelReview> result = reviews.findByHotelIdAndStatus(hotelId, ReviewStatus.PUBLISHED, pageable(page, size));
        ReviewStats stats = statsOf(hotelId);
        return new ReviewResponse.Page(result.getContent().stream().map(ReviewResponse::publicView).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), stats.averageScore(), stats.count());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listOwn(UUID userId) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        return reviews.findByCustomerIdOrderByCreatedAtDesc(userId).stream().map(ReviewResponse::fullView).toList();
    }

    /** Eligible items come from package-booking-service, already reviewed ones are removed here. */
    @Transactional(readOnly = true)
    public List<ReviewableItemResponse> listReviewable(UUID userId) {
        if (userId == null) throw new AuthenticationRequiredException("Authentication required");
        List<ReviewEligibility> eligible;
        try {
            eligible = orderItems.reviewEligible(userId).data();
        } catch (RuntimeException e) {
            log.warn("Order service unavailable while listing reviewable items");
            throw new AuthorizationUnavailableException("Order service unavailable");
        }
        if (eligible == null || eligible.isEmpty()) return List.of();
        Set<UUID> done = Set.copyOf(reviews.findExistingOrderItemIds(
                eligible.stream().map(ReviewEligibility::orderItemId).toList()));
        return eligible.stream().filter(e -> !done.contains(e.orderItemId())).map(e -> {
            Hotel hotel = hotelOf(e);
            if (hotel.getOrganizationId() != null && isMember(userId, hotel.getOrganizationId())) return null;
            return new ReviewableItemResponse(e.orderItemId(), hotel.getId(), hotel.getName(), e.description(),
                    e.checkIn(), e.checkOut());
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse.Page listForOrganization(UUID orgId, int page, int size) {
        Page<HotelReview> result = reviews.findByOrganization(orgId, pageable(page, size));
        return new ReviewResponse.Page(result.getContent().stream().map(ReviewResponse::fullView).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), null, 0);
    }

    /** The caller must already be authorized for the review's hotel (controller guard). The score is never touched. */
    @Transactional
    public ReviewResponse respond(UUID reviewId, UUID actorId, String text) {
        HotelReview review = find(reviewId);
        review.respond(text.trim(), actorId, Instant.now(clock));
        return ReviewResponse.fullView(reviews.save(review));
    }

    public UUID hotelIdOf(UUID reviewId) {
        return find(reviewId).getHotelId();
    }

    public UUID organizationIdOf(UUID reviewId) {
        return hotels.findById(find(reviewId).getHotelId()).map(Hotel::getOrganizationId).orElse(null);
    }

    @Transactional
    public ReviewResponse moderate(String role, UUID actorId, UUID reviewId, ReviewStatus status, String reason) {
        if (!"ADMIN".equals(role) || actorId == null) throw new AccessDeniedException("Admin only");
        if (reason == null || reason.isBlank()) throw new BusinessRuleException("REASON_REQUIRED", "Moderation reason is required");
        HotelReview review = find(reviewId);
        ReviewStatus from = review.getStatus();
        Instant now = Instant.now(clock);
        review.moderate(status, reason.trim(), actorId, now);
        moderationEvents.save(new ReviewModerationEvent(reviewId, from, status, reason.trim(), actorId, now));
        return ReviewResponse.fullView(reviews.save(review));
    }

    @Transactional(readOnly = true)
    public ReviewResponse.Page listAdmin(String role, ReviewStatus status, int page, int size) {
        if (!"ADMIN".equals(role)) throw new AccessDeniedException("Admin only");
        Page<HotelReview> result = status == null ? reviews.findAll(pageable(page, size))
                : reviews.findByStatus(status, pageable(page, size));
        return new ReviewResponse.Page(result.getContent().stream().map(ReviewResponse::fullView).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), null, 0);
    }

    // --- aggregate -------------------------------------------------------------------------------------------

    public HotelResponse enrich(HotelResponse hotel) {
        ReviewStats s = statsOf(hotel.id());
        return hotel.withReviews(s.averageScore(), s.count());
    }

    public List<HotelResponse> enrich(List<HotelResponse> list) {
        if (list.isEmpty()) return list;
        Map<UUID, ReviewStats> byHotel = new HashMap<>();
        reviews.statsFor(list.stream().map(HotelResponse::id).collect(Collectors.toSet()))
                .forEach(s -> byHotel.put(s.hotelId(), s));
        return list.stream().map(h -> {
            ReviewStats s = byHotel.get(h.id());
            return s == null ? h : h.withReviews(s.averageScore(), s.count());
        }).toList();
    }

    private ReviewStats statsOf(UUID hotelId) {
        return reviews.statsFor(List.of(hotelId)).stream().findFirst().orElse(new ReviewStats(hotelId, 0L, 0L));
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private ReviewEligibility fetchEligibility(UUID orderItemId, UUID userId) {
        try {
            return orderItems.reviewEligibility(orderItemId, userId).data();
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("OrderItem", "id", orderItemId);
        } catch (RuntimeException e) {
            log.warn("Order service unavailable while checking review eligibility");
            throw new AuthorizationUnavailableException("Order service unavailable");
        }
    }

    private Hotel hotelOf(ReviewEligibility e) {
        UUID holdId = e.fulfillmentRef();
        HotelHold hold = holdId == null ? null : holds.findById(holdId).orElse(null);
        if (hold == null) throw new BusinessRuleException("REVIEW_HOTEL_UNKNOWN", "Hotel of this stay could not be resolved");
        return hotels.findById(hold.getHotelId())
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hold.getHotelId()));
    }

    /** Fails closed: an auth outage blocks the review rather than letting staff review their own hotel. */
    private boolean isMember(UUID userId, UUID organizationId) {
        try {
            AccessDecision d = authAccess.check(userId, organizationId, null, "HOTEL_READ");
            return d != null && d.allowed();
        } catch (RuntimeException e) {
            log.warn("Authorization service unavailable while checking membership");
            throw new AuthorizationUnavailableException("Authorization service unavailable");
        }
    }

    private HotelReview find(UUID id) {
        return reviews.findById(id).orElseThrow(() -> new ResourceNotFoundException("Review", "id", id));
    }

    static String normalize(String text) {
        if (text == null) return null;
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }
}
