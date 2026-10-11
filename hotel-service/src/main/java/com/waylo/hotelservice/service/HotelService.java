package com.waylo.hotelservice.service;

import com.waylo.hotelservice.dto.HotelRequest;
import com.waylo.hotelservice.dto.HotelResponse;

import java.util.List;
import java.util.UUID;

public interface HotelService {

    HotelResponse create(HotelRequest request);

    HotelResponse getById(UUID id);

    List<HotelResponse> getAll();

    /**
     * Sehir ve/veya ulkeye gore otel arama.
     */
    List<HotelResponse> search(String city, String country, Integer minStars);

    HotelResponse update(UUID id, HotelRequest request);

    void delete(UUID id);

    HotelResponse deactivate(UUID id);

    HotelResponse activate(UUID id);
}
