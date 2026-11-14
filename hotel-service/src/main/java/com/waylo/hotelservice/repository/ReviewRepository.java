package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.HotelReview;
import com.waylo.hotelservice.domain.ReviewStatus;
import com.waylo.hotelservice.dto.ReviewStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<HotelReview, UUID> {

    boolean existsByOrderItemId(UUID orderItemId);

    Page<HotelReview> findByHotelIdAndStatus(UUID hotelId, ReviewStatus status, Pageable pageable);

    List<HotelReview> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Page<HotelReview> findByStatus(ReviewStatus status, Pageable pageable);

    @Query("select r.orderItemId from HotelReview r where r.orderItemId in :ids")
    List<UUID> findExistingOrderItemIds(@Param("ids") Collection<UUID> ids);

    @Query(value = "select r from HotelReview r where r.hotelId in (select h.id from Hotel h where h.organizationId = :orgId)",
            countQuery = "select count(r) from HotelReview r where r.hotelId in (select h.id from Hotel h where h.organizationId = :orgId)")
    Page<HotelReview> findByOrganization(@Param("orgId") UUID orgId, Pageable pageable);

    /** Aggregate over PUBLISHED reviews only. Hotels without published reviews yield no row. */
    @Query("select new com.waylo.hotelservice.dto.ReviewStats(r.hotelId, count(r), sum(r.score)) from HotelReview r "
            + "where r.hotelId in :ids and r.status = com.waylo.hotelservice.domain.ReviewStatus.PUBLISHED group by r.hotelId")
    List<ReviewStats> statsFor(@Param("ids") Collection<UUID> ids);
}
