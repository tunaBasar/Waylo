package com.waylo.pricingservice.repository;

import com.waylo.pricingservice.domain.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByCode(String code);

    List<PromoCode> findByCampaignId(UUID campaignId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT pc FROM PromoCode pc
        WHERE pc.code = :code
        """)
    Optional<PromoCode> findByCodeWithLock(@Param("code") String code);

    @Modifying
    @Query("""
        UPDATE PromoCode pc SET pc.usesCount = pc.usesCount + 1
        WHERE pc.code = :code AND pc.usesCount < COALESCE(pc.maxUsesTotal, 2147483647)
        """)
    int incrementUsage(@Param("code") String code);

    @Modifying
    @Query("""
        UPDATE PromoCode pc SET pc.usesCount = pc.usesCount + 1
        WHERE pc.code = :code AND pc.usesCount < COALESCE(pc.maxUsesTotal, 2147483647)
        """)
    int incrementUsageWithLock(@Param("code") String code);
}
