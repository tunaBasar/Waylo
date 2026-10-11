package com.waylo.checkinservice.service;

import com.waylo.checkinservice.dto.CheckinRequest;
import com.waylo.checkinservice.dto.CheckinResponse;

import java.util.List;
import java.util.UUID;

public interface CheckinService {
    CheckinResponse checkIn(CheckinRequest request);
    CheckinResponse getById(UUID id);
    List<CheckinResponse> getAll();
    List<CheckinResponse> getByBookingId(UUID bookingId);
    List<CheckinResponse> getByFlightId(UUID flightId);
    CheckinResponse issueBoardingPass(UUID id);
    CheckinResponse cancel(UUID id);
}
