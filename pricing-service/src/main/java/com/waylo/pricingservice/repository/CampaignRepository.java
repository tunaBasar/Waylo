package com.waylo.pricingservice.repository;

import com.waylo.pricingservice.domain.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    @Query("""
        SELECT c FROM Campaign c
        WHERE c.vertical = :vertical
          AND (c.scopeOrgId IS NULL OR c.scopeOrgId = :orgId)
          AND c.startAt <= :now
          AND c.endAt > :now
        ORDER BY c.scopeOrgId DESC NULLS LAST, c.createdAt DESC
        """)
    List<Campaign> findActiveForVerticalAndOrg(
            @Param("vertical") String vertical,
            @Param("orgId") UUID orgId,
            @Param("now") java.time.LocalDateTime now
    );

    @Query("""
        SELECT c FROM Campaign c
        WHERE c.vertical = :vertical
          AND c.scopeOrgId IS NULL
          AND c.startAt <= :now
          AND c.endAt > :now
        ORDER BY c.createdAt DESC
        """)
    List<Campaign> findActivePlatformForVertical(
            @Param("vertical") String vertical,
            @Param("now") java.time.LocalDateTime now
    );

    @Query("""
        SELECT c FROM Campaign c
        LEFT JOIN PromoCode pc ON pc.campaign = c AND pc.code = :code
        WHERE pc.code = :code
        """)
    Optional<Campaign> findCampaignByPromoCode(@Param("code") String code);

    List<Campaign> findByScopeOrgIdIsNullOrderByCreatedAtDesc();
}
