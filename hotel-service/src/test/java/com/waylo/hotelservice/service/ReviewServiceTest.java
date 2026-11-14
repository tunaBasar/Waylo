package com.waylo.hotelservice.service;

import com.waylo.hotelservice.client.AccessDecision;
import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.client.OrderItemClient;
import com.waylo.hotelservice.client.ReviewEligibility;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelHold;
import com.waylo.hotelservice.domain.HotelReview;
import com.waylo.hotelservice.domain.ReviewStatus;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.ReviewRequest;
import com.waylo.hotelservice.dto.ReviewResponse;
import com.waylo.hotelservice.dto.ReviewStats;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.DuplicateResourceException;
import com.waylo.hotelservice.repository.HotelHoldRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.ReviewModerationEventRepository;
import com.waylo.hotelservice.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ReviewModerationEventRepository events = mock(ReviewModerationEventRepository.class);
    private final OrderItemClient orderItems = mock(OrderItemClient.class);
    private final HotelHoldRepository holds = mock(HotelHoldRepository.class);
    private final HotelRepository hotels = mock(HotelRepository.class);
    private final AuthAccessClient auth = mock(AuthAccessClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);
    private final ReviewService service = new ReviewService(reviews, events, orderItems, holds, hotels, auth, clock);

    private final UUID user = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID holdId = UUID.randomUUID();
    private final UUID hotelId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Hotel hotel = new Hotel();
        hotel.setId(hotelId);
        hotel.setName("H");
        hotel.setOrganizationId(orgId);
        when(hotels.findById(hotelId)).thenReturn(Optional.of(hotel));
        HotelHold hold = mock(HotelHold.class);
        when(hold.getHotelId()).thenReturn(hotelId);
        when(holds.findById(holdId)).thenReturn(Optional.of(hold));
        when(auth.check(any(), any(), any(), any())).thenReturn(new AccessDecision(false, List.of()));
        when(reviews.saveAndFlush(any(HotelReview.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void eligibility(boolean ok, String reason) {
        when(orderItems.reviewEligibility(item, user)).thenReturn(ApiResponse.of(
                new ReviewEligibility(item, ok, reason, user, holdId, orgId, "d", null, null)));
    }

    @Test
    void eligibleGuestCreatesReviewWithTrimmedText() {
        eligibility(true, null);
        ReviewResponse r = service.submit(user, new ReviewRequest(item, 5, "  great  "));
        assertThat(r.text()).isEqualTo("great");
        assertThat(r.score()).isEqualTo(5);
        assertThat(r.hotelId()).isEqualTo(hotelId);
        assertThat(r.status()).isEqualTo("PUBLISHED");
    }

    @Test
    void blankTextBecomesNull() {
        eligibility(true, null);
        assertThat(service.submit(user, new ReviewRequest(item, 3, "   ")).text()).isNull();
    }

    @Test
    void notCheckedInIsRejected() {
        eligibility(false, "NOT_CHECKED_IN");
        assertThatThrownBy(() -> service.submit(user, new ReviewRequest(item, 4, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("NOT_CHECKED_IN");
    }

    @Test
    void foreignItemIsForbidden() {
        eligibility(false, "NOT_OWNER");
        assertThatThrownBy(() -> service.submit(user, new ReviewRequest(item, 4, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void organizationMemberIsRejected() {
        eligibility(true, null);
        when(auth.check(user, orgId, null, "HOTEL_READ")).thenReturn(new AccessDecision(true, List.of("HOTEL_STAFF")));
        assertThatThrownBy(() -> service.submit(user, new ReviewRequest(item, 4, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateIsConflict() {
        eligibility(true, null);
        when(reviews.existsByOrderItemId(item)).thenReturn(true);
        assertThatThrownBy(() -> service.submit(user, new ReviewRequest(item, 4, null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void moderationRequiresAdminAndReasonAndStoresAudit() {
        UUID admin = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        HotelReview review = new HotelReview(hotelId, item, user, 2, "bad", Instant.now(clock));
        when(reviews.findById(id)).thenReturn(Optional.of(review));
        when(reviews.save(any(HotelReview.class))).thenAnswer(i -> i.getArgument(0));
        assertThatThrownBy(() -> service.moderate("CUSTOMER", admin, id, ReviewStatus.HIDDEN, "x"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.moderate("ADMIN", admin, id, ReviewStatus.HIDDEN, " "))
                .isInstanceOf(BusinessRuleException.class);
        ReviewResponse r = service.moderate("ADMIN", admin, id, ReviewStatus.HIDDEN, "spam");
        assertThat(r.status()).isEqualTo("HIDDEN");
        assertThat(review.getModeratedBy()).isEqualTo(admin);
        assertThat(review.getModerationReason()).isEqualTo("spam");
        ArgumentCaptor<com.waylo.hotelservice.domain.ReviewModerationEvent> ev =
                ArgumentCaptor.forClass(com.waylo.hotelservice.domain.ReviewModerationEvent.class);
        verify(events).save(ev.capture());
        assertThat(ev.getValue().getFromStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(ev.getValue().getToStatus()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void responseDoesNotChangeScore() {
        UUID id = UUID.randomUUID();
        HotelReview review = new HotelReview(hotelId, item, user, 2, "bad", Instant.now(clock));
        when(reviews.findById(id)).thenReturn(Optional.of(review));
        when(reviews.save(any(HotelReview.class))).thenAnswer(i -> i.getArgument(0));
        service.respond(id, UUID.randomUUID(), " sorry ");
        assertThat(review.getScore()).isEqualTo(2);
        assertThat(review.getPartnerResponse()).isEqualTo("sorry");
        assertThat(review.getRespondedAt()).isNotNull();
    }

    @Test
    void aggregateIsNullWithoutReviewsAndRoundedOtherwise() {
        assertThat(new ReviewStats(hotelId, 0L, 0L).averageScore()).isNull();
        assertThat(new ReviewStats(hotelId, 3L, 13L).averageScore()).isEqualTo(4.3);
    }

    @Test
    void enrichUsesPublishedStatsOnly() {
        when(reviews.statsFor(any())).thenReturn(List.of(new ReviewStats(hotelId, 2L, 9L)));
        var plain = new com.waylo.hotelservice.dto.HotelResponse(hotelId, orgId, "H", "c", "t", "a", 3, null, null, null,
                true, 0, null, null);
        var empty = new com.waylo.hotelservice.dto.HotelResponse(UUID.randomUUID(), orgId, "E", "c", "t", "a", 3, null,
                null, null, true, 0, null, null);
        var out = service.enrich(List.of(plain, empty));
        assertThat(out.get(0).averageScore()).isEqualTo(4.5);
        assertThat(out.get(0).reviewCount()).isEqualTo(2);
        assertThat(out.get(1).averageScore()).isNull();
        assertThat(out.get(1).reviewCount()).isZero();
    }
}
