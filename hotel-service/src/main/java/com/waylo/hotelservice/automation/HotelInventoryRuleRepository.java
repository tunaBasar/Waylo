package com.waylo.hotelservice.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelInventoryRuleRepository extends JpaRepository<HotelInventoryRule, UUID> {

    List<HotelInventoryRule> findByHotelId(UUID hotelId);

    Optional<HotelInventoryRule> findByHotelIdAndRoomTypeId(UUID hotelId, UUID roomTypeId);

    @Query("SELECT DISTINCT r.hotelId FROM HotelInventoryRule r WHERE r.active = true")
    List<UUID> findHotelIdsWithActiveRules();
}
