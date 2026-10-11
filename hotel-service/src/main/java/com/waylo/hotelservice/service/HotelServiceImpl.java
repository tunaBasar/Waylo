package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.dto.HotelMapper;
import com.waylo.hotelservice.dto.HotelRequest;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
public class HotelServiceImpl implements HotelService {

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;

    public HotelServiceImpl(HotelRepository hotelRepository, HotelMapper hotelMapper) {
        this.hotelRepository = hotelRepository;
        this.hotelMapper = hotelMapper;
    }

    @Override
    public HotelResponse create(HotelRequest request) {
        Hotel hotel = hotelMapper.toEntity(request);
        hotel = hotelRepository.save(hotel);
        return hotelMapper.toResponse(hotel);
    }

    @Override
    @Transactional(readOnly = true)
    public HotelResponse getById(UUID id) {
        Hotel hotel = findHotelOrThrow(id);
        return hotelMapper.toResponse(hotel);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotelResponse> getAll() {
        return hotelRepository.findAll().stream()
                .map(hotelMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotelResponse> search(String city, String country, Integer minStars) {
        Stream<Hotel> stream;
        if (city != null && country != null) {
            stream = hotelRepository.findByCityAndCountry(city, country).stream();
        } else if (city != null) {
            stream = hotelRepository.findByCity(city).stream();
        } else if (country != null) {
            stream = hotelRepository.findByCountry(country).stream();
        } else {
            stream = hotelRepository.findAll().stream();
        }
        if (minStars != null) {
            stream = stream.filter(h -> h.getStarRating() >= minStars);
        }
        return stream.map(hotelMapper::toResponse).toList();
    }

    @Override
    public HotelResponse update(UUID id, HotelRequest request) {
        Hotel hotel = findHotelOrThrow(id);
        hotelMapper.updateEntity(request, hotel);
        hotel = hotelRepository.save(hotel);
        return hotelMapper.toResponse(hotel);
    }

    @Override
    public void delete(UUID id) {
        if (!hotelRepository.existsById(id)) {
            throw new ResourceNotFoundException("Hotel", "id", id);
        }
        hotelRepository.deleteById(id);
    }

    @Override
    public HotelResponse deactivate(UUID id) {
        Hotel hotel = findHotelOrThrow(id);
        hotel.setActive(false);
        hotel = hotelRepository.save(hotel);
        return hotelMapper.toResponse(hotel);
    }

    @Override
    public HotelResponse activate(UUID id) {
        Hotel hotel = findHotelOrThrow(id);
        hotel.setActive(true);
        hotel = hotelRepository.save(hotel);
        return hotelMapper.toResponse(hotel);
    }

    private Hotel findHotelOrThrow(UUID id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", id));
    }
}
