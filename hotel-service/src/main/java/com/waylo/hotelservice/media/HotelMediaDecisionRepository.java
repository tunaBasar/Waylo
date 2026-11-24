package com.waylo.hotelservice.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelMediaDecisionRepository extends JpaRepository<HotelMediaDecision, UUID> {

    List<HotelMediaDecision> findByMediaIdOrderByCreatedAtDesc(UUID mediaId);
}
