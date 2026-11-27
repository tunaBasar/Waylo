package com.waylo.hotelservice.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelRateRuleRepository extends JpaRepository<HotelRateRule, UUID> {

    List<HotelRateRule> findByHotelId(UUID hotelId);

    List<HotelRateRule> findByHotelIdAndActiveTrue(UUID hotelId);

    List<HotelRateRule> findByHotelIdAndRoomTypeIdAndRatePlanIdAndActiveTrue(UUID hotelId, UUID roomTypeId, UUID ratePlanId);

    Optional<HotelRateRule> findByIdAndHotelId(UUID id, UUID hotelId);

    @Query("SELECT DISTINCT r.hotelId FROM HotelRateRule r WHERE r.active = true")
    List<UUID> findHotelIdsWithActiveRules();
}
