package com.waylo.hotelservice.dto;

import com.waylo.hotelservice.domain.HotelReview;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Review views. No view exposes the customer id or name: hotel-service holds no user names, so the public
 * list shows no reviewer identity (allowed by the task: "else none").
 */
public record ReviewResponse(UUID id, UUID hotelId, int score, String text, String status, String partnerResponse,
                             Instant respondedAt, Instant createdAt, UUID orderItemId, String moderationReason,
                             Instant moderatedAt) {

    public static ReviewResponse publicView(HotelReview r) {
        return new ReviewResponse(r.getId(), r.getHotelId(), r.getScore(), r.getText(), null, r.getPartnerResponse(),
                r.getRespondedAt(), r.getCreatedAt(), null, null, null);
    }

    /** Own, partner and admin view. */
    public static ReviewResponse fullView(HotelReview r) {
        return new ReviewResponse(r.getId(), r.getHotelId(), r.getScore(), r.getText(), r.getStatus().name(),
                r.getPartnerResponse(), r.getRespondedAt(), r.getCreatedAt(), r.getOrderItemId(),
                r.getModerationReason(), r.getModeratedAt());
    }
    public record Page(List<ReviewResponse> items, int page, int size, long total, Double averageScore, long reviewCount) {}
}
