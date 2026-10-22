package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.client.AccessDecision;
import com.waylo.hotelservice.domain.Hotel;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.HotelRequest;
import com.waylo.hotelservice.dto.HotelResponse;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.repository.HotelRepository;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.HotelService;
import com.waylo.hotelservice.service.HotelSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelControllerTest {

    private final HotelSearchService hotelSearchService = mock(HotelSearchService.class);
    private final HotelService hotelService = mock(HotelService.class);
    private final HotelAccessGuard accessGuard = mock(HotelAccessGuard.class);
    private final HotelRepository hotelRepository = mock(HotelRepository.class);
    @InjectMocks
    private HotelController controller;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

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

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.create(userId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(accessGuard).requireCreate(userId, orgId);
    }

    @Test
    void create_guardThrowsAccessDenied_serviceNotCalled() {
        HotelRequest request = new HotelRequest(
                "Grand Hotel", "Istanbul", "Turkey",
                "123 Main St", 4, "A great hotel",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                orgId
        );
        doThrow(new AccessDeniedException("Not allowed to perform this action")).when(accessGuard).requireCreate(userId, orgId);

        assertThatThrownBy(() -> controller.create(userId, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(hotelService, never()).create(any());
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

        ResponseEntity<Void> result = controller.delete(userId, hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(accessGuard).requireHotelManage(userId, hotelId);
    }

    @Test
    void delete_guardThrowsAccessDenied_serviceNotCalled() {
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireHotelManage(userId, hotelId);

        assertThatThrownBy(() -> controller.delete(userId, hotelId))
                .isInstanceOf(AccessDeniedException.class);

        verify(hotelService, never()).delete(hotelId);
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

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.update(userId, hotelId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireHotelManage(userId, hotelId);
    }

    @Test
    void update_guardThrowsAccessDenied_serviceNotCalled() {
        HotelRequest request = new HotelRequest(
                "Updated Hotel", "Istanbul", "Turkey",
                "456 Oak Ave", 5, "Updated",
                BigDecimal.valueOf(41.0), BigDecimal.valueOf(29.0),
                orgId
        );
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireHotelManage(userId, hotelId);

        assertThatThrownBy(() -> controller.update(userId, hotelId, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(hotelService, never()).update(any(), any());
    }

    @Test
    void activate_returns200() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.activate(hotelId)).thenReturn(response);

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.activate(userId, hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireHotelManage(userId, hotelId);
    }

    @Test
    void activate_guardThrowsAccessDenied_serviceNotCalled() {
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireHotelManage(userId, hotelId);

        assertThatThrownBy(() -> controller.activate(userId, hotelId))
                .isInstanceOf(AccessDeniedException.class);

        verify(hotelService, never()).activate(hotelId);
    }

    @Test
    void deactivate_returns200() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.deactivate(hotelId)).thenReturn(response);

        ResponseEntity<ApiResponse<HotelResponse>> result = controller.deactivate(userId, hotelId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireHotelManage(userId, hotelId);
    }

    @Test
    void deactivate_guardThrowsAccessDenied_serviceNotCalled() {
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireHotelManage(userId, hotelId);

        assertThatThrownBy(() -> controller.deactivate(userId, hotelId))
                .isInstanceOf(AccessDeniedException.class);

        verify(hotelService, never()).deactivate(hotelId);
    }

    @Test
    void search_returns200() {
        HotelResponse response = buildResponse(hotelId);
        when(hotelService.search("Istanbul", "Turkey", 4)).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<HotelResponse>>> result = controller.search("Istanbul", "Turkey", 4, null, null, 1, 1, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }
}
