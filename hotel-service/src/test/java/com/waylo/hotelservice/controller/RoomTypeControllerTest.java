package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.RoomTypeCreateRequest;
import com.waylo.hotelservice.dto.RoomTypeResponse;
import com.waylo.hotelservice.dto.RoomTypeUpdateRequest;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.RoomTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomTypeControllerTest {

    private final RoomTypeService roomTypeService = mock(RoomTypeService.class);
    private final HotelAccessGuard accessGuard = mock(HotelAccessGuard.class);
    @InjectMocks
    private RoomTypeController controller;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID roomTypeId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private RoomTypeResponse buildResponse(UUID id) {
        return new RoomTypeResponse(
                id, hotelId, "DLX", "Deluxe Room", "Spacious",
                2, 1, 3, 45, CatalogStatus.ACTIVE,
                0L, Instant.now(), Instant.now()
        );
    }

    // --- CREATE ---

    @Test
    void create_returns201() {
        RoomTypeCreateRequest request = new RoomTypeCreateRequest("DLX", "Deluxe", "desc", 2, 1, 3, 45);
        RoomTypeResponse response = buildResponse(roomTypeId);
        when(roomTypeService.create(eq(hotelId), any(RoomTypeCreateRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<RoomTypeResponse>> result = controller.create(userId, hotelId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().id()).isEqualTo(roomTypeId);
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    @Test
    void create_guardThrowsAccessDenied_serviceNotCalled() {
        RoomTypeCreateRequest request = new RoomTypeCreateRequest("DLX", "Deluxe", "desc", 2, 1, 3, 45);
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireRoomManage(userId, hotelId);

        assertThatThrownBy(() -> controller.create(userId, hotelId, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(roomTypeService, never()).create(any(), any());
    }

    // --- GET BY ID ---

    @Test
    void getById_includeInactive_false_noGuard() {
        RoomTypeResponse response = buildResponse(roomTypeId);
        when(roomTypeService.getById(hotelId, roomTypeId, false)).thenReturn(response);

        ResponseEntity<ApiResponse<RoomTypeResponse>> result = controller.getById(userId, hotelId, roomTypeId, false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().id()).isEqualTo(roomTypeId);
        // No accessGuard call for public access
        verify(accessGuard, never()).requireRoomManage(any(), any());
    }

    @Test
    void getById_includeInactive_true_callsGuard() {
        RoomTypeResponse response = buildResponse(roomTypeId);
        when(roomTypeService.getById(hotelId, roomTypeId, true)).thenReturn(response);

        ResponseEntity<ApiResponse<RoomTypeResponse>> result = controller.getById(userId, hotelId, roomTypeId, true);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    // --- GET BY HOTEL ---

    @Test
    void getByHotelId_includeInactive_false_noGuard() {
        RoomTypeResponse response = buildResponse(roomTypeId);
        when(roomTypeService.getByHotelId(hotelId, false)).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<RoomTypeResponse>>> result = controller.getByHotelId(userId, hotelId, false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).hasSize(1);
        verify(accessGuard, never()).requireRoomManage(any(), any());
    }

    @Test
    void getByHotelId_includeInactive_true_callsGuard() {
        RoomTypeResponse response = buildResponse(roomTypeId);
        when(roomTypeService.getByHotelId(hotelId, true)).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<RoomTypeResponse>>> result = controller.getByHotelId(userId, hotelId, true);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    // --- UPDATE (PATCH) ---

    @Test
    void update_returns200() {
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest(null, "Updated Name", null, null, null, null, null, null);
        RoomTypeResponse response = buildResponse(roomTypeId);
        when(roomTypeService.update(eq(hotelId), eq(roomTypeId), any(RoomTypeUpdateRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<RoomTypeResponse>> result = controller.update(userId, hotelId, roomTypeId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    @Test
    void update_guardThrowsAccessDenied_serviceNotCalled() {
        RoomTypeUpdateRequest request = new RoomTypeUpdateRequest(null, "Updated Name", null, null, null, null, null, null);
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireRoomManage(userId, hotelId);

        assertThatThrownBy(() -> controller.update(userId, hotelId, roomTypeId, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(roomTypeService, never()).update(any(), any(), any());
    }
}
