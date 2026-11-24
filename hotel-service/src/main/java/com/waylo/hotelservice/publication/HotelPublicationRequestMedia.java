package com.waylo.hotelservice.publication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable link between a publication request and a photo it asks the admin to decide (V13). */
@Entity
@Table(name = "hotel_publication_request_media")
@IdClass(HotelPublicationRequestMedia.Pk.class)
public class HotelPublicationRequestMedia {

    public static class Pk implements Serializable {
        private UUID requestId;
        private UUID mediaId;

        public Pk() { }

        public Pk(UUID requestId, UUID mediaId) {
            this.requestId = requestId;
            this.mediaId = mediaId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Pk p && Objects.equals(requestId, p.requestId) && Objects.equals(mediaId, p.mediaId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, mediaId);
        }
    }

    @Id
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Id
    @Column(name = "media_id", nullable = false, updatable = false)
    private UUID mediaId;

    @Column(name = "hotel_id", nullable = false, updatable = false)
    private UUID hotelId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public HotelPublicationRequestMedia() { }

    public HotelPublicationRequestMedia(UUID requestId, UUID mediaId, UUID hotelId, UUID organizationId) {
        this.requestId = requestId;
        this.mediaId = mediaId;
        this.hotelId = hotelId;
        this.organizationId = organizationId;
    }

    public UUID getRequestId() { return requestId; }
    public UUID getMediaId() { return mediaId; }
    public UUID getHotelId() { return hotelId; }
    public UUID getOrganizationId() { return organizationId; }
    public Instant getCreatedAt() { return createdAt; }
}
