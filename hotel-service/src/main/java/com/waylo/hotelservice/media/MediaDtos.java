package com.waylo.hotelservice.media;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MediaDtos {

    private MediaDtos() { }

    /** Partner view: every own photo with its review state. */
    public record MediaView(UUID id, UUID hotelId, String caption, int sortOrder, String rightsSource, String rightsNote,
                            String contentType, long sizeBytes, int width, int height, String scanStatus,
                            String reviewStatus, String reviewReason, Instant createdAt) {
        public static MediaView of(HotelMedia m) {
            return new MediaView(m.getId(), m.getHotelId(), m.getCaption(), m.getSortOrder(), m.getRightsSource(),
                    m.getRightsNote(), m.getContentType(), m.getSizeBytes(), m.getWidth(), m.getHeight(),
                    m.getScanStatus().name(), m.getReviewStatus().name(), m.getReviewReason(), m.getCreatedAt());
        }
    }

    /** Public view: ids and captions only. The bytes come from GET /api/hotels/{id}/media/{mediaId}. */
    public record PublicMedia(UUID id, String caption, int sortOrder, int width, int height) {
        static PublicMedia of(HotelMedia m) {
            return new PublicMedia(m.getId(), m.getCaption(), m.getSortOrder(), m.getWidth(), m.getHeight());
        }
    }

    public record AdminMediaView(MediaView media, String hotelName, UUID organizationId, UUID uploadedBy,
                                 UUID reviewedBy, Instant reviewedAt, List<DecisionView> decisions) { }

    public record DecisionView(UUID id, String decision, String fromStatus, String toStatus, String reason,
                               UUID actorUserId, Instant createdAt) {
        static DecisionView of(HotelMediaDecision d) {
            return new DecisionView(d.getId(), d.getDecision(), d.getFromStatus(), d.getToStatus(), d.getReason(),
                    d.getActorUserId(), d.getCreatedAt());
        }
    }

    public record AdminMediaPage(List<AdminMediaView> items, int page, int size, long total) { }

    public record CaptionRequest(String caption) { }

    public record OrderRequest(List<UUID> ids) { }

    public record ReviewRequest(String decision, String reason) { }

    public record StoredFile(byte[] content, String contentType) { }
}
