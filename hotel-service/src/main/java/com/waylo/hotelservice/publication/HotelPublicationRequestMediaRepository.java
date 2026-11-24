package com.waylo.hotelservice.publication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HotelPublicationRequestMediaRepository
        extends JpaRepository<HotelPublicationRequestMedia, HotelPublicationRequestMedia.Pk> {

    List<HotelPublicationRequestMedia> findByRequestId(java.util.UUID requestId);
}
