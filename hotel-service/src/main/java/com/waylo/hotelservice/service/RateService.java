package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.DailyRateResponse;
import com.waylo.hotelservice.dto.RateUpdateRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RateService {

    List<DailyRateResponse> getRates(UUID hotelId, LocalDate from, LocalDate to,
                                      UUID roomTypeId, UUID ratePlanId);

    List<DailyRateResponse> updateRates(UUID hotelId, RateUpdateRequest request);
}
