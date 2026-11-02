package com.waylo.pricingservice.repository;

import com.waylo.pricingservice.domain.PromoReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PromoReservationRepository extends JpaRepository<PromoReservation, UUID> {

    Optional<PromoReservation> findByOrderId(UUID orderId);

    long countByPromoCodeId(UUID promoCodeId);

    @Query("""
        SELECT COUNT(pr) FROM PromoReservation pr
        WHERE pr.status = 'RESERVED'
        AND pr.promoCode.campaign.id = :campaignId
        """)
    long countActiveByCampaignId(@Param("campaignId") UUID campaignId);

    @Query("""
        SELECT COUNT(pr) FROM PromoReservation pr
        WHERE pr.status = 'RESERVED'
        AND pr.customerId = :customerId
        AND pr.promoCode.id = :promoCodeId
        """)
    long countByCustomerIdAndPromoCodeId(
            @Param("customerId") String customerId,
            @Param("promoCodeId") UUID promoCodeId);
}
