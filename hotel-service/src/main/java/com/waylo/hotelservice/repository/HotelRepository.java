package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.Hotel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, UUID> {

    List<Hotel> findByCity(String city);

    List<Hotel> findByCityAndCountry(String city, String country);

    List<Hotel> findByCountry(String country);

    List<Hotel> findByStarRatingGreaterThanEqual(int starRating);

    List<Hotel> findByActiveTrue();

    List<Hotel> findByCityAndActiveTrue(String city);

    List<Hotel> findByOrganizationId(UUID organizationId);

    boolean existsByNameAndAddress(String name, String address);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Hotel h WHERE h.id = :hotelId")
    Optional<Hotel> findByIdForUpdate(@Param("hotelId") UUID hotelId);
}
