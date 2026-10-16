package com.waylo.hotelservice.service;

import com.waylo.hotelservice.client.AuthAccessClient;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.dto.HotelMapper;
import com.waylo.hotelservice.dto.HotelRequest;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
public class HotelServiceImpl implements HotelService {

    private static final Logger log = LoggerFactory.getLogger(HotelServiceImpl.class);

    private final HotelRepository hotelRepository;
    private final HotelMapper hotelMapper;
    private final AuthAccessClient authAccessClient;

    public HotelServiceImpl(HotelRepository hotelRepository, HotelMapper hotelMapper, AuthAccessClient authAccessClient) {
        this.hotelRepository = hotelRepository;
        this.hotelMapper = hotelMapper;
        this.authAccessClient = authAccessClient;
    }

    @Override
    public HotelResponse create(HotelRequest request) {
        Hotel hotel = hotelMapper.toEntity(request);
        hotel = hotelRepository.save(hotel);
        return hotelMapper.toResponse(hotel);
    }

    /**
     * Unfiltered lookup by ID. Intentionally NOT subject to the public projection below:
     * auth-service's RoleGrantService calls this endpoint service-to-service to verify a
     * hotel's organizationId while granting roles during PENDING approval, before the org
     * (and therefore the hotel) would ever appear in the public listing. Filtering this method
     * would break that internal verification flow for PENDING organizations.
     */
    @Override
    @Transactional(readOnly = true)
    public HotelResponse getById(UUID id) {
        Hotel hotel = findHotelOrThrow(id);
        return hotelMapper.toResponse(hotel);
    }

    /**
     * Public, unauthenticated browse projection: excludes inactive hotels and hotels whose
     * organization is not ACTIVE (PENDING/SUSPENDED), while legacy hotels with no owning
     * organization remain visible (read-only elsewhere, not hidden here). See filterPublic().
     */
    @Override
    @Transactional(readOnly = true)
    public List<HotelResponse> getAll() {
        return filterPublic(hotelRepository.findAll()).stream()
                .map(hotelMapper::toResponse)
                .toList();
    }

    /**
     * Sehir ve/veya ulkeye gore otel arama. Same public projection as getAll().
     */
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
        List<Hotel> visible = filterPublic(stream.toList());
        Stream<Hotel> result = visible.stream();
        if (minStars != null) {
            result = result.filter(h -> h.getStarRating() >= minStars);
        }
        return result.map(hotelMapper::toResponse).toList();
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

    /**
     * Management projection scoped to one organization (partner dashboard), intentionally NOT
     * subject to the public projection: an organization's own owner/manager must see their
     * draft/inactive hotels and hotels while their organization is still PENDING approval.
     */
    @Override
    @Transactional(readOnly = true)
    public List<HotelResponse> getByOrganization(UUID organizationId) {
        return hotelRepository.findByOrganizationId(organizationId).stream()
                .map(hotelMapper::toResponse)
                .toList();
    }

    /**
     * Filters a candidate list down to the public projection: active hotels only, and for
     * hotels with a non-null organizationId, only those whose organization is currently ACTIVE.
     * Legacy hotels (organizationId == null) pass through unfiltered by organization status.
     * Fails closed on auth-service unavailability: organization-owned hotels are hidden rather
     * than shown, so a transient outage degrades to "legacy hotels only" instead of leaking
     * hotels of a possibly-suspended organization.
     */
    private List<Hotel> filterPublic(List<Hotel> hotels) {
        List<Hotel> activeOnly = hotels.stream().filter(Hotel::isActive).toList();

        List<Hotel> legacy = new ArrayList<>();
        List<Hotel> orgOwned = new ArrayList<>();
        for (Hotel hotel : activeOnly) {
            if (hotel.getOrganizationId() == null) {
                legacy.add(hotel);
            } else {
                orgOwned.add(hotel);
            }
        }

        if (orgOwned.isEmpty()) {
            return legacy;
        }

        List<UUID> activeOrgIdList;
        try {
            activeOrgIdList = authAccessClient.activeOrganizationIds();
        } catch (RuntimeException e) {
            log.warn("Failed to fetch active organization ids for public hotel projection, hiding organization-owned hotels: {}", e.getMessage());
            activeOrgIdList = null;
        }

        if (activeOrgIdList == null) {
            return legacy;
        }

        Set<UUID> activeOrgIds = new HashSet<>(activeOrgIdList);
        List<Hotel> result = new ArrayList<>(legacy);
        for (Hotel hotel : orgOwned) {
            if (activeOrgIds.contains(hotel.getOrganizationId())) {
                result.add(hotel);
            }
        }
        return result;
    }

    private Hotel findHotelOrThrow(UUID id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", id));
    }
}
