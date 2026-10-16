package com.waylo.hotelservice.service;

import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.domain.HotelRoomType;
import com.waylo.hotelservice.dto.RoomTypeCreateRequest;
import com.waylo.hotelservice.dto.RoomTypeResponse;
import com.waylo.hotelservice.dto.RoomTypeUpdateRequest;
import com.waylo.hotelservice.exception.BusinessRuleException;
import com.waylo.hotelservice.exception.DuplicateResourceException;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.repository.HotelRoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomTypeServiceImplTest {

    @Mock
    private HotelRoomTypeRepository roomTypeRepository;

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private RoomTypeServiceImpl roomTypeService;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID roomTypeId = UUID.randomUUID();
    private final UUID otherHotelId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Hotel testHotel;

    @BeforeEach
    void setUp() {
        testHotel = new Hotel();
        testHotel.setId(hotelId);
        testHotel.setName("Grand Hotel");
    }

    private HotelRoomType buildRoomType(UUID id, String code) {
        HotelRoomType entity = new HotelRoomType();
        entity.setId(id);
        entity.setHotelId(hotelId);
        entity.setCode(code);
        entity.setName("Deluxe Room");
        entity.setDescription("Spacious room");
        entity.setMaxAdults(2);
        entity.setMaxChildren(1);
        entity.setMaxOccupancy(3);
        entity.setSizeSqm(45);
        entity.setStatus(CatalogStatus.ACTIVE);
        entity.setVersion(0L);
        return entity;
    }

    private RoomTypeResponse buildResponse(UUID id) {
        return new RoomTypeResponse(
                id, hotelId, "DLX", "Deluxe Room", "Spacious room",
                2, 1, 3, 45, CatalogStatus.ACTIVE,
                0L, null, null
        );
    }

    // --- CREATE tests ---

    @Test
    void create_hotelMissing_throwsException() {
        RoomTypeCreateRequest request = new RoomTypeCreateRequest("DLX", "Deluxe", "desc", 2, 1, 3, 45);
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> roomTypeService.create(hotelId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(roomTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_duplicateCode_throwsConflict() {
        RoomTypeCreateRequest request = new RoomTypeCreateRequest("DLX", "Deluxe", "desc", 2, 1, 3, 45);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.existsByHotelIdAndCode(hotelId, "DLX")).thenReturn(true);

        assertThatThrownBy(() -> roomTypeService.create(hotelId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_occupancyRuleViolation_throwsException() {
        RoomTypeCreateRequest request = new RoomTypeCreateRequest("DLX", "Deluxe", "desc", 5, 1, 10, 45);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.existsByHotelIdAndCode(hotelId, "DLX")).thenReturn(false);

        assertThatThrownBy(() -> roomTypeService.create(hotelId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("OCCUPANCY_RULE");
    }

    @Test
    void create_maxOccupancyBelowMaxAdults_throwsException() {
        RoomTypeCreateRequest request = new RoomTypeCreateRequest("DLX", "Deluxe", "desc", 3, 2, 2, 45);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.existsByHotelIdAndCode(hotelId, "DLX")).thenReturn(false);

        assertThatThrownBy(() -> roomTypeService.create(hotelId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("OCCUPANCY_RULE");
    }

    @Test
    void create_success() {
        RoomTypeCreateRequest request = new RoomTypeCreateRequest("DLX", "Deluxe", "desc", 2, 1, 3, 45);
        HotelRoomType saved = buildRoomType(roomTypeId, "DLX");
        RoomTypeResponse response = buildResponse(roomTypeId);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.lockHotelById(hotelId)).thenReturn(Optional.of(testHotel));
        when(roomTypeRepository.countByHotelId(hotelId)).thenReturn(0L);
        when(roomTypeRepository.existsByHotelIdAndCode(hotelId, "DLX")).thenReturn(false);
        when(roomTypeRepository.saveAndFlush(any())).thenReturn(saved);

        RoomTypeResponse result = roomTypeService.create(hotelId, request);

        assertThat(result).isEqualTo(response);
        verify(roomTypeRepository).saveAndFlush(any());
    }

    @Test
    void create_limitReached_throwsException() {
        RoomTypeCreateRequest request = new RoomTypeCreateRequest("DLX", "Deluxe", "desc", 2, 1, 3, 45);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.countByHotelId(hotelId)).thenReturn(50L);

        assertThatThrownBy(() -> roomTypeService.create(hotelId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("LIMIT_REACHED");
    }

    // --- GET BY ID tests ---

    @Test
    void getById_found_returnsResponse() {
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.of(entity));

        RoomTypeResponse result = roomTypeService.getById(hotelId, roomTypeId, false);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(roomTypeId);
    }

    @Test
    void getById_notFound_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomTypeService.getById(hotelId, roomTypeId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_inactiveHidden_defaultReturns404() {
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        entity.setStatus(CatalogStatus.INACTIVE);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> roomTypeService.getById(hotelId, roomTypeId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_inactiveShown_includeInactiveTrue() {
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        entity.setStatus(CatalogStatus.INACTIVE);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.of(entity));

        RoomTypeResponse result = roomTypeService.getById(hotelId, roomTypeId, true);

        assertThat(result.status()).isEqualTo(CatalogStatus.INACTIVE);
    }

    @Test
    void getById_crossHotelSecurity_returns404() {
        when(hotelRepository.existsById(otherHotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, otherHotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomTypeService.getById(otherHotelId, roomTypeId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getById_hotelMissing_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> roomTypeService.getById(hotelId, roomTypeId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- GET BY HOTEL tests ---

    @Test
    void getByHotelId_activeOnly_default() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        when(roomTypeRepository.findByHotelIdAndStatusOrderByNameAsc(hotelId, CatalogStatus.ACTIVE))
                .thenReturn(List.of(entity));

        List<RoomTypeResponse> result = roomTypeService.getByHotelId(hotelId, false);

        assertThat(result).hasSize(1);
    }

    @Test
    void getByHotelId_includeInactive_returnsAll() {
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        HotelRoomType active = buildRoomType(roomTypeId, "DLX");
        HotelRoomType inactive = buildRoomType(UUID.randomUUID(), "STD");
        inactive.setStatus(CatalogStatus.INACTIVE);
        when(roomTypeRepository.findByHotelIdOrderByNameAsc(hotelId))
                .thenReturn(List.of(active, inactive));

        List<RoomTypeResponse> result = roomTypeService.getByHotelId(hotelId, true);

        assertThat(result).hasSize(2);
    }

    @Test
    void getByHotelId_hotelMissing_throwsException() {
        when(hotelRepository.existsById(hotelId)).thenReturn(false);

        assertThatThrownBy(() -> roomTypeService.getByHotelId(hotelId, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- UPDATE tests ---

    @Test
    void update_notFound_throwsException() {
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest(null, "New Name", null, null, null, null, null, null);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomTypeService.update(hotelId, roomTypeId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_success_partialUpdate_keepsUnspecified() {
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        entity.setVersion(0L);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.of(entity));
        when(roomTypeRepository.saveAndFlush(any())).thenReturn(entity);

        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest("Updated Name", null, null, null, null, null, null, null);

        RoomTypeResponse result = roomTypeService.update(hotelId, roomTypeId, request);

        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.code()).isEqualTo("DLX");
        assertThat(result.description()).isEqualTo("Spacious room");
    }

    @Test
    void update_versionMatchWithLargeVersion_success() {
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        entity.setVersion(500L);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.of(entity));
        when(roomTypeRepository.saveAndFlush(any())).thenReturn(entity);

        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest("Updated Name", null, null, null, null, null, null, 500L);

        RoomTypeResponse result = roomTypeService.update(hotelId, roomTypeId, request);

        assertThat(result).isNotNull();
    }

    @Test
    void update_versionMismatch_throwsLockingFailure() {
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        entity.setVersion(0L);
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest("Updated Name", null, null, null, null, null, CatalogStatus.INACTIVE, 99L);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> roomTypeService.update(hotelId, roomTypeId, request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void update_occupancyRuleViolation_throwsException() {
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        entity.setMaxAdults(2);
        entity.setMaxChildren(1);
        entity.setMaxOccupancy(3);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.of(entity));

        // Set maxOccupancy to 10 which violates: 10 > 2 + 1 = 3
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest(null, null, null, null, 10, null, null, null);

        assertThatThrownBy(() -> roomTypeService.update(hotelId, roomTypeId, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("ruleCode").isEqualTo("OCCUPANCY_RULE");
    }

    @Test
    void update_statusChange_success() {
        HotelRoomType entity = buildRoomType(roomTypeId, "DLX");
        entity.setStatus(CatalogStatus.ACTIVE);
        when(hotelRepository.existsById(hotelId)).thenReturn(true);
        when(roomTypeRepository.findByIdAndHotelId(roomTypeId, hotelId)).thenReturn(Optional.of(entity));
        when(roomTypeRepository.saveAndFlush(any())).thenReturn(entity);

        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest(null, null, null, null, null, null, CatalogStatus.INACTIVE, null);

        RoomTypeResponse result = roomTypeService.update(hotelId, roomTypeId, request);

        assertThat(result.status()).isEqualTo(CatalogStatus.INACTIVE);
        verify(roomTypeRepository).saveAndFlush(any());
    }
}
