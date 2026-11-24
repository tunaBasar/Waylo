package com.waylo.hotelservice.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HotelPublicationRequestRepository extends JpaRepository<HotelPublicationRequest, UUID>, JpaSpecificationExecutor<HotelPublicationRequest> {

    boolean existsByHotelId(UUID hotelId);


    Optional<HotelPublicationRequest> findByIdAndHotelId(UUID id, UUID hotelId);

    List<HotelPublicationRequest> findByHotelIdOrderBySubmittedAtDesc(UUID hotelId);

    boolean existsByHotelIdAndStatus(UUID hotelId, HotelPublicationRequest.Status status);

    boolean existsByRevisionId(UUID revisionId);

    List<HotelPublicationRequest> findByStatusOrderBySubmittedAtAsc(HotelPublicationRequest.Status status);
}
