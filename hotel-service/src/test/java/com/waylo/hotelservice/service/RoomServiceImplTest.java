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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomMapper roomMapper;

    @InjectMocks
    private RoomServiceImpl roomService;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private Hotel testHotel;

    @BeforeEach
    void setUp() {
        testHotel = new Hotel();
        testHotel.setId(hotelId);
        testHotel.setName("Grand Hotel");
        testHotel.setCity("Istanbul");
        testHotel.setCountry("Turkey");
        testHotel.setAddress("123 Main St");
        testHotel.setStarRating(4);
        testHotel.setActive(true);
    }

    private Room buildRoom(UUID id, String roomNumber, RoomType type) {
        Room room = new Room();
        room.setId(id);
        room.setRoomNumber(roomNumber);
        room.setRoomType(type);
        room.setStatus(RoomStatus.AVAILABLE);
        room.setCapacity(2);
        room.setPricePerNight(BigDecimal.valueOf(100));
        room.setFloor(1);
        room.setDescription("A nice room");
        room.setHotel(testHotel);
        return room;
    }

    private RoomResponse buildRoomResponse(UUID id) {
        return new RoomResponse(
                id, hotelId, "Grand Hotel", "101",
                RoomType.DOUBLE, RoomStatus.AVAILABLE,
                2, BigDecimal.valueOf(100), 1,
                "A nice room", null, null
        );
    }

    @Test
    void create_hotelMissing_throwsException() {
        RoomRequest request = new RoomRequest("101", RoomType.DOUBLE, 2, BigDecimal.valueOf(100), 1, "desc", null);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.create(hotelId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel not found with id");

        verify(roomRepository, never()).save(any());
    }

    @Test
    void create_duplicateRoomNumber_throwsException() {
        RoomRequest request = new RoomRequest("101", RoomType.DOUBLE, 2, BigDecimal.valueOf(100), 1, "desc", null);
        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(testHotel));
        when(roomRepository.existsByHotelIdAndRoomNumber(hotelId, "101")).thenReturn(true);

        assertThatThrownBy(() -> roomService.create(hotelId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Room already exists with roomNumber");
    }

    @Test
    void create_success_setsHotelAndDefaultStatus() {
        RoomRequest request = new RoomRequest("101", RoomType.DOUBLE, 2, BigDecimal.valueOf(100), 1, "desc", null);
        Room savedRoom = buildRoom(roomId, "101", RoomType.DOUBLE);
        RoomResponse response = buildRoomResponse(roomId);

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(testHotel));
        when(roomRepository.existsByHotelIdAndRoomNumber(hotelId, "101")).thenReturn(false);
        when(roomMapper.toEntity(request)).thenReturn(new Room());
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);
        when(roomMapper.toResponse(savedRoom)).thenReturn(response);

        RoomResponse result = roomService.create(hotelId, request);

        assertThat(result).isEqualTo(response);
        assertThat(savedRoom.getHotel()).isEqualTo(testHotel);
        assertThat(savedRoom.getStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    void create_statusProvided_keepsStatus() {
        RoomRequest request = new RoomRequest("101", RoomType.DOUBLE, 2, BigDecimal.valueOf(100), 1, "desc", RoomStatus.RESERVED);
        Room savedRoom = buildRoom(roomId, "101", RoomType.DOUBLE);
        savedRoom.setStatus(RoomStatus.RESERVED);
        RoomResponse response = new RoomResponse(
                roomId, hotelId, "Grand Hotel", "101",
                RoomType.DOUBLE, RoomStatus.RESERVED,
                2, BigDecimal.valueOf(100), 1,
                "desc", null, null
        );

        when(hotelRepository.findById(hotelId)).thenReturn(Optional.of(testHotel));
        when(roomRepository.existsByHotelIdAndRoomNumber(hotelId, "101")).thenReturn(false);
        when(roomMapper.toEntity(request)).thenReturn(new Room());
        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);
        when(roomMapper.toResponse(savedRoom)).thenReturn(response);

        RoomResponse result = roomService.create(hotelId, request);

        assertThat(result.status()).isEqualTo(RoomStatus.RESERVED);
    }

    @Test
    void getById_found_returnsResponse() {
        Room room = buildRoom(roomId, "101", RoomType.DOUBLE);
        RoomResponse response = buildRoomResponse(roomId);

        when(roomRepository.findByIdAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomMapper.toResponse(room)).thenReturn(response);

        RoomResponse result = roomService.getById(hotelId, roomId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getById_notFound_throwsException() {
        when(roomRepository.findByIdAndHotelId(roomId, hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getById(hotelId, roomId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room not found with id");
    }

    @Test
    void getById_crossHotelSecurity_check() {
        UUID otherHotelId = UUID.randomUUID();
        when(roomRepository.findByIdAndHotelId(roomId, otherHotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getById(otherHotelId, roomId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room not found with id");
    }

    @Test
    void getByHotelId_success() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomRepository.findByHotelId(hotelId)).thenReturn(List.of(buildRoom(roomId, "101", RoomType.DOUBLE)));
        when(roomMapper.toResponse(any(Room.class))).thenReturn(buildRoomResponse(roomId));

        List<RoomResponse> result = roomService.getByHotelId(hotelId);

        assertThat(result).hasSize(1);
    }

    @Test
    void getByHotelId_hotelMissing_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> roomService.getByHotelId(hotelId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel not found with id");
    }

    @Test
    void search_noFilters() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomRepository.findByHotelId(hotelId)).thenReturn(List.of(buildRoom(roomId, "101", RoomType.DOUBLE)));
        when(roomMapper.toResponse(any(Room.class))).thenReturn(buildRoomResponse(roomId));

        List<RoomResponse> result = roomService.search(hotelId, null, null, null);

        assertThat(result).hasSize(1);
        verify(roomRepository).findByHotelId(hotelId);
    }

    @Test
    void search_roomTypeOnly() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomRepository.findByHotelIdAndRoomType(hotelId, RoomType.SINGLE)).thenReturn(List.of());

        List<RoomResponse> result = roomService.search(hotelId, RoomType.SINGLE, null, null);

        assertThat(result).isEmpty();
        verify(roomRepository).findByHotelIdAndRoomType(hotelId, RoomType.SINGLE);
    }

    @Test
    void search_statusOnly() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomRepository.findByHotelIdAndStatus(hotelId, RoomStatus.RESERVED)).thenReturn(List.of());

        List<RoomResponse> result = roomService.search(hotelId, null, RoomStatus.RESERVED, null);

        assertThat(result).isEmpty();
        verify(roomRepository).findByHotelIdAndStatus(hotelId, RoomStatus.RESERVED);
    }

    @Test
    void search_roomTypeAndStatus() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomRepository.findByHotelIdAndRoomTypeAndStatus(hotelId, RoomType.SUITE, RoomStatus.OCCUPIED)).thenReturn(List.of());

        List<RoomResponse> result = roomService.search(hotelId, RoomType.SUITE, RoomStatus.OCCUPIED, null);

        assertThat(result).isEmpty();
        verify(roomRepository).findByHotelIdAndRoomTypeAndStatus(hotelId, RoomType.SUITE, RoomStatus.OCCUPIED);
    }

    @Test
    void search_withMaxPrice_filter() {
        Room room = buildRoom(roomId, "101", RoomType.DOUBLE);
        room.setPricePerNight(BigDecimal.valueOf(50));
        RoomResponse response = new RoomResponse(
                roomId, hotelId, "Grand Hotel", "101",
                RoomType.DOUBLE, RoomStatus.AVAILABLE,
                2, BigDecimal.valueOf(50), 1,
                "desc", null, null
        );
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomRepository.findByHotelId(hotelId)).thenReturn(List.of(room));
        when(roomMapper.toResponse(room)).thenReturn(response);

        List<RoomResponse> result = roomService.search(hotelId, null, null, BigDecimal.valueOf(100));

        assertThat(result).hasSize(1);
    }

    @Test
    void search_hotelMissing_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> roomService.search(hotelId, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel not found with id");
    }

    @Test
    void update_duplicateRoomNumber_throwsException() {
        Room room = buildRoom(roomId, "101", RoomType.DOUBLE);
        RoomRequest request = new RoomRequest("201", RoomType.DOUBLE, 2, BigDecimal.valueOf(100), 1, "desc", null);
        when(roomRepository.findByIdAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomRepository.existsByHotelIdAndRoomNumberAndIdNot(hotelId, "201", roomId)).thenReturn(true);

        assertThatThrownBy(() -> roomService.update(hotelId, roomId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Room already exists with roomNumber");
    }

    @Test
    void update_success() {
        Room room = buildRoom(roomId, "101", RoomType.DOUBLE);
        RoomRequest request = new RoomRequest("102", RoomType.DOUBLE, 2, BigDecimal.valueOf(120), 2, "desc", null);
        Room updatedRoom = buildRoom(roomId, "102", RoomType.DOUBLE);
        updatedRoom.setPricePerNight(BigDecimal.valueOf(120));
        RoomResponse response = new RoomResponse(
                roomId, hotelId, "Grand Hotel", "102",
                RoomType.DOUBLE, RoomStatus.AVAILABLE,
                2, BigDecimal.valueOf(120), 2,
                "desc", null, null
        );

        when(roomRepository.findByIdAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomRepository.existsByHotelIdAndRoomNumberAndIdNot(hotelId, "102", roomId)).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenReturn(updatedRoom);
        when(roomMapper.toResponse(updatedRoom)).thenReturn(response);

        RoomResponse result = roomService.update(hotelId, roomId, request);

        assertThat(result).isEqualTo(response);
        verify(roomMapper).updateEntity(any(RoomRequest.class), any(Room.class));
    }

    @Test
    void update_roomNumberNotChanged_doesNotCheckDuplicate() {
        Room room = buildRoom(roomId, "101", RoomType.DOUBLE);
        RoomRequest request = new RoomRequest("101", RoomType.SUITE, 3, BigDecimal.valueOf(150), 3, "desc", null);
        Room updatedRoom = buildRoom(roomId, "101", RoomType.SUITE);
        updatedRoom.setRoomType(RoomType.SUITE);
        updatedRoom.setCapacity(3);
        updatedRoom.setPricePerNight(BigDecimal.valueOf(150));
        RoomResponse response = new RoomResponse(
                roomId, hotelId, "Grand Hotel", "101",
                RoomType.SUITE, RoomStatus.AVAILABLE,
                3, BigDecimal.valueOf(150), 3,
                "desc", null, null
        );

        when(roomRepository.findByIdAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(updatedRoom);
        when(roomMapper.toResponse(updatedRoom)).thenReturn(response);

        RoomResponse result = roomService.update(hotelId, roomId, request);

        assertThat(result).isEqualTo(response);
        // existsByHotelIdAndRoomNumberAndIdNot should NOT be called
        verify(roomRepository, never()).existsByHotelIdAndRoomNumberAndIdNot(any(), any(), any());
    }

    @Test
    void delete_success() {
        Room room = buildRoom(roomId, "101", RoomType.DOUBLE);

        when(roomRepository.findByIdAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));

        roomService.delete(hotelId, roomId);

        verify(roomRepository).delete(room);
    }

    @Test
    void delete_notFound_throwsException() {
        when(roomRepository.findByIdAndHotelId(roomId, hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.delete(hotelId, roomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_success() {
        Room room = buildRoom(roomId, "101", RoomType.DOUBLE);
        RoomResponse response = new RoomResponse(
                roomId, hotelId, "Grand Hotel", "101",
                RoomType.DOUBLE, RoomStatus.OCCUPIED,
                2, BigDecimal.valueOf(100), 1,
                "desc", null, null
        );

        when(roomRepository.findByIdAndHotelId(roomId, hotelId)).thenReturn(Optional.of(room));
        when(roomRepository.save(any(Room.class))).thenReturn(room);
        when(roomMapper.toResponse(room)).thenReturn(response);

        RoomResponse result = roomService.updateStatus(hotelId, roomId, RoomStatus.OCCUPIED);

        assertThat(result.status()).isEqualTo(RoomStatus.OCCUPIED);
        verify(roomRepository).save(room);
    }

    @Test
    void getAvailableRoomCount_success() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomRepository.countByHotelIdAndStatus(hotelId, RoomStatus.AVAILABLE)).thenReturn(10L);

        long count = roomService.getAvailableRoomCount(hotelId);

        assertThat(count).isEqualTo(10);
    }

    @Test
    void getAvailableRoomCount_hotelMissing_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> roomService.getAvailableRoomCount(hotelId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel not found with id");
    }
}
