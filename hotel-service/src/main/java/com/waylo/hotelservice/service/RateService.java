package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.DailyRateResponse;
import com.waylo.hotelservice.dto.HotelCurrencyResponse;
import com.waylo.hotelservice.dto.RateUpdateRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RateService {

    List<DailyRateResponse> getRates(UUID hotelId, LocalDate from, LocalDate to,
                                      UUID roomTypeId, UUID ratePlanId);

    List<DailyRateResponse> updateRates(UUID hotelId, RateUpdateRequest request);

    /**
     * Returns the single currency used across all daily rates for this hotel,
     * or null if no rates exist yet.
     */
    HotelCurrencyResponse getHotelCurrency(UUID hotelId);
}
