package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.Rental;
import com.waylo.carrentalservice.domain.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RentalRepository extends JpaRepository<Rental, UUID> {

    @Query("""
            select count(r) > 0 from Rental r
            where r.vehicleId = :vehicleId and r.status = :status
              and r.pickupDate < :returnDate and r.returnDate > :pickupDate
            """)
    boolean existsOverlapping(@Param("vehicleId") UUID vehicleId,
                              @Param("status") RentalStatus status,
                              @Param("pickupDate") LocalDate pickupDate,
                              @Param("returnDate") LocalDate returnDate);

    List<Rental> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<Rental> findByVehicleIdOrderByPickupDateDesc(UUID vehicleId);
}
