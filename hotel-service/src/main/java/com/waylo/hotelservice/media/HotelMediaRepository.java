package com.waylo.hotelservice.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HotelMediaRepository extends JpaRepository<HotelMedia, UUID>, JpaSpecificationExecutor<HotelMedia> {

    long countByHotelId(UUID hotelId);

    List<HotelMedia> findByHotelIdOrderBySortOrderAscCreatedAtAsc(UUID hotelId);

    List<HotelMedia> findByHotelIdAndScanStatusAndReviewStatusOrderBySortOrderAscCreatedAtAsc(
            UUID hotelId, HotelMedia.ScanStatus scan, HotelMedia.ReviewStatus review);

    Optional<HotelMedia> findByIdAndHotelId(UUID id, UUID hotelId);

    Optional<HotelMedia> findByIdAndHotelIdAndOrganizationId(UUID id, UUID hotelId, UUID organizationId);

    /** One row per hotel: the first CLEAN + APPROVED photo (sort_order, created_at, id) of PUBLISHED hotels only. */
    @Query(value = """
            SELECT DISTINCT ON (m.hotel_id) m.hotel_id AS hotelId, m.id AS mediaId
              FROM hotel_media m
              JOIN hotels h ON h.id = m.hotel_id
             WHERE m.hotel_id IN (:hotelIds)
               AND m.deleted_at IS NULL
               AND m.scan_status = 'CLEAN'
               AND m.review_status = 'APPROVED'
               AND h.publication_status = 'PUBLISHED'
             ORDER BY m.hotel_id, m.sort_order, m.created_at, m.id
            """, nativeQuery = true)
    List<CoverRow> findCovers(@Param("hotelIds") Collection<UUID> hotelIds);

    interface CoverRow {
        UUID getHotelId();

        UUID getMediaId();
    }
}
