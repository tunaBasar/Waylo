package com.waylo.hotelservice.repository;

import com.waylo.hotelservice.domain.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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
}
