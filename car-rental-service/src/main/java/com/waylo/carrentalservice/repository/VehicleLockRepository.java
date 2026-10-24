package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.Vehicle;
import com.waylo.carrentalservice.domain.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleLockRepository extends JpaRepository<Vehicle, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select v from Vehicle v
            where v.active = true and v.status <> :excluded
              and not exists (
                select 1 from Rental r
                where r.vehicleId = v.id and r.status = com.waylo.carrentalservice.domain.RentalStatus.RESERVED
                  and r.pickupDate < :to and r.returnDate > :from)
            order by v.brand, v.model
            """)
    List<Vehicle> findAvailable(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                @Param("excluded") VehicleStatus excluded);
}
