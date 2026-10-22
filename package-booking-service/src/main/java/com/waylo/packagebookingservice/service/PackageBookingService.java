package com.waylo.packagebookingservice.service;

import com.waylo.packagebookingservice.domain.BookingStatus;
import com.waylo.packagebookingservice.dto.PackageBookingRequest;
import com.waylo.packagebookingservice.dto.PackageBookingResponse;

import java.util.List;
import java.util.UUID;

public interface PackageBookingService {

    PackageBookingResponse create(PackageBookingRequest request);

    PackageBookingResponse getById(UUID id);

    List<PackageBookingResponse> getAll();

    List<PackageBookingResponse> getByCustomerId(UUID customerId);

    List<PackageBookingResponse> getByFlightId(UUID flightId);

    List<PackageBookingResponse> search(UUID customerId, BookingStatus status);

    PackageBookingResponse update(UUID id, PackageBookingRequest request);

    void delete(UUID id);

    PackageBookingResponse updateStatus(UUID id, BookingStatus status);

    PackageBookingResponse cancel(UUID id);
}
