package com.waylo.carrentalservice.repository;

import com.waylo.carrentalservice.domain.Vehicle;
import com.waylo.carrentalservice.domain.VehicleStatus;
import com.waylo.carrentalservice.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    boolean existsByPlateNumber(String plateNumber);

    List<Vehicle> findByLocation(String location);

    List<Vehicle> findByVehicleType(VehicleType vehicleType);

    List<Vehicle> findByStatus(VehicleStatus status);

    List<Vehicle> findByLocationAndStatus(String location, VehicleStatus status);

    List<Vehicle> findByLocationAndVehicleTypeAndStatus(String location, VehicleType vehicleType, VehicleStatus status);

    List<Vehicle> findByDailyRateLessThanEqual(BigDecimal maxRate);

    List<Vehicle> findByActiveTrue();

    long countByLocationAndStatus(String location, VehicleStatus status);
}
