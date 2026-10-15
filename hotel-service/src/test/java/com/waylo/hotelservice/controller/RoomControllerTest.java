package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.RoomRequest;
import com.waylo.hotelservice.dto.RoomResponse;
import com.waylo.hotelservice.domain.RoomStatus;
import com.waylo.hotelservice.domain.RoomType;
import com.waylo.hotelservice.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomControllerTest {

    private final RoomService roomService = mock(RoomService.class);
    private final RoomController controller = new RoomController(roomService);

    private final UUID hotelId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();

    private RoomResponse buildRoomResponse(UUID id) {
        return new RoomResponse(
                id, hotelId, "Grand Hotel", "101",
                RoomType.DOUBLE, RoomStatus.AVAILABLE,
                2, BigDecimal.valueOf(100), 1,
                "A nice room", null, null
        );
    }

    @Test
    void create_returns201() {
        RoomRequest request = new RoomRequest("101", RoomType.DOUBLE, 2, BigDecimal.valueOf(100), 1, "desc", null);
        RoomResponse response = buildRoomResponse(roomId);
        when(roomService.create(eq(hotelId), any(RoomRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<RoomResponse>> result = controller.create(hotelId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().id()).isEqualTo(roomId);
    }

    @Test
    void getById_returns200() {
        RoomResponse response = buildRoomResponse(roomId);
        when(roomService.getById(hotelId, roomId)).thenReturn(response);

        ResponseEntity<ApiResponse<RoomResponse>> result = controller.getById(hotelId, roomId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void getByHotelId_returns200() {
        RoomResponse response = buildRoomResponse(roomId);
        when(roomService.getByHotelId(hotelId)).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<RoomResponse>>> result = controller.getByHotelId(hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void search_returns200() {
        RoomResponse response = buildRoomResponse(roomId);
        when(roomService.search(eq(hotelId), eq(RoomType.DOUBLE), eq(RoomStatus.AVAILABLE), any(BigDecimal.class)))
                .thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<RoomResponse>>> result =
                controller.search(hotelId, RoomType.DOUBLE, RoomStatus.AVAILABLE, BigDecimal.valueOf(200));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void search_noFilters_returns200() {
        when(roomService.search(eq(hotelId), any(), any(), any())).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<RoomResponse>>> result = controller.search(hotelId, null, null, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void update_returns200() {
        RoomRequest request = new RoomRequest("102", RoomType.SUITE, 3, BigDecimal.valueOf(150), 2, "desc", null);
        RoomResponse response = buildRoomResponse(roomId);
        when(roomService.update(eq(hotelId), eq(roomId), any(RoomRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<RoomResponse>> result = controller.update(hotelId, roomId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void delete_returns204() {
        doNothing().when(roomService).delete(hotelId, roomId);

        ResponseEntity<Void> result = controller.delete(hotelId, roomId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void updateStatus_returns200() {
        RoomResponse response = buildRoomResponse(roomId);
        when(roomService.updateStatus(hotelId, roomId, RoomStatus.OCCUPIED)).thenReturn(response);

        ResponseEntity<ApiResponse<RoomResponse>> result =
                controller.updateStatus(hotelId, roomId, RoomStatus.OCCUPIED);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void getAvailableCount_returns200() {
        when(roomService.getAvailableRoomCount(hotelId)).thenReturn(10L);

        ResponseEntity<ApiResponse<Long>> result = controller.getAvailableCount(hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(10L);
    }
}
