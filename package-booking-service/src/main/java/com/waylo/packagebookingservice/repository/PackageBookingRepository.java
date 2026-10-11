package com.waylo.packagebookingservice.repository;

import com.waylo.packagebookingservice.domain.BookingStatus;
import com.waylo.packagebookingservice.domain.PackageBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PackageBookingRepository extends JpaRepository<PackageBooking, UUID> {

    List<PackageBooking> findByCustomerId(UUID customerId);

    List<PackageBooking> findByStatus(BookingStatus status);

    List<PackageBooking> findByCustomerIdAndStatus(UUID customerId, BookingStatus status);

    List<PackageBooking> findByFlightId(UUID flightId);

    List<PackageBooking> findByHotelReservationId(UUID hotelReservationId);

    List<PackageBooking> findByVehicleReservationId(UUID vehicleReservationId);

    long countByCustomerIdAndStatus(UUID customerId, BookingStatus status);
}
