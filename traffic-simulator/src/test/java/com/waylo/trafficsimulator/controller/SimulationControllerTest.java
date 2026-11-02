package com.waylo.trafficsimulator.controller;

import com.waylo.trafficsimulator.domain.ScenarioType;
import com.waylo.trafficsimulator.domain.SimulationStatus;
import com.waylo.trafficsimulator.dto.ApiResponse;
import com.waylo.trafficsimulator.dto.SimulationRequest;
import com.waylo.trafficsimulator.dto.SimulationResponse;
import com.waylo.trafficsimulator.service.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimulationControllerTest {

    private final SimulationService service = mock(SimulationService.class);
    private final SimulationController controller = new SimulationController(service);

    @Test
    void start_noRoleHeader_returns403() {
        var request = new SimulationRequest(ScenarioType.FLIGHT_SEARCH, 1, 5, 0, null);
        ResponseEntity<ApiResponse<SimulationResponse>> resp = controller.start(request, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void start_userRole_returns403() {
        var request = new SimulationRequest(ScenarioType.FLIGHT_SEARCH, 1, 5, 0, null);
        ResponseEntity<ApiResponse<SimulationResponse>> resp = controller.start(request, "USER");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void start_adminRole_returns201() {
        var request = new SimulationRequest(ScenarioType.FLIGHT_SEARCH, 1, 5, 0, null);
        var response = new SimulationResponse(
                "abc123", ScenarioType.FLIGHT_SEARCH, SimulationStatus.RUNNING,
                1, 5, 0, 0, 0, 0.0, 0.0, 0.0, 0.0,
                Instant.now(), null);

        when(service.start(request)).thenReturn(response);

        ResponseEntity<ApiResponse<SimulationResponse>> resp = controller.start(request, "ADMIN");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().data().simulationId()).isEqualTo("abc123");
        assertThat(resp.getBody().data().status()).isEqualTo(SimulationStatus.RUNNING);
    }

    @Test
    void start_emptyRole_returns403() {
        var request = new SimulationRequest(ScenarioType.FLIGHT_SEARCH, 1, 5, 0, null);
        ResponseEntity<ApiResponse<SimulationResponse>> resp = controller.start(request, "");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getStatus_returns200() {
        var response = new SimulationResponse(
                "abc123", ScenarioType.FLIGHT_SEARCH, SimulationStatus.COMPLETED,
                1, 5, 100, 95, 5, 150.0, 200.0, 250.0, 10.0,
                Instant.now().minusSeconds(5), Instant.now());

        when(service.getStatus("abc123")).thenReturn(response);

        ResponseEntity<ApiResponse<SimulationResponse>> resp = controller.getStatus("abc123", "ADMIN");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().totalRequests()).isEqualTo(100);
    }

    @Test
    void listAll_returns200() {
        var response = new SimulationResponse(
                "abc123", ScenarioType.FLIGHT_SEARCH, SimulationStatus.RUNNING,
                1, 5, 0, 0, 0, 0.0, 0.0, 0.0, 0.0,
                Instant.now(), null);

        when(service.listAll()).thenReturn(List.of(response));

        ResponseEntity<ApiResponse<List<SimulationResponse>>> resp = controller.listAll("ADMIN");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data()).hasSize(1);
    }

    @Test
    void cancel_returns200() {
        var response = new SimulationResponse(
                "abc123", ScenarioType.FLIGHT_SEARCH, SimulationStatus.CANCELLED,
                1, 5, 50, 40, 10, 150.0, 200.0, 250.0, 10.0,
                Instant.now().minusSeconds(5), Instant.now());

        when(service.cancel("abc123")).thenReturn(response);

        ResponseEntity<ApiResponse<SimulationResponse>> resp = controller.cancel("abc123", "ADMIN");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().data().status()).isEqualTo(SimulationStatus.CANCELLED);
        assertThat(resp.getBody().message()).isEqualTo("Simulation cancelled");
    }

    @Test
    void readAndCancelRequireAdmin() {
        SimulationService service = org.mockito.Mockito.mock(SimulationService.class);
        SimulationController c = new SimulationController(service);
        for (String role : new String[] {null, "", "CUSTOMER", "admin"}) {
            assertThat(c.getStatus("abc123", role).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(c.listAll(role).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(c.cancel("abc123", role).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
