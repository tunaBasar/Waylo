package com.waylo.carrentalservice.controller;

import com.waylo.carrentalservice.domain.VehicleStatus;
import com.waylo.carrentalservice.domain.VehicleType;
import com.waylo.carrentalservice.dto.ApiResponse;
import com.waylo.carrentalservice.dto.VehicleRequest;
import com.waylo.carrentalservice.dto.VehicleResponse;
import com.waylo.carrentalservice.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    @Mock
    private VehicleService vehicleService;
    @InjectMocks
    private VehicleController controller;

    private final UUID vehicleId = UUID.randomUUID();

    private VehicleResponse buildResponse() {
        return new VehicleResponse(vehicleId, "34ABC123", "Toyota", "Corolla", 2022, VehicleType.MIDSIZE,
                VehicleStatus.AVAILABLE, "White", 5, new BigDecimal("50.00"), "Istanbul", 10000,
                true, Instant.now(), Instant.now());
    }

    private VehicleRequest buildRequest() {
        return new VehicleRequest("34ABC123", "Toyota", "Corolla", 2022, VehicleType.MIDSIZE,
                "White", 5, new BigDecimal("50.00"), "Istanbul", 10000);
    }

    @Test
    void create_returns201() {
        VehicleRequest request = buildRequest();
        VehicleResponse response = buildResponse();
        when(vehicleService.create(any(VehicleRequest.class))).thenReturn(response);

        ResponseEntity<ApiResponse<VehicleResponse>> result = controller.create(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getById_returns200() {
        VehicleResponse response = buildResponse();
        when(vehicleService.getById(vehicleId)).thenReturn(response);

        ResponseEntity<ApiResponse<VehicleResponse>> result = controller.getById(vehicleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getByPlateNumber_returns200() {
        VehicleResponse response = buildResponse();
        when(vehicleService.getByPlateNumber("34ABC123")).thenReturn(response);

        ResponseEntity<ApiResponse<VehicleResponse>> result = controller.getByPlateNumber("34ABC123");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getAll_returns200() {
        when(vehicleService.getAll()).thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<VehicleResponse>>> result = controller.getAll();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
    }

    @Test
    void search_returns200() {
        when(vehicleService.search("Istanbul", VehicleType.MIDSIZE, VehicleStatus.AVAILABLE, new BigDecimal("100.00")))
                .thenReturn(List.of(buildResponse()));

        ResponseEntity<ApiResponse<List<VehicleResponse>>> result = controller.search(
                "Istanbul", VehicleType.MIDSIZE, VehicleStatus.AVAILABLE, new BigDecimal("100.00"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).hasSize(1);
        verify(vehicleService).search("Istanbul", VehicleType.MIDSIZE, VehicleStatus.AVAILABLE, new BigDecimal("100.00"));
    }

    @Test
    void update_returns200() {
        VehicleRequest request = buildRequest();
        VehicleResponse response = buildResponse();
        when(vehicleService.update(vehicleId, request)).thenReturn(response);

        ResponseEntity<ApiResponse<VehicleResponse>> result = controller.update(vehicleId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void delete_returns204() {
        ResponseEntity<Void> result = controller.delete(vehicleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(vehicleService).delete(vehicleId);
    }

    @Test
    void updateStatus_returns200() {
        VehicleResponse response = buildResponse();
        when(vehicleService.updateStatus(vehicleId, VehicleStatus.RENTED)).thenReturn(response);

        ResponseEntity<ApiResponse<VehicleResponse>> result = controller.updateStatus(vehicleId, VehicleStatus.RENTED);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void deactivate_returns200() {
        VehicleResponse response = buildResponse();
        when(vehicleService.deactivate(vehicleId)).thenReturn(response);

        ResponseEntity<ApiResponse<VehicleResponse>> result = controller.deactivate(vehicleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void activate_returns200() {
        VehicleResponse response = buildResponse();
        when(vehicleService.activate(vehicleId)).thenReturn(response);

        ResponseEntity<ApiResponse<VehicleResponse>> result = controller.activate(vehicleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(response);
    }

    @Test
    void getAvailableCount_returns200() {
        when(vehicleService.getAvailableCount("Istanbul")).thenReturn(7L);

        ResponseEntity<ApiResponse<Long>> result = controller.getAvailableCount("Istanbul");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().data()).isEqualTo(7L);
    }
}
