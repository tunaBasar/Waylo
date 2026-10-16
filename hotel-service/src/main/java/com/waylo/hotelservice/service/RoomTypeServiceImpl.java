package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.dto.RoomTypeCreateRequest;
import com.waylo.hotelservice.dto.RoomTypeResponse;
import com.waylo.hotelservice.dto.RoomTypeUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.DuplicateResourceException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import com.waylo.hotelservice.repository.HotelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class RoomTypeServiceImpl implements RoomTypeService {

    private static final Logger log = LoggerFactory.getLogger(RoomTypeServiceImpl.class);

    private static final int MAX_ROOM_TYPES = 50;

    private final HotelRoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;

    public RoomTypeServiceImpl(HotelRoomTypeRepository roomTypeRepository, HotelRepository hotelRepository) {
        this.roomTypeRepository = roomTypeRepository;
        this.hotelRepository = hotelRepository;
    }

    private void ensureHotelExists(UUID hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", "id", hotelId);
        }
    }

    private void ensureOccupancyRule(int maxAdults, int maxChildren, int maxOccupancy) {
        if (maxAdults > maxOccupancy || maxOccupancy > maxAdults + maxChildren) {
            throw new BusinessRuleException("OCCUPANCY_RULE",
                    "maxOccupancy must be between maxAdults (" + maxAdults + ") and maxAdults + maxChildren (" + (maxAdults + maxChildren) + ")");
        }
    }

    private RoomTypeResponse toResponse(HotelRoomType entity) {
        return new RoomTypeResponse(
                entity.getId(),
                entity.getHotelId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getMaxAdults(),
                entity.getMaxChildren(),
                entity.getMaxOccupancy(),
                entity.getSizeSqm(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    @Override
    public RoomTypeResponse create(UUID hotelId, RoomTypeCreateRequest request) {
        ensureHotelExists(hotelId);

        roomTypeRepository.lockHotelById(hotelId);

        if (roomTypeRepository.countByHotelId(hotelId) >= MAX_ROOM_TYPES) {
            throw new BusinessRuleException("LIMIT_REACHED",
                    "Maximum of " + MAX_ROOM_TYPES + " room types per hotel reached");
        }

        if (roomTypeRepository.existsByHotelIdAndCode(hotelId, request.code())) {
            throw new DuplicateResourceException("RoomType", "code", request.code());
        }

        ensureOccupancyRule(request.maxAdults(), request.maxChildren(), request.maxOccupancy());

        HotelRoomType entity = new HotelRoomType();
        entity.setHotelId(hotelId);
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setMaxAdults(request.maxAdults());
        entity.setMaxChildren(request.maxChildren());
        entity.setMaxOccupancy(request.maxOccupancy());
        entity.setSizeSqm(request.sizeSqm());
        entity.setStatus(CatalogStatus.ACTIVE);

        entity = roomTypeRepository.saveAndFlush(entity);
        log.info("Room type created: id={}, hotelId={}", entity.getId(), hotelId);
        return toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomTypeResponse getById(UUID hotelId, UUID roomTypeId, boolean includeInactive) {
        ensureHotelExists(hotelId);

        HotelRoomType entity = roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", roomTypeId));

        if (!includeInactive && entity.getStatus() != CatalogStatus.ACTIVE) {
            throw new ResourceNotFoundException("RoomType", "id", roomTypeId);
        }

        return toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomTypeResponse> getByHotelId(UUID hotelId, boolean includeInactive) {
        ensureHotelExists(hotelId);

        List<HotelRoomType> entities;
        if (includeInactive) {
            entities = roomTypeRepository.findByHotelIdOrderByNameAsc(hotelId);
        } else {
            entities = roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE);
        }
        return entities.stream().map(this::toResponse).toList();
    }

    @Override
    public RoomTypeResponse update(UUID hotelId, UUID roomTypeId, RoomTypeUpdateRequest request) {
        ensureHotelExists(hotelId);

        HotelRoomType entity = roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("RoomType", "id", roomTypeId));

        // Handle expectedVersion check
        if (request.expectedVersion() != null && !Objects.equals(request.expectedVersion(), entity.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(HotelRoomType.class, roomTypeId);
        }

        // Apply partial updates (null = unchanged)
        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.maxAdults() != null) {
            entity.setMaxAdults(request.maxAdults());
        }
        if (request.maxChildren() != null) {
            entity.setMaxChildren(request.maxChildren());
        }
        if (request.maxOccupancy() != null) {
            entity.setMaxOccupancy(request.maxOccupancy());
        }
        if (request.sizeSqm() != null) {
            entity.setSizeSqm(request.sizeSqm());
        }
        if (request.status() != null) {
            entity.setStatus(request.status());
        }

        // Re-validate occupancy with updated values
        ensureOccupancyRule(entity.getMaxAdults(), entity.getMaxChildren(), entity.getMaxOccupancy());

        entity = roomTypeRepository.saveAndFlush(entity);
        log.info("Room type updated: id={}, hotelId={}", roomTypeId, hotelId);
        return toResponse(entity);
    }
}
