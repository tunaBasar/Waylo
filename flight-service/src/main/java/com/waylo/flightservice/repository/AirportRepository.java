package com.waylo.flightservice.repository;

import com.waylo.flightservice.domain.Airport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AirportRepository extends JpaRepository<Airport, UUID> {

    Optional<Airport> findByIataCode(String iataCode);

    boolean existsByIataCode(String iataCode);

    List<Airport> findAllByOrderByIataCodeAsc();

    @Query("select count(f) > 0 from Flight f where f.departureAirport.id = :id or f.arrivalAirport.id = :id")
    boolean isReferencedByFlights(@Param("id") UUID id);
}
