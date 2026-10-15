package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.HotelRequest;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.exception.ResourceNotFoundException;
import com.waylo.hotelservice.service.HotelService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HotelControllerTest {

    private final HotelService hotelService = mock(HotelService.class);
    private final HotelController controller = new HotelController(hotelService);

    private final UUID hotelId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    private HotelResponse buildResponse(UUID id) {
        return new HotelResponse(
                id, orgId, "Grand Hotel", "Istanbul", "Turkey",
                "123 Main St", 4, "A great hotel",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                true, 0, null, null
        );
    }

    @Test
    void create_returns201() {
        HotelRequest request = new HotelRequest(
                "Grand Hotel", "Istanbul", "Turkey",
                "123 Main St", 4, "A great hotel",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                orgId
        );
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.create(any(HotelRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.create(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getById_returns200() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.getById(hotelId)).thenReturn(response);

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.getById(hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().id()).isEqualTo(hotelId);
    }

    @Test
    void getAll_withoutOrganizationId_callsGetAll() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.getAll()).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<HotelResponse>>> result = controller.getAll(null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(hotelService).getAll();
        verify(hotelService, never()).getByOrganization(any());
    }

    @Test
    void getAll_withOrganizationId_callsGetByOrganization() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.getByOrganization(orgId)).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<HotelResponse>>> result = controller.getAll(orgId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(hotelService).getByOrganization(orgId);
        verify(hotelService, never()).getAll();
    }

    @Test
    void delete_returns204() {
        doNothing().when(hotelService).delete(hotelId);

        ResponseEntity<Void> result = controller.delete(hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void update_returns200() {
        HotelRequest request = new HotelRequest(
                "Updated Hotel", "Istanbul", "Turkey",
                "456 Oak Ave", 5, "Updated",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                orgId
        );
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.update(eq(hotelId), any(HotelRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.update(hotelId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void activate_returns200() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.activate(hotelId)).thenReturn(response);

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.activate(hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void deactivate_returns200() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.deactivate(hotelId)).thenReturn(response);

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.deactivate(hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }

    @Test
    void search_returns200() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.search("Istanbul", "Turkey", 4)).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<HotelResponse>>> result = controller.search("Istanbul", "Turkey", 4);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }
}
