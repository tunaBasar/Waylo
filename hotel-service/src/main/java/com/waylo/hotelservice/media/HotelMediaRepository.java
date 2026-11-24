package com.waylo.hotelservice.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
}
