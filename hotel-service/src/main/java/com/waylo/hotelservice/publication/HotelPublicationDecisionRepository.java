package com.waylo.hotelservice.publication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HotelPublicationDecisionRepository extends JpaRepository<HotelPublicationDecision, UUID> {

    boolean existsByHotelId(UUID hotelId);


    List<HotelPublicationDecision> findByRequestIdOrderByCreatedAtDesc(UUID requestId);

    List<HotelPublicationDecision> findByHotelIdOrderByCreatedAtAsc(UUID hotelId);
}
