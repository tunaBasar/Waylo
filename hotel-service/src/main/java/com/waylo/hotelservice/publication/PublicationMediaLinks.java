package com.waylo.hotelservice.publication;

import com.waylo.hotelservice.exception.PublicationException;
import com.waylo.hotelservice.media.HotelMedia;
import com.waylo.hotelservice.media.HotelMediaDecision;
import com.waylo.hotelservice.media.HotelMediaDecisionRepository;
import com.waylo.hotelservice.media.HotelMediaRepository;
import com.waylo.hotelservice.media.MediaDtos.MediaView;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Media phase 3: photos attached to a publication request and the admin decision cascade. Repositories only. */
@Component
public class PublicationMediaLinks {

    static final int MAX_MEDIA = 20;

    private final HotelPublicationRequestMediaRepository links;
    private final HotelMediaRepository media;
    private final HotelMediaDecisionRepository mediaDecisions;

    public PublicationMediaLinks(HotelPublicationRequestMediaRepository links, HotelMediaRepository media,
                                 HotelMediaDecisionRepository mediaDecisions) {
        this.links = links;
        this.media = media;
        this.mediaDecisions = mediaDecisions;
    }

    /** Validates the ids (400/404/409) and returns them as a safe list; nothing is written. */
    public List<UUID> validate(UUID hotelId, List<UUID> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return List.of();
        }
        if (mediaIds.size() > MAX_MEDIA) {
            throw PublicationException.validation("A request can carry at most " + MAX_MEDIA + " photos");
        }
        if (mediaIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw PublicationException.validation("mediaIds must not contain null");
        }
        if (new HashSet<>(mediaIds).size() != mediaIds.size()) {
            throw PublicationException.validation("mediaIds must not contain duplicates");
        }
        for (UUID id : mediaIds) {
            HotelMedia m = media.findByIdAndHotelId(id, hotelId).orElseThrow(() ->
                    new PublicationException(404, "MEDIA_NOT_FOUND", "Photo not found for this hotel",
                            Map.of("mediaId", id.toString())));
            if (m.getReviewStatus() != HotelMedia.ReviewStatus.PENDING) {
                throw new PublicationException(409, "MEDIA_NOT_PENDING", "Only pending photos can be attached",
                        Map.of("mediaId", id.toString(), "reviewStatus", m.getReviewStatus().name()));
            }
        }
        return List.copyOf(mediaIds);
    }

    public void link(UUID requestId, UUID hotelId, UUID organizationId, List<UUID> mediaIds) {
        if (mediaIds.isEmpty()) return;
        links.saveAll(mediaIds.stream()
                .map(id -> new HotelPublicationRequestMedia(requestId, id, hotelId, organizationId)).toList());
    }

    /** Linked non-deleted photos ordered by sort_order. */
    public List<HotelMedia> linked(UUID requestId) {
        List<UUID> ids = links.findByRequestId(requestId).stream().map(HotelPublicationRequestMedia::getMediaId).toList();
        if (ids.isEmpty()) return List.of();
        return media.findAllById(ids).stream()
                .sorted(Comparator.comparingInt(HotelMedia::getSortOrder)
                        .thenComparing(HotelMedia::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<UUID> mediaIds(UUID requestId) {
        return linked(requestId).stream().map(HotelMedia::getId).toList();
    }

    public List<MediaView> views(UUID requestId) {
        return linked(requestId).stream().map(MediaView::of).toList();
    }

    /** APPROVE cascade: every linked photo that is still PENDING becomes APPROVED/CLEAN with one decision row. */
    public void approvePending(UUID requestId, UUID adminId, Instant now) {
        for (HotelMedia m : linked(requestId)) {
            if (m.getReviewStatus() != HotelMedia.ReviewStatus.PENDING) continue;
            m.setReviewStatus(HotelMedia.ReviewStatus.APPROVED);
            m.setScanStatus(HotelMedia.ScanStatus.CLEAN);
            m.setReviewedBy(adminId);
            m.setReviewedAt(now);
            m.setReviewReason(null);
            media.save(m);
            mediaDecisions.save(new HotelMediaDecision(m, "APPROVE", "PENDING", "APPROVED", null, adminId));
        }
    }
}
