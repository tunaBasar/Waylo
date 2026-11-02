package com.waylo.pricingservice.repository;

import com.waylo.pricingservice.domain.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    @Query("""
        SELECT t FROM TaxRate t
        WHERE t.vertical = :vertical
          AND t.validFrom <= CURRENT_TIMESTAMP
          AND (t.scopeOrgId IS NULL OR t.scopeOrgId = :orgId)
        ORDER BY t.scopeOrgId DESC NULLS LAST
        """)
    List<TaxRate> findActiveForVerticalAndOrg(
            @Param("vertical") String vertical,
            @Param("orgId") UUID orgId
    );
}
