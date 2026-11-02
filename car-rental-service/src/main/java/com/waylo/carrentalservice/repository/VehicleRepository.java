package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.CarCategory;
import com.waylo.carrentalservice.domain.Transmission;
import com.waylo.carrentalservice.domain.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByOrganizationIdOrderByPlateNumber(UUID organizationId);

    List<Vehicle> findAllByOrderByOrganizationIdAscPlateNumberAsc();

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    List<Vehicle> findByOrganizationIdAndActiveTrueOrderById(UUID organizationId);

    /** SELECT ... FOR UPDATE on candidate units, ordered by id so concurrent holds lock in the same order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.organizationId = :org and v.category = :category "
            + "and v.transmission = :transmission and v.active = true order by v.id")
    List<Vehicle> lockCandidates(@Param("org") UUID organizationId, @Param("category") CarCategory category,
                                 @Param("transmission") Transmission transmission);
}
