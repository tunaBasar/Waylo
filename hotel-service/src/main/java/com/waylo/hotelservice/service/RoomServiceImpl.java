package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.Room;
import com.waylo.hotelservice.domain.RoomStatus;
import com.waylo.hotelservice.domain.RoomType;
import com.waylo.hotelservice.dto.RoomMapper;
import com.waylo.hotelservice.dto.RoomRequest;
import com.waylo.hotelservice.dto.RoomResponse;
import com.waylo.hotelservice.exception.DuplicateResourceException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
public class RoomServiceImpl implements RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomServiceImpl.class);

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    public RoomServiceImpl(RoomRepository roomRepository, HotelRepository hotelRepository, RoomMapper roomMapper) {
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
        this.roomMapper = roomMapper;
    }

    @Override
    public RoomResponse create(UUID hotelId, RoomRequest request) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel", "id", hotelId));
        if (roomRepository.existsByHotelIdAndRoomNumber(hotelId, request.roomNumber())) {
            throw new DuplicateResourceException("Room", "roomNumber", request.roomNumber());
        }
        Room room = roomMapper.toEntity(request);
        room.setHotel(hotel);
        if (room.getStatus() == null) {
            room.setStatus(RoomStatus.AVAILABLE);
        }
        room = roomRepository.save(room);
        log.info("Room created: roomId={}, hotelId={}", room.getId(), hotelId);
        return roomMapper.toResponse(room);
    }

    private Room findRoomOrThrow(UUID hotelId, UUID roomId) {
        return roomRepository.findByIdAndHotelId(roomId, hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));
    }

    private void ensureHotelExists(UUID hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel", "id", hotelId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public RoomResponse getById(UUID hotelId, UUID roomId) {
        Room room = findRoomOrThrow(hotelId, roomId);
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getByHotelId(UUID hotelId) {
        ensureHotelExists(hotelId);
        return roomRepository.findByHotelId(hotelId).stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> search(UUID hotelId, RoomType roomType, RoomStatus status, BigDecimal maxPrice) {
        ensureHotelExists(hotelId);
        Stream<Room> stream;
        if (roomType != null && status != null) {
            stream = roomRepository.findByHotelIdAndRoomTypeAndStatus(hotelId, roomType, status).stream();
        } else if (roomType != null) {
            stream = roomRepository.findByHotelIdAndRoomType(hotelId, roomType).stream();
        } else if (status != null) {
            stream = roomRepository.findByHotelIdAndStatus(hotelId, status).stream();
        } else {
            stream = roomRepository.findByHotelId(hotelId).stream();
        }
        if (maxPrice != null) {
            stream = stream.filter(r -> r.getPricePerNight().compareTo(maxPrice) <= 0);
        }
        return stream.map(roomMapper::toResponse).toList();
    }

    @Override
    public RoomResponse update(UUID hotelId, UUID roomId, RoomRequest request) {
        Room room = findRoomOrThrow(hotelId, roomId);
        if (request.roomNumber() != null && !request.roomNumber().equals(room.getRoomNumber())
                && roomRepository.existsByHotelIdAndRoomNumberAndIdNot(hotelId, request.roomNumber(), roomId)) {
            throw new DuplicateResourceException("Room", "roomNumber", request.roomNumber());
        }
        roomMapper.updateEntity(request, room);
        room = roomRepository.save(room);
        log.info("Room updated: roomId={}, hotelId={}", roomId, hotelId);
        return roomMapper.toResponse(room);
    }

    @Override
    public void delete(UUID hotelId, UUID roomId) {
        Room room = findRoomOrThrow(hotelId, roomId);
        roomRepository.delete(room);
        log.info("Room deleted: roomId={}, hotelId={}", roomId, hotelId);
    }

    @Override
    public RoomResponse updateStatus(UUID hotelId, UUID roomId, RoomStatus status) {
        Room room = findRoomOrThrow(hotelId, roomId);
        room.setStatus(status);
        room = roomRepository.save(room);
        log.info("Room status updated: roomId={}, hotelId={}, status={}", roomId, hotelId, status);
        return roomMapper.toResponse(room);
    }

    @Override
    @Transactional(readOnly = true)
    public long getAvailableRoomCount(UUID hotelId) {
        ensureHotelExists(hotelId);
        return roomRepository.countByHotelIdAndStatus(hotelId, RoomStatus.AVAILABLE);
    }
}
