package com.waylo.hotelservice.controller;

import com.waylo.hotelservice.domain.CancellationPolicy;
import com.waylo.hotelservice.domain.CatalogStatus;
import com.waylo.hotelservice.domain.MealPlan;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.dto.RatePlanCreateRequest;
import com.waylo.hotelservice.dto.RatePlanResponse;
import com.waylo.hotelservice.dto.RatePlanUpdateRequest;
import com.waylo.hotelservice.exception.AccessDeniedException;
import com.waylo.hotelservice.security.HotelAccessGuard;
import com.waylo.hotelservice.service.RatePlanService;
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
class RatePlanControllerTest {

    private final RatePlanService ratePlanService = mock(RatePlanService.class);
    private final HotelAccessGuard accessGuard = mock(HotelAccessGuard.class);
    @InjectMocks
    private RatePlanController controller;

    private final UUID hotelId = UUID.randomUUID();
    private final UUID ratePlanId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private RatePlanResponse buildResponse(UUID id) {
        return new RatePlanResponse(
                id, hotelId, "STD", "Standard Rate", "Standard",
                MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, 7,
                CatalogStatus.ACTIVE, 0L, Instant.now(), Instant.now()
        );
    }

    // --- CREATE ---

    @Test
    void create_returns201() {
        RatePlanCreateRequest request = new RatePlanCreateRequest("STD", "Standard", "desc", MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, 7);
        RatePlanResponse response = buildResponse(ratePlanId);
        when(ratePlanService.create(eq(hotelId), any(RatePlanCreateRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<RatePlanResponse>> result = controller.create(userId, hotelId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().id()).isEqualTo(ratePlanId);
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    @Test
    void create_guardThrowsAccessDenied_serviceNotCalled() {
        RatePlanCreateRequest request = new RatePlanCreateRequest("STD", "Standard", "desc", MealPlan.BREAKFAST, CancellationPolicy.FREE_CANCELLATION, 7);
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireRoomManage(userId, hotelId);

        assertThatThrownBy(() -> controller.create(userId, hotelId, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(ratePlanService, never()).create(any(), any());
    }

    // --- GET BY ID ---

    @Test
    void getById_publicAccess_returns200() {
        RatePlanResponse response = buildResponse(ratePlanId);
        when(ratePlanService.getById(hotelId, ratePlanId, false)).thenReturn(response);

        ResponseEntity<ApiResponse<RatePlanResponse>> result = controller.getById(userId, hotelId, ratePlanId, false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().id()).isEqualTo(ratePlanId);
        verify(accessGuard, never()).requireRoomManage(any(), any());
    }

    @Test
    void getById_includeInactive_true_callsGuard() {
        RatePlanResponse response = buildResponse(ratePlanId);
        when(ratePlanService.getById(hotelId, ratePlanId, true)).thenReturn(response);

        ResponseEntity<ApiResponse<RatePlanResponse>> result = controller.getById(userId, hotelId, ratePlanId, true);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    // --- GET BY HOTEL ---

    @Test
    void getByHotelId_publicAccess_returns200() {
        RatePlanResponse response = buildResponse(ratePlanId);
        when(ratePlanService.getByHotelId(hotelId, false)).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<RatePlanResponse>>> result = controller.getByHotelId(userId, hotelId, false);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).hasSize(1);
        verify(accessGuard, never()).requireRoomManage(any(), any());
    }

    @Test
    void getByHotelId_includeInactive_true_callsGuard() {
        RatePlanResponse response = buildResponse(ratePlanId);
        when(ratePlanService.getByHotelId(hotelId, true)).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<RatePlanResponse>>> result = controller.getByHotelId(userId, hotelId, true);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    // --- UPDATE (PATCH) ---

    @Test
    void update_returns200() {
        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, "Updated Name", null, null, null, null, null);
        RatePlanResponse response = buildResponse(ratePlanId);
        when(ratePlanService.update(eq(hotelId), eq(ratePlanId), any(RatePlanUpdateRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<RatePlanResponse>> result = controller.update(userId, hotelId, ratePlanId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(accessGuard).requireRoomManage(userId, hotelId);
    }

    @Test
    void update_guardThrowsAccessDenied_serviceNotCalled() {
        RatePlanUpdateRequest request = new RatePlanUpdateRequest(null, "Updated Name", null, null, null, null, null);
        doThrow(new AccessDeniedException("Not allowed")).when(accessGuard).requireRoomManage(userId, hotelId);

        assertThatThrownBy(() -> controller.update(userId, hotelId, ratePlanId, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(ratePlanService, never()).update(any(), any(), any());
    }
}
