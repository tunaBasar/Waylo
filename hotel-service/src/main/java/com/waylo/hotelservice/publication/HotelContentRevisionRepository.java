package com.waylo.hotelservice.publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface HotelContentRevisionRepository extends JpaRepository<HotelContentRevision, UUID> {

    boolean existsByHotelId(UUID hotelId);


    Optional<HotelContentRevision> findByIdAndHotelId(UUID id, UUID hotelId);

    @Query("SELECT r FROM HotelContentRevision r WHERE r.hotelId = :hotelId AND r.status IN "
            + "(com.waylo.hotelservice.publication.HotelContentRevision.Status.DRAFT, "
            + "com.waylo.hotelservice.publication.HotelContentRevision.Status.SUBMITTED)")
    Optional<HotelContentRevision> findWorking(@Param("hotelId") UUID hotelId);

    @Query("SELECT COALESCE(MAX(r.revisionNo), 0) FROM HotelContentRevision r WHERE r.hotelId = :hotelId")
    int maxRevisionNo(@Param("hotelId") UUID hotelId);
}
